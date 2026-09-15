package io.github.mgdx.escale.follow

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.mgdx.escale.MainActivity
import io.github.mgdx.escale.R

/**
 * Les deux notifications du suivi de trajet (SPEC.md § 5.3.1), et leurs deux canaux.
 *
 * - La **notification permanente** est celle du service au premier plan : silencieuse, mise à jour
 *   à chaque échéance, elle dit la portion en cours et la prochaine action. Son appui ouvre la fiche
 *   du trajet suivi, son action l'arrête.
 * - Les **alertes** sont sur un canal à part, sonore et vibrant, et ne tombent qu'aux moments qui
 *   demandent un geste. Une alerte remplace la précédente : il n'y a jamais qu'une chose à faire.
 *
 * Chaque alerte est un texte, jamais un son seul (SPEC.md § 9) ; c'est `FollowWording` qui l'écrit.
 */
class FollowNotifications(private val context: Context) {

  private val manager: NotificationManagerCompat
    get() = NotificationManagerCompat.from(context)

  /** Crée les deux canaux, ou ne fait rien s'ils existent : l'usager peut les avoir réglés. */
  fun ensureChannels() {
    manager.createNotificationChannelsCompat(
      listOf(
        NotificationChannelCompat.Builder(ONGOING_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
          .setName(context.getString(R.string.follow_channel_ongoing))
          .setDescription(context.getString(R.string.follow_channel_ongoing_description))
          .setShowBadge(false)
          .build(),
        NotificationChannelCompat.Builder(ALERTS_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
          .setName(context.getString(R.string.follow_channel_alerts))
          .setDescription(context.getString(R.string.follow_channel_alerts_description))
          .setVibrationEnabled(true)
          .build(),
      ),
    )
  }

  fun ongoing(wording: FollowText): Notification = NotificationCompat.Builder(context, ONGOING_CHANNEL)
    .setSmallIcon(R.drawable.ic_directions_transit)
    .setContentTitle(wording.title)
    .setContentText(wording.text)
    .setStyle(wording.text?.let { NotificationCompat.BigTextStyle().bigText(it) })
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setSilent(true)
    .setShowWhen(false)
    .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
    .setContentIntent(openIntent())
    .addAction(0, context.getString(R.string.follow_action_stop), stopIntent())
    .build()

  /** La notification permanente, remise à jour à une échéance. Muette : `setOnlyAlertOnce`. */
  fun updateOngoing(wording: FollowText) {
    if (manager.areNotificationsEnabled()) manager.notify(ONGOING_ID, ongoing(wording))
  }

  fun showAlert(wording: FollowText) {
    val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
      .setSmallIcon(R.drawable.ic_directions_transit)
      .setContentTitle(wording.title)
      .setContentText(wording.text)
      .setStyle(wording.text?.let { NotificationCompat.BigTextStyle().bigText(it) })
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .setContentIntent(openIntent())
      .build()
    if (manager.areNotificationsEnabled()) manager.notify(ALERT_ID, notification)
  }

  /** À l'arrêt du suivi, rien ne subsiste : la dernière alerte s'en va avec la notification permanente. */
  fun cancelAlert() {
    manager.cancel(ALERT_ID)
  }

  private fun openIntent(): PendingIntent = PendingIntent.getActivity(
    context,
    OPEN_REQUEST,
    Intent(context, MainActivity::class.java)
      .setAction(MainActivity.ACTION_OPEN_FOLLOW)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

  private fun stopIntent(): PendingIntent = PendingIntent.getService(
    context,
    STOP_REQUEST,
    FollowService.stopIntent(context),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

  companion object {
    const val ONGOING_ID = 1
    const val ALERT_ID = 2
    private const val ONGOING_CHANNEL = "follow.ongoing"
    private const val ALERTS_CHANNEL = "follow.alerts"
    private const val OPEN_REQUEST = 10
    private const val STOP_REQUEST = 11
  }
}
