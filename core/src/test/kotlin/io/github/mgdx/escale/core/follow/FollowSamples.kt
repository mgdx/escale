package io.github.mgdx.escale.core.follow

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TransitMode
import java.time.Duration
import java.time.Instant

/** Point d'origine des trajets d'exemple : une heure ronde, pour que les calculs se lisent. */
internal val T0: Instant = Instant.parse("2026-09-01T08:00:00Z")

internal fun at(minutes: Long): Instant = T0.plusSeconds(minutes * 60)

internal fun place(name: String, at: Instant, track: String? = null) = Place(
  name = name,
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  stopId = null,
  track = track,
  scheduledTime = at,
  time = at,
)

internal fun stop(name: String, minute: Long, cancelled: Boolean = false) = StopVisit(
  place = place(name, at(minute)),
  arrival = at(minute).minusSeconds(30),
  departure = at(minute),
  cancelled = cancelled,
)

/** Une portion en transport en commun de [from] à [to], avec ses arrêts intermédiaires à la minute. */
@Suppress("LongParameterList")
internal fun transit(
  from: String,
  to: String,
  startMinute: Long,
  endMinute: Long,
  stops: List<StopVisit> = emptyList(),
  line: String = "4",
  headsign: String? = "Porte d'Orléans",
  track: String? = null,
  cancelled: Boolean = false,
) = JourneyLeg.Transit(
  startTime = at(startMinute),
  endTime = at(endMinute),
  scheduledStartTime = at(startMinute),
  scheduledEndTime = at(endMinute),
  duration = Duration.ofMinutes(endMinute - startMinute),
  from = place(from, at(startMinute), track),
  to = place(to, at(endMinute)),
  cancelled = cancelled,
  mode = TransitMode.SUBWAY,
  routeShortName = line,
  headsign = headsign,
  intermediateStops = stops,
)

internal fun walk(from: String, to: String, startMinute: Long, endMinute: Long) = JourneyLeg.Walk(
  startTime = at(startMinute),
  endTime = at(endMinute),
  scheduledStartTime = at(startMinute),
  scheduledEndTime = at(endMinute),
  duration = Duration.ofMinutes(endMinute - startMinute),
  from = place(from, at(startMinute)),
  to = place(to, at(endMinute)),
)

internal fun journey(id: String? = "it-1", legs: List<JourneyLeg>): Journey {
  val first = legs.first()
  val last = legs.last()
  return Journey(
    id = id,
    startTime = first.startTime,
    endTime = last.endTime,
    scheduledStartTime = first.scheduledStartTime,
    scheduledEndTime = last.scheduledEndTime,
    duration = Duration.between(first.startTime, last.endTime),
    transfers = (legs.count { it is JourneyLeg.Transit } - 1).coerceAtLeast(0),
    legs = legs,
  )
}

/**
 * Le trajet de référence de SPEC.md § 5.3.1 : cinq minutes à pied, la ligne 4 sur cinq arrêts
 * intermédiaires, une correspondance à pied, la ligne 6 sur un arrêt, et l'arrivée.
 */
internal fun referenceJourney(): Journey = journey(
  legs = listOf(
    walk("Maison", "Châtelet", 0, 5),
    transit(
      from = "Châtelet",
      to = "Montparnasse",
      startMinute = 10,
      endMinute = 22,
      stops = listOf(
        stop("Cité", 12),
        stop("Saint-Michel", 14),
        stop("Odéon", 16),
        stop("Saint-Germain", 18),
        stop("Saint-Placide", 20),
      ),
      track = "2",
    ),
    walk("Montparnasse", "Montparnasse", 22, 26),
    transit(
      from = "Montparnasse",
      to = "Pasteur",
      startMinute = 30,
      endMinute = 34,
      stops = listOf(stop("Falguière", 32)),
      line = "6",
      headsign = "Nation",
    ),
    walk("Pasteur", "Bureau", 34, 40),
  ),
)
