package io.github.mgdx.escale.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.mgdx.escale.MainActivity
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.ClockTime
import io.github.mgdx.escale.core.model.WatchIssue
import io.github.mgdx.escale.core.model.WatchNotice
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * La notification d'un trajet surveillé (SPEC.md § 5.5.1).
 *
 * **Canal dédié et silencieux par défaut** : `IMPORTANCE_LOW` ne fait ni son ni vibration, et
 * l'usager reste libre de le régler autrement — ou de l'éteindre — depuis les réglages système
 * d'Android, comme la spec l'exige.
 *
 * Rien n'est notifié sans permission : à partir d'Android 13, `POST_NOTIFICATIONS` est demandée à
 * l'activation de la première surveillance, et son refus ne fait pas échouer la vérification. La
 * surveillance continue, et son résultat s'affiche à l'ouverture de l'application.
 *
 * **Aucune trace n'est écrite** (SPEC.md § 11) : ni le trajet, ni l'heure, ni la ligne concernée ne
 * partent dans un journal, y compris en débogage.
 */
class WatchNotifications(private val context: Context) : WatchNotifier {

  /**
   * Vrai si une notification peut être émise.
   *
   * Les deux conditions ne se recouvrent pas : la permission d'Android 13 peut être accordée alors
   * que l'usager a désactivé le canal depuis les réglages système, et l'inverse est vrai aussi.
   */
  override fun canNotify(): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  /**
   * Émet la notification de [notice] pour le trajet [journeyId].
   *
   * @return vrai si elle est partie. Faux signifie « permission refusée ou canal éteint », ce qui
   *   n'est pas une erreur : l'appelant enregistre alors le résultat pour l'afficher dans
   *   l'application.
   */
  override fun notify(journeyId: Long, notice: WatchNotice, use24Hour: Boolean): Boolean {
    if (!canNotify()) return false
    val message = message(notice, use24Hour)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_schedule)
      .setContentTitle(message.title)
      .setContentText(message.body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setAutoCancel(true)
      .setContentIntent(openApplication(journeyId))
      .build()
    return try {
      createChannel()
      NotificationManagerCompat.from(context).notify(notificationId(journeyId), notification)
      true
    } catch (_: SecurityException) {
      // La permission a pu être retirée entre la vérification et l'émission : on se tait.
      false
    }
  }

  /** Retire la notification d'un trajet dont la surveillance vient d'être arrêtée. */
  override fun cancel(journeyId: Long) {
    NotificationManagerCompat.from(context).cancel(notificationId(journeyId))
  }

  /**
   * Le canal dédié, créé au moment d'émettre et non au démarrage : une application qui n'a jamais
   * surveillé de trajet n'a pas à faire apparaître un canal dans les réglages système.
   */
  private fun createChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.watch_channel_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = context.getString(R.string.watch_channel_description)
      setShowBadge(false)
    }
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
  }

  /**
   * L'appui sur la notification ouvre l'application.
   *
   * `FLAG_IMMUTABLE` : l'intention ne porte aucune donnée qu'un tiers aurait à compléter, et une
   * `PendingIntent` modifiable est une porte ouverte (SPEC.md § 11).
   */
  private fun openApplication(journeyId: Long): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
      context,
      notificationId(journeyId),
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  /**
   * Le texte de la notification : « la ligne concernée, la nature du problème, la nouvelle heure de
   * départ conseillée s'il en existe une » (SPEC.md § 5.5.1).
   *
   * Tout vient des ressources, en anglais et en français : une notification est du texte affiché à
   * l'usager comme un autre (docs/architecture.md § 8).
   */
  private fun message(notice: WatchNotice, use24Hour: Boolean): WatchMessage {
    val suggested = notice.suggestedDeparture?.let { at(it, use24Hour) }
    return when (notice.issue) {
      WatchIssue.DELAYED -> delayed(notice, suggested)

      WatchIssue.CANCELLED -> cancelled(notice.lineName, suggested)

      WatchIssue.DISRUPTED -> disrupted(notice, suggested)

      WatchIssue.IMPOSSIBLE -> WatchMessage(
        title = context.getString(R.string.watch_notification_impossible),
        body = context.getString(R.string.watch_notification_impossible_body),
      )

      WatchIssue.NOTHING -> WatchMessage(
        title = context.getString(R.string.watch_notification_fine),
        body = suggested?.let { context.getString(R.string.watch_notification_fine_body, it) }.orEmpty(),
      )
    }
  }

  private fun delayed(notice: WatchNotice, suggested: String?) = WatchMessage(
    title = titled(notice.lineName, R.string.watch_notification_delayed_line, R.string.watch_notification_delayed),
    body = join(delayText(notice), suggested?.let { advice(it) }),
  )

  private fun cancelled(line: String?, suggested: String?) = WatchMessage(
    title = titled(line, R.string.watch_notification_cancelled_line, R.string.watch_notification_cancelled),
    body = join(context.getString(R.string.watch_notification_cancelled_body), suggested?.let { advice(it) }),
  )

  private fun disrupted(notice: WatchNotice, suggested: String?) = WatchMessage(
    title = titled(
      notice.lineName,
      R.string.watch_notification_disrupted_line,
      R.string.watch_notification_disrupted,
    ),
    body = join(notice.detail, suggested?.let { advice(it) }),
  )

  /** Le titre nomme la ligne quand le serveur la nomme, et reste vrai quand il ne la nomme pas. */
  private fun titled(line: String?, withLine: Int, without: Int): String =
    line?.let { context.getString(withLine, it) } ?: context.getString(without)

  private fun delayText(notice: WatchNotice): String {
    val minutes = notice.delay?.toMinutes()?.toInt() ?: 0
    return context.resources.getQuantityString(R.plurals.watch_notification_delay, minutes, minutes)
  }

  private fun advice(time: String) = context.getString(R.string.watch_notification_suggested, time)

  private fun at(instant: Instant, use24Hour: Boolean) =
    ClockTime.format(instant, ZoneId.systemDefault(), Locale.getDefault(), use24Hour)

  private fun join(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(separator = " ")

  private fun notificationId(journeyId: Long) = NOTIFICATION_ID_BASE + journeyId.toInt()

  /** Le titre et le corps d'une notification, une fois mis en mots. */
  private data class WatchMessage(val title: String, val body: String)

  companion object {
    /**
     * L'identifiant du canal. Il est stable : le changer ferait réapparaître un canal neuf, avec
     * ses réglages par défaut, et effacerait les choix de l'usager dans les réglages système.
     */
    const val CHANNEL_ID = "watched_journeys"

    /** Une notification par trajet surveillé, cinq au plus (`JourneyWatchLimit`). */
    private const val NOTIFICATION_ID_BASE = 4200
  }
}
