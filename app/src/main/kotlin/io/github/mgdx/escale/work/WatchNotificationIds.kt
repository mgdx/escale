package io.github.mgdx.escale.work

/**
 * Les identifiants qui distinguent deux trajets surveillés dans le système de notification.
 *
 * **C'est un piège classique, et il est silencieux.** Deux surveillances peuvent alerter le même
 * matin. Si leurs `PendingIntent` partagent le même `requestCode`, Android considère qu'il s'agit
 * de la même intention en attente — les extras ne participent pas à cette comparaison — et la
 * seconde notification rouvre le **premier** trajet. Rien ne plante, rien ne se voit : l'usager
 * ouvre simplement le mauvais trajet.
 *
 * Les deux identifiants sont donc dérivés du seul `journeyId`, et vérifiés en JVM.
 */
object WatchNotificationIds {

  /** Une notification par trajet surveillé, cinq au plus (`JourneyWatchLimit`). */
  fun notificationId(journeyId: Long): Int = BASE + journeyId.toInt()

  /**
   * Le code de requête du `PendingIntent`, distinct par trajet.
   *
   * Il vaut celui de la notification : un trajet, une notification, une intention en attente.
   */
  fun requestCode(journeyId: Long): Int = notificationId(journeyId)

  /**
   * Décalage arbitraire, choisi pour ne pas empiéter sur les identifiants qu'une autre partie de
   * l'application pourrait utiliser un jour.
   */
  private const val BASE = 4200
}
