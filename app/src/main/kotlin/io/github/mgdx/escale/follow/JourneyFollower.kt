package io.github.mgdx.escale.follow

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.core.follow.FollowTimeline
import io.github.mgdx.escale.core.model.Journey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Le point de rencontre du suivi de trajet (SPEC.md § 5.3.1) : l'écran d'un côté, le service de
 * l'autre.
 *
 * L'écran lui demande de suivre, de remplacer ou d'arrêter ; le service lui rapporte où en est
 * l'usager à chaque échéance. Ni l'un ni l'autre ne se connaissent : l'écran ne sait pas qu'un
 * service existe, le service ne sait pas qu'un écran regarde.
 *
 * Tout ce qui est ici est **en mémoire** et disparaît avec le processus. Le seul état qui lui
 * survit est celui que le système garde pour relancer le service : l'intent de démarrage, qui
 * porte le plan réduit. Rien n'est écrit sur le disque (SPEC.md § 5.3.1 et § 11).
 */
class JourneyFollower(context: Context, private val now: () -> Instant = Instant::now) : JourneyFollowing {

  private val appContext: Context = context.applicationContext

  private val sessionState = MutableStateFlow<FollowSession?>(null)

  override val session: StateFlow<FollowSession?> = sessionState.asStateFlow()

  /**
   * Le trajet suivi, entier, tant que le processus vit : c'est lui que l'écran de détail rouvre
   * depuis la notification. Le plan réduit, lui, ne suffirait pas à afficher la fiche.
   */
  var journey: Journey? = null
    private set

  private val openState = MutableStateFlow<Long?>(null)

  /** Une demande d'ouverture de la fiche du trajet suivi, déposée par la notification. */
  val openRequests: StateFlow<Long?> = openState.asStateFlow()

  private var openTokens = 0L

  private var reopenPending = false

  override fun start(journey: Journey) {
    val plan = FollowPlan.of(journey)
    this.journey = journey
    publish(plan, initialState(plan))
    send(plan)
  }

  override fun replace(key: String, journey: Journey) {
    val current = sessionState.value ?: return
    if (current.plan.key != key) return
    val plan = FollowPlan.of(journey, key = key)
    this.journey = journey
    publish(plan, initialState(plan))
    send(plan)
  }

  override fun stop() {
    cleared()
    appContext.stopService(FollowService.intent(appContext))
  }

  override fun takeReopenItineraryId(): String? {
    if (!reopenPending) return null
    reopenPending = false
    return sessionState.value?.plan?.itineraryId
  }

  /** La notification a été touchée : la fiche du trajet suivi doit s'ouvrir. */
  fun requestOpen() {
    // Sans trajet en mémoire alors qu'un suivi tourne, le processus est mort entre-temps : la fiche
    // se reconstruira depuis l'identifiant d'itinéraire, et c'est l'écran qui viendra le chercher.
    reopenPending = journey == null && sessionState.value != null
    openTokens += 1
    openState.value = openTokens
  }

  fun consumeOpen() {
    openState.value = null
  }

  /** Le service rapporte l'état atteint à une échéance. */
  internal fun publish(plan: FollowPlan, state: FollowState) {
    sessionState.value = FollowSession(plan, state)
  }

  /** Le service s'est arrêté, de lui-même ou sur demande : plus rien à montrer. */
  internal fun cleared() {
    journey = null
    reopenPending = false
    sessionState.value = null
  }

  private fun initialState(plan: FollowPlan): FollowState = FollowTimeline.stateAt(plan, FollowTimeline.of(plan), now())

  private fun send(plan: FollowPlan) {
    try {
      ContextCompat.startForegroundService(appContext, FollowService.followIntent(appContext, plan))
    } catch (_: IllegalStateException) {
      // Le système refuse le démarrage — l'application n'est pas au premier plan, ce qui n'arrive
      // pas depuis un bouton, ou une restriction du constructeur. Le suivi ne commence pas, et
      // l'écran le voit : rien n'est publié.
      cleared()
    }
  }
}
