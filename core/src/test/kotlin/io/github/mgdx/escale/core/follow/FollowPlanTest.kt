package io.github.mgdx.escale.core.follow

import io.github.mgdx.escale.core.model.StopVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowPlanTest {

  @Test
  fun `le plan reduit garde les portions, les arrets, les heures et les libelles`() {
    val plan = FollowPlan.of(referenceJourney())

    assertEquals("it-1", plan.key)
    assertEquals("it-1", plan.itineraryId)
    assertEquals(5, plan.legs.size)
    val line4 = plan.legs[1] as FollowLeg.Transit
    assertEquals("4", line4.lineLabel)
    assertEquals("Porte d'Orléans", line4.headsign)
    assertEquals("2", line4.track)
    assertEquals(listOf("Cité", "Saint-Michel", "Odéon", "Saint-Germain", "Saint-Placide"), line4.stops.map { it.name })
    assertEquals(at(12), line4.stops.first().time)
    val walk = plan.legs[0] as FollowLeg.Street
    assertEquals(StreetKind.WALK, walk.kind)
    assertEquals(at(0), plan.start)
    assertEquals(at(40), plan.end)
    assertFalse(plan.hasCancelledLeg)
  }

  @Test
  fun `sans identifiant d'itineraire, la cle est une empreinte des heures`() {
    val journey = referenceJourney().copy(id = null)
    val plan = FollowPlan.of(journey)

    assertNull(plan.itineraryId)
    assertEquals("${at(0).epochSecond}-${at(40).epochSecond}-5", plan.key)
  }

  @Test
  fun `la cle du trajet suivi survit a un rafraichissement qui change l'identifiant`() {
    val refreshed = referenceJourney().copy(id = "it-2")
    val plan = FollowPlan.of(refreshed, key = "it-1")
    assertEquals("it-1", plan.key)
    assertEquals("it-2", plan.itineraryId)
  }

  @Test
  fun `un terminus sans heure de depart prend son heure d'arrivee`() {
    val terminus = StopVisit(place = place("Terminus", at(3)), arrival = at(3), departure = null)
    val journey =
      journey(legs = listOf(transit(from = "A", to = "B", startMinute = 0, endMinute = 5, stops = listOf(terminus))))
    val leg = FollowPlan.of(journey).legs.single() as FollowLeg.Transit
    assertEquals(at(3), leg.stops.single().time)
  }

  @Test
  fun `une portion annulee se lit sur le plan`() {
    val journey =
      journey(legs = listOf(transit(from = "A", to = "B", startMinute = 0, endMinute = 5, cancelled = true)))
    assertTrue(FollowPlan.of(journey).hasCancelledLeg)
  }
}
