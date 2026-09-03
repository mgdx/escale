package io.github.mgdx.escale.work

import io.github.mgdx.escale.core.model.JourneyWatchLimit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deux trajets surveillés peuvent alerter le même matin (SPEC.md § 5.5.1).
 *
 * Si leurs `PendingIntent` partagent le même code de requête, Android les confond — les extras ne
 * comptent pas dans cette comparaison — et la seconde notification rouvre le premier trajet. Rien
 * ne plante : l'usager ouvre simplement le mauvais trajet, ce qu'aucun journal ne montrerait.
 */
class WatchNotificationIdsTest {

  @Test
  fun `deux trajets n'ont ni la meme notification ni le meme code de requete`() {
    val ids = (1L..JourneyWatchLimit.MAX_WATCHED.toLong()).map(WatchNotificationIds::notificationId)
    val codes = (1L..JourneyWatchLimit.MAX_WATCHED.toLong()).map(WatchNotificationIds::requestCode)

    assertEquals(ids.size, ids.distinct().size)
    assertEquals(codes.size, codes.distinct().size)
  }

  @Test
  fun `le meme trajet garde les memes identifiants d'une alerte a l'autre`() {
    // Sinon une nouvelle alerte empilerait une seconde notification pour le même trajet au lieu de
    // remplacer la précédente.
    assertEquals(WatchNotificationIds.notificationId(3), WatchNotificationIds.notificationId(3))
    assertEquals(WatchNotificationIds.requestCode(3), WatchNotificationIds.requestCode(3))
  }

  @Test
  fun `un trajet a un seul identifiant, partage par sa notification et son intention`() {
    assertEquals(WatchNotificationIds.notificationId(7), WatchNotificationIds.requestCode(7))
  }
}
