package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.transit
import io.github.mgdx.escale.core.format.walk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * La pagination de SPEC.md § 5.2 : « Plus tôt » et « Plus tard » étendent la liste affichée, ils ne
 * la remplacent pas.
 */
class JourneyFeedTest {

  private val early = journey(id = "a", legs = listOf(transit(afterMinutes = 0, minutes = 30)))
  private val middle = journey(id = "b", legs = listOf(transit(afterMinutes = 30, minutes = 30)))
  private val late = journey(id = "c", legs = listOf(transit(afterMinutes = 60, minutes = 30)))
  private val onFoot = journey(legs = listOf(walk(afterMinutes = 0, minutes = 95)))

  @Test
  fun `la premiere page fournit la liste et ses deux curseurs`() {
    val feed = JourneyFeed.of(
      JourneyPage(journeys = listOf(middle), previousPageCursor = "avant", nextPageCursor = "apres"),
    )
    assertEquals(listOf(middle), feed.journeys)
    assertEquals("avant", feed.previousPageCursor)
    assertEquals("apres", feed.nextPageCursor)
  }

  @Test
  fun `plus tot ajoute les trajets et n avance que le curseur precedent`() {
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(middle), previousPageCursor = "p1", nextPageCursor = "n1"))
      .earlier(JourneyPage(journeys = listOf(early), previousPageCursor = "p0", nextPageCursor = "autre"))
    assertEquals(listOf(early, middle), feed.journeys)
    assertEquals("p0", feed.previousPageCursor)
    assertEquals("n1", feed.nextPageCursor)
  }

  @Test
  fun `plus tard ajoute les trajets et n avance que le curseur suivant`() {
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(middle), previousPageCursor = "p1", nextPageCursor = "n1"))
      .later(JourneyPage(journeys = listOf(late), previousPageCursor = "autre", nextPageCursor = "n2"))
    assertEquals(listOf(middle, late), feed.journeys)
    assertEquals("p1", feed.previousPageCursor)
    assertEquals("n2", feed.nextPageCursor)
  }

  @Test
  fun `un curseur absent est conserve tel quel et masque le bouton`() {
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(middle), nextPageCursor = "n1"))
      .later(JourneyPage(journeys = listOf(late)))
    assertNull(feed.nextPageCursor)
    assertNull(feed.previousPageCursor)
  }

  @Test
  fun `les trajets sont ordonnes par heure de depart`() {
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(late, early, middle)))
    assertEquals(listOf(early, middle, late), feed.journeys)
  }

  @Test
  fun `un trajet deja affiche n apparait pas deux fois`() {
    // Le serveur renvoie les trajets sans horaire à chaque page : sans dédoublonnage, la marche
    // s'afficherait autant de fois que l'usager appuie sur « Plus tard ».
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(middle), direct = listOf(onFoot)))
      .later(JourneyPage(journeys = listOf(late), direct = listOf(onFoot)))
    assertEquals(3, feed.journeys.size)
    assertEquals(1, feed.journeys.count { it.legs.first() is JourneyLeg.Walk })
  }

  @Test
  fun `les trajets sans horaire sont meles aux autres`() {
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(late), direct = listOf(onFoot)))
    assertEquals(listOf(onFoot, late), feed.journeys)
    assertTrue(!feed.isEmpty)
  }

  @Test
  fun `une page vide donne une liste vide`() {
    assertTrue(JourneyFeed.of(JourneyPage(journeys = emptyList())).isEmpty)
  }

  // --- La durée annoncée sous l'onglet (SPEC.md § 5.2) -----------------------------------------

  @Test
  fun `un onglet annonce la duree du trajet le plus rapide, pas celle du premier depart`() {
    val quick = journey(id = "rapide", legs = listOf(transit(afterMinutes = 30, minutes = 10)))
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(early, quick)))

    // `early` part le premier, il est donc en tête de liste ; c'est bien l'autre qui est le plus
    // rapide, et c'est lui que l'onglet annonce.
    assertEquals(early, feed.journeys.first())
    assertEquals(Duration.ofMinutes(10), feed.fastestDuration)
  }

  @Test
  fun `un onglet sans trajet n annonce aucune duree`() {
    assertNull(JourneyFeed.of(JourneyPage(journeys = emptyList())).fastestDuration)
  }

  @Test
  fun `un trajet supprime n est jamais annonce comme le plus rapide`() {
    val cancelled = journey(id = "supprime", legs = listOf(transit(afterMinutes = 0, minutes = 5, cancelled = true)))
    val feed = JourneyFeed.of(JourneyPage(journeys = listOf(cancelled, early)))

    // Le trajet supprimé est plus court, mais il ne circule pas : l'annoncer serait une promesse
    // fausse.
    assertEquals(Duration.ofMinutes(30), feed.fastestDuration)
  }

  @Test
  fun `un onglet dont tous les trajets sont supprimes n annonce aucune duree`() {
    val cancelled = journey(id = "supprime", legs = listOf(transit(afterMinutes = 0, minutes = 5, cancelled = true)))

    assertNull(JourneyFeed.of(JourneyPage(journeys = listOf(cancelled))).fastestDuration)
  }
}
