package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** La desserte complète d'une course, recollée à partir des trois champs de l'API (SPEC.md § 5.3). */
class TripCallsTest {

  private val origin: Instant = Instant.parse("2026-09-02T05:34:00Z")

  @Test
  fun `les deux extremites encadrent les arrets intermediaires`() {
    // `intermediateStops` exclut l'origine et le terminus : c'est ce que rend api.transitous.org,
    // et c'est ce qui fait qu'une desserte affichée sans recollement perdrait ses deux bouts.
    val calls = tripOf(leg(from = "Altona", to = "Nürnberg Hbf", between = listOf("Hamburg Hbf", "Leipzig Hbf"))).calls
    assertEquals(listOf("Altona", "Hamburg Hbf", "Leipzig Hbf", "Nürnberg Hbf"), calls.map { it.place.name })
  }

  @Test
  fun `on ne descend pas a l'origine et on ne repart pas du terminus`() {
    val calls = tripOf(leg(from = "Altona", to = "Nürnberg Hbf", between = listOf("Hamburg Hbf"))).calls
    assertNull(calls.first().arrival)
    assertEquals(origin, calls.first().departure)
    assertNull(calls.last().departure)
    assertTrue(calls.last().arrival != null)
  }

  @Test
  fun `un arret de jonction entre deux portions n'est affiche qu'une fois`() {
    // `joinInterlinedLegs` vaut `true` par défaut, mais un serveur peut rendre plusieurs portions.
    // L'arrêt commun porte alors l'arrivée de la première et le départ de la seconde.
    val first = leg(from = "Altona", to = "Hamburg Hbf", between = emptyList())
    val second = leg(from = "Hamburg Hbf", to = "Berlin Hbf", between = emptyList(), shiftMinutes = 20)
    val calls = tripOf(first, second).calls

    assertEquals(listOf("Altona", "Hamburg Hbf", "Berlin Hbf"), calls.map { it.place.name })
    val junction = calls[1]
    assertEquals(first.to.time, junction.arrival)
    assertEquals(second.from.time, junction.departure)
  }

  @Test
  fun `le quai connu d'un seul cote survit au recollement`() {
    val first = leg(from = "Altona", to = "Hamburg Hbf", between = emptyList())
    val second = leg(
      from = "Hamburg Hbf",
      to = "Berlin Hbf",
      between = emptyList(),
      shiftMinutes = 20,
      departureTrack = "14",
    )
    assertEquals("14", tripOf(first, second).calls[1].place.track)
  }

  @Test
  fun `une course annulee marque tous ses arrets, extremites comprises`() {
    // Le modèle de domaine ne porte `cancelled` que sur un arrêt intermédiaire : les extrémités
    // héritent de la portion, ce qui reste exact pour une course supprimée.
    val calls = tripOf(leg(from = "A", to = "B", between = listOf("M"), cancelled = true)).calls
    assertTrue(calls.all { it.cancelled })
  }

  @Test
  fun `les portions de rue d'un itineraire ne produisent aucun arret`() {
    // `/api/v6/trip` ne rend qu'une course, mais le même calcul sert sur un itinéraire ordinaire :
    // un cheminement à pied n'a pas de desserte.
    val walk = JourneyLeg.Walk(
      startTime = origin,
      endTime = origin.plusSeconds(300),
      scheduledStartTime = origin,
      scheduledEndTime = origin.plusSeconds(300),
      duration = Duration.ofMinutes(5),
      from = stop("Rue", origin),
      to = stop("Gare", origin.plusSeconds(300)),
    )
    val journey = Journey(
      id = null,
      startTime = origin,
      endTime = origin.plusSeconds(300),
      scheduledStartTime = origin,
      scheduledEndTime = origin.plusSeconds(300),
      duration = Duration.ofMinutes(5),
      transfers = 0,
      legs = listOf(walk),
    )
    assertTrue(journey.calls.isEmpty())
  }

  // --- Fabriques d'exemples -----------------------------------------------------------------

  private fun stop(name: String, at: Instant, track: String? = null) = Place(
    name = name,
    coordinates = LatLon(lat = 53.55, lon = 10.0),
    stopId = "stop:$name",
    track = track,
    scheduledTime = at,
    time = at,
  )

  @Suppress("LongParameterList")
  private fun leg(
    from: String,
    to: String,
    between: List<String>,
    shiftMinutes: Long = 0,
    cancelled: Boolean = false,
    departureTrack: String? = null,
  ): JourneyLeg.Transit {
    val start = origin.plusSeconds(shiftMinutes * 60)
    val end = start.plusSeconds(3600)
    return JourneyLeg.Transit(
      startTime = start,
      endTime = end,
      scheduledStartTime = start,
      scheduledEndTime = end,
      duration = Duration.ofHours(1),
      from = stop(from, start, departureTrack),
      to = stop(to, end),
      cancelled = cancelled,
      mode = TransitMode.HIGHSPEED_RAIL,
      lineName = "ICE 91",
      intermediateStops = between.mapIndexed { index, name ->
        val at = start.plusSeconds((index + 1) * 600L)
        StopVisit(place = stop(name, at), arrival = at, departure = at.plusSeconds(120), cancelled = cancelled)
      },
    )
  }

  private fun tripOf(vararg legs: JourneyLeg.Transit) = Journey(
    id = null,
    startTime = legs.first().startTime,
    endTime = legs.last().endTime,
    scheduledStartTime = legs.first().scheduledStartTime,
    scheduledEndTime = legs.last().scheduledEndTime,
    duration = Duration.between(legs.first().startTime, legs.last().endTime),
    transfers = 0,
    legs = legs.toList(),
  )
}
