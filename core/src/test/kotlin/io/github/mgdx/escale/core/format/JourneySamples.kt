package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import java.time.Duration
import java.time.Instant

/** Point d'origine des trajets d'exemple : une heure ronde, pour que les calculs se lisent. */
internal val ORIGIN: Instant = Instant.parse("2026-09-01T08:00:00Z")

internal fun place(name: String, at: Instant, scheduled: Instant = at) = Place(
  name = name,
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  stopId = null,
  track = null,
  scheduledTime = scheduled,
  time = at,
)

/** Une portion à pied de [minutes], commençant [afterMinutes] après [ORIGIN]. */
internal fun walk(afterMinutes: Long, minutes: Long): JourneyLeg.Walk {
  val start = ORIGIN.plusSeconds(afterMinutes * 60)
  val end = start.plusSeconds(minutes * 60)
  return JourneyLeg.Walk(
    startTime = start,
    endTime = end,
    scheduledStartTime = start,
    scheduledEndTime = end,
    duration = Duration.ofMinutes(minutes),
    from = place("A", start),
    to = place("B", end),
  )
}

/** Une portion en transport en commun, avec ou sans temps réel. */
@Suppress("LongParameterList")
internal fun transit(
  afterMinutes: Long,
  minutes: Long,
  realTime: Boolean = false,
  delayMinutes: Long = 0,
  cancelled: Boolean = false,
  lineName: String = "21",
): JourneyLeg.Transit {
  val scheduledStart = ORIGIN.plusSeconds(afterMinutes * 60)
  val scheduledEnd = scheduledStart.plusSeconds(minutes * 60)
  val shift = Duration.ofMinutes(delayMinutes)
  return JourneyLeg.Transit(
    startTime = scheduledStart.plus(shift),
    endTime = scheduledEnd.plus(shift),
    scheduledStartTime = scheduledStart,
    scheduledEndTime = scheduledEnd,
    duration = Duration.ofMinutes(minutes),
    from = place("A", scheduledStart.plus(shift), scheduledStart),
    to = place("B", scheduledEnd.plus(shift), scheduledEnd),
    realTime = realTime,
    cancelled = cancelled,
    lineName = lineName,
  )
}

/** Un trajet dont les bornes se déduisent de ses portions, comme le fait le mapping de `:data`. */
internal fun journey(id: String? = null, legs: List<JourneyLeg>): Journey {
  val first = legs.first()
  val last = legs.last()
  return Journey(
    id = id,
    startTime = first.startTime,
    endTime = last.endTime,
    scheduledStartTime = first.scheduledStartTime,
    scheduledEndTime = last.scheduledEndTime,
    duration = Duration.between(first.startTime, last.endTime),
    transfers = legs.count { it is JourneyLeg.Transit }.coerceAtLeast(1) - 1,
    legs = legs,
  )
}
