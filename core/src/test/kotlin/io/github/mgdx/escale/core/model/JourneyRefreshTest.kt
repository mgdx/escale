package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.ORIGIN
import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.transit
import io.github.mgdx.escale.core.format.walk
import io.github.mgdx.escale.core.result.EscaleError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class JourneyRefreshTest {

  @Test
  fun `un refus du serveur invalide l'identifiant d'itineraire`() {
    assertTrue(JourneyRefresh.invalidatesItineraryId(EscaleError.BadRequest(serverMessage = null)))
  }

  @Test
  fun `un 404 sur refresh-itinerary declenche le repli`() {
    val error = EscaleError.ApiVersionTooOld(endpoint = "/api/v6/refresh-itinerary")
    assertTrue(JourneyRefresh.invalidatesItineraryId(error))
  }

  @Test
  fun `une panne passagere ne declenche pas de repli`() {
    assertFalse(JourneyRefresh.invalidatesItineraryId(EscaleError.NoNetwork))
    assertFalse(JourneyRefresh.invalidatesItineraryId(EscaleError.Timeout))
    assertFalse(JourneyRefresh.invalidatesItineraryId(EscaleError.ServerUnreachable(statusCode = 503)))
    assertFalse(JourneyRefresh.invalidatesItineraryId(EscaleError.HostNotFound))
    assertFalse(JourneyRefresh.invalidatesItineraryId(EscaleError.Unknown(cause = null)))
  }

  @Test
  fun `le repli retient le trajet le plus proche en heure de depart`() {
    val early = journey(id = "tot", legs = listOf(transit(afterMinutes = 0, minutes = 10)))
    val target = journey(id = "cible", legs = listOf(transit(afterMinutes = 12, minutes = 10)))
    val late = journey(id = "tard", legs = listOf(transit(afterMinutes = 40, minutes = 10)))

    val closest = JourneyRefresh.closestToDeparture(
      journeys = listOf(early, late, target),
      reference = ORIGIN.plus(Duration.ofMinutes(14)),
    )

    assertEquals("cible", closest?.id)
  }

  @Test
  fun `le repli retient aussi un trajet plus tot que la reference`() {
    val before = journey(id = "avant", legs = listOf(transit(afterMinutes = 10, minutes = 10)))
    val after = journey(id = "apres", legs = listOf(transit(afterMinutes = 30, minutes = 10)))

    val closest = JourneyRefresh.closestToDeparture(
      journeys = listOf(after, before),
      reference = ORIGIN.plus(Duration.ofMinutes(12)),
    )

    assertEquals("avant", closest?.id)
  }

  @Test
  fun `une page vide ne rend aucun trajet`() {
    assertNull(JourneyRefresh.closestToDeparture(journeys = emptyList(), reference = Instant.EPOCH))
  }

  @Test
  fun `une portion en transport en commun classe le trajet dans l'onglet Transport`() {
    val mixed = journey(legs = listOf(walk(afterMinutes = 0, minutes = 5), transit(afterMinutes = 5, minutes = 10)))
    assertEquals(JourneyCategory.TRANSIT, JourneyRefresh.categoryOf(mixed))
  }

  @Test
  fun `un rabattement en libre-service vers une gare reste un trajet en transport en commun`() {
    val legs = listOf(rental(afterMinutes = 0, minutes = 6), transit(afterMinutes = 6, minutes = 10))
    assertEquals(JourneyCategory.TRANSIT, JourneyRefresh.categoryOf(journey(legs = legs)))
  }

  @Test
  fun `un trajet en libre-service seul releve de l'onglet Velo`() {
    val solo = journey(legs = listOf(rental(afterMinutes = 0, minutes = 20)))
    assertEquals(JourneyCategory.BIKE, JourneyRefresh.categoryOf(solo))
  }

  @Test
  fun `un trajet sans autre portion qu'a pied releve de l'onglet A pied`() {
    assertEquals(JourneyCategory.WALK, JourneyRefresh.categoryOf(journey(legs = listOf(walk(0, 20)))))
  }

  @Test
  fun `un trajet en voiture releve de l'onglet Voiture`() {
    val start = ORIGIN
    val end = start.plus(Duration.ofMinutes(25))
    val car = JourneyLeg.Car(
      startTime = start,
      endTime = end,
      scheduledStartTime = start,
      scheduledEndTime = end,
      duration = Duration.ofMinutes(25),
      from = Place("A", LatLon(48.0, 2.0), null, null, start, start),
      to = Place("B", LatLon(48.1, 2.1), null, null, end, end),
    )
    assertEquals(JourneyCategory.CAR, JourneyRefresh.categoryOf(journey(legs = listOf(car))))
  }

  private fun rental(afterMinutes: Long, minutes: Long): JourneyLeg.Rental {
    val start = ORIGIN.plus(Duration.ofMinutes(afterMinutes))
    val end = start.plus(Duration.ofMinutes(minutes))
    return JourneyLeg.Rental(
      startTime = start,
      endTime = end,
      scheduledStartTime = start,
      scheduledEndTime = end,
      duration = Duration.ofMinutes(minutes),
      from = Place("A", LatLon(48.0, 2.0), null, null, start, start),
      to = Place("B", LatLon(48.1, 2.1), null, null, end, end),
    )
  }
}
