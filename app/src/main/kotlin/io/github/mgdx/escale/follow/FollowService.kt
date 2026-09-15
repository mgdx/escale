package io.github.mgdx.escale.follow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.mgdx.escale.EscaleApplication
import io.github.mgdx.escale.core.follow.FollowAlert
import io.github.mgdx.escale.core.follow.FollowEvent
import io.github.mgdx.escale.core.follow.FollowLimits
import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.core.follow.FollowTimeline
import io.github.mgdx.escale.core.format.ClockTime
import io.github.mgdx.escale.core.format.uses24Hour
import io.github.mgdx.escale.core.model.DisplayPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * **Le seul service de l'application**, celui du suivi de trajet (SPEC.md § 5.3.1).
 *
 * Il ne fait qu'une chose : dormir jusqu'à la prochaine échéance de `FollowTimeline`, puis mettre
 * la notification à jour et émettre l'alerte que l'échéance porte. Tout ce qu'il sait du trajet
 * vient de l'intent qui l'a démarré, sous forme réduite, et reste en mémoire.
 *
 * Ce qu'il ne fait **jamais**, et qu'un test vérifie sur ses sources : aucune requête réseau,
 * aucune lecture de position, rien sur le disque. Il ne démarre que par un appui sur « Suivre ce
 * trajet », et jamais au démarrage de l'appareil ni de l'application.
 *
 * Il détient un verrou de réveil partiel le temps du suivi, faute de quoi les échéances ne se
 * déclencheraient pas écran éteint, et le relâche à l'arrêt. Le verrou est borné à la fin du suivi
 * quoi qu'il arrive.
 */
class FollowService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val notifications by lazy { FollowNotifications(this) }

  private val follower: JourneyFollower
    get() = (application as EscaleApplication).container.journeyFollower

  private var plan: FollowPlan? = null

  private var deadline: Instant = Instant.MAX

  private var loop: Job? = null

  private var wakeLock: PowerManager.WakeLock? = null

  private var lastStartId: Int? = null

  /**
   * Vrai quand le suivi s'est achevé de lui-même : la dernière alerte — « Arrivée à destination » —
   * reste alors visible jusqu'à ce que l'usager la balaie. Un arrêt demandé, lui, efface tout.
   */
  private var completed = false

  /** Le format d'heure choisi par l'usager (SPEC.md § 5.6), suivi tant que le service vit. */
  private var display = DisplayPreferences()

  override fun onCreate() {
    super.onCreate()
    notifications.ensureChannels()
    scope.launch {
      (application as EscaleApplication).container.preferencesRepository.displayPreferences.collect { display = it }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Un intent de démarrage acquitté ne sera pas relivré : seul le dernier plan compte.
    lastStartId?.let(::stopSelf)
    lastStartId = startId
    val encoded = intent?.takeIf { it.action == ACTION_FOLLOW }?.getStringExtra(EXTRA_PLAN)
    val received = encoded?.let(::decodeFollowPlan)
    completed = false
    if (received == null) {
      // Une demande d'arrêt, un intent inconnu, ou une relance sans intent : il n'y a rien à suivre.
      finish()
      return START_NOT_STICKY
    }
    follow(received)
    return START_REDELIVER_INTENT
  }

  override fun onDestroy() {
    // Arrêt demandé par l'écran (`stopService`) ou par le système : plus rien ne doit subsister.
    release()
    if (!completed) notifications.cancelAlert()
    follower.cleared()
    scope.cancel()
    super.onDestroy()
  }

  private fun follow(received: FollowPlan) {
    val previous = plan
    plan = received
    // La borne de fin se fixe une fois, au lancement : un rafraîchissement ne la repousse pas.
    if (previous == null) deadline = FollowLimits.deadline(received)
    val events = FollowTimeline.of(received)
    val now = Instant.now()
    startForeground(FollowTimeline.stateAt(received, events, now))
    acquireWakeLock(now)
    // Un plan qui en remplace un autre : l'alerte du précédent ne le concerne plus. Seule
    // l'annulation d'une portion, survenue à un rafraîchissement, mérite une nouvelle alerte.
    if (previous != null) notifications.cancelAlert()
    if (previous != null && !previous.hasCancelledLeg && received.hasCancelledLeg) {
      notifications.showAlert(wording().of(FollowAlert.LegCancelled))
    }
    loop?.cancel()
    loop = scope.launch { run(received, events, since = now) }
  }

  private suspend fun run(plan: FollowPlan, events: List<FollowEvent>, since: Instant) {
    var processed = since
    var next = step(plan, events)
    while (next != null) {
      delay(Duration.between(Instant.now(), minOf(next, deadline)).toMillis().coerceAtLeast(0))
      val reached = Instant.now()
      FollowTimeline.alertsBetween(events, after = processed, until = reached).forEach { alert ->
        notifications.showAlert(wording().of(alert))
      }
      processed = reached
      next = step(plan, events)
    }
    // L'arrivée, ou la borne : le suivi s'arrête de lui-même (SPEC.md § 5.3.1). Seule l'alerte
    // d'arrivée reste, le temps que l'usager la lise.
    completed = true
    finish()
  }

  /** Publie l'état courant, et rend l'instant du prochain réveil, ou `null` quand le suivi est fini. */
  private fun step(plan: FollowPlan, events: List<FollowEvent>): Instant? {
    val now = Instant.now()
    val state = FollowTimeline.stateAt(plan, events, now)
    follower.publish(plan, state)
    notifications.updateOngoing(wording().of(state))
    if (state == FollowState.Arrived || !now.isBefore(deadline)) return null
    return FollowTimeline.nextAfter(events, now)?.at
  }

  private fun startForeground(state: FollowState) {
    val notification = notifications.ongoing(wording().of(state))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(FollowNotifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(FollowNotifications.ONGOING_ID, notification)
    }
  }

  private fun acquireWakeLock(now: Instant) {
    val timeout = Duration.between(now, deadline).toMillis() + WAKE_LOCK_MARGIN_MILLIS
    val lock = wakeLock ?: ContextCompat.getSystemService(this, PowerManager::class.java)
      ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
      ?.also { wakeLock = it }
      ?: return
    if (lock.isHeld) lock.release()
    lock.acquire(timeout.coerceAtLeast(0))
  }

  private fun finish() {
    release()
    follower.cleared()
    if (!completed) notifications.cancelAlert()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun release() {
    loop?.cancel()
    loop = null
    plan = null
    wakeLock?.takeIf { it.isHeld }?.release()
  }

  private fun wording(): FollowWording {
    val zone = ZoneId.systemDefault()
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val use24Hour = display.clockFormat.uses24Hour(DateFormat.is24HourFormat(this))
    return FollowWording(this) { ClockTime.format(it, zone, locale, use24Hour) }
  }

  companion object {
    private const val ACTION_FOLLOW = "io.github.mgdx.escale.follow.FOLLOW"
    private const val ACTION_STOP = "io.github.mgdx.escale.follow.STOP"
    private const val EXTRA_PLAN = "plan"
    private const val WAKE_LOCK_TAG = "Escale:follow"

    /** Une minute de plus que la borne : le service s'arrête d'abord, le verrou expire ensuite. */
    private const val WAKE_LOCK_MARGIN_MILLIS = 60_000L

    fun intent(context: Context): Intent = Intent(context, FollowService::class.java)

    fun followIntent(context: Context, plan: FollowPlan): Intent =
      intent(context).setAction(ACTION_FOLLOW).putExtra(EXTRA_PLAN, encodeFollowPlan(plan))

    fun stopIntent(context: Context): Intent = intent(context).setAction(ACTION_STOP)
  }
}
