package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.JourneyLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyShareTest {

  @Test
  fun `le plan de partage commence par le resume et le depart, et finit par l'arrivee`() {
    val trip = journey(legs = listOf(walk(0, 5), transit(5, 15)))

    val lines = shareLinesOf(trip)

    assertTrue(lines.first() is JourneyShareLine.Summary)
    val origin = lines[1] as JourneyShareLine.Endpoint
    assertTrue(origin.isOrigin)
    assertEquals(trip.startTime, origin.time)
    val destination = lines.last() as JourneyShareLine.Endpoint
    assertEquals(false, destination.isOrigin)
    assertEquals(trip.endTime, destination.time)
  }

  @Test
  fun `chaque portion produit une ligne, dans l'ordre du trajet`() {
    val trip = journey(legs = listOf(walk(0, 5), transit(5, 15), walk(20, 4)))

    val legLines = shareLinesOf(trip).filter { it is JourneyShareLine.Transit || it is JourneyShareLine.Street }

    assertEquals(3, legLines.size)
    assertTrue(legLines[0] is JourneyShareLine.Street)
    assertTrue(legLines[1] is JourneyShareLine.Transit)
    assertTrue(legLines[2] is JourneyShareLine.Street)
  }

  @Test
  fun `le resume reprend les bornes et les correspondances du trajet`() {
    val trip = journey(legs = listOf(transit(0, 10), transit(15, 10)))

    val summary = shareLinesOf(trip).first() as JourneyShareLine.Summary

    assertEquals(trip.startTime, summary.start)
    assertEquals(trip.endTime, summary.end)
    assertEquals(trip.duration, summary.duration)
    assertEquals(trip.transfers, summary.transfers)
  }

  @Test
  fun `le numero court de ligne passe devant le nom arbitre par le serveur`() {
    val leg = transit(0, 10, lineName = "Ligne 21 Nation").copy(routeShortName = "21")

    assertEquals("21", transitLineLabel(leg))
  }

  @Test
  fun `un numero court vide retombe sur le nom arbitre par le serveur`() {
    val leg = transit(0, 10, lineName = "RER A").copy(routeShortName = "  ")

    assertEquals("RER A", transitLineLabel(leg))
  }

  @Test
  fun `une ligne que le serveur ne nomme pas n'a pas de libelle`() {
    val leg = transit(0, 10, lineName = "").copy(routeShortName = null)

    assertNull(transitLineLabel(leg))
    val shared = shareLinesOf(journey(legs = listOf(leg))).filterIsInstance<JourneyShareLine.Transit>().single()
    assertNull(shared.label)
  }

  @Test
  fun `une portion en libre-service est partagee comme une portion de rue`() {
    val trip = journey(legs = listOf(walk(0, 5)))

    val line = shareLinesOf(trip).filterIsInstance<JourneyShareLine.Street>().single()

    assertTrue(line.leg is JourneyLeg.Walk)
  }
}
