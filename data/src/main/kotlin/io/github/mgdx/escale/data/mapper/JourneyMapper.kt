package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.data.dto.PlanItineraryDto
import io.github.mgdx.escale.data.dto.PlanLegDto
import io.github.mgdx.escale.data.dto.PlanResponseDto
import java.time.Duration
import java.time.Instant

// `Itinerary` -> Journey et `Leg` -> JourneyLeg.
//
// Deux principes gouvernent ce fichier :
// - les heures théoriques ET réelles sont conservées, seule façon de calculer un retard
//   (SPEC.md § 5.2). `realTime = false` est reporté tel quel, pour que l'interface s'abstienne de
//   colorer une portion dont personne ne connaît l'avance ou le retard ;
// - une portion dont le mode est inconnu n'est jamais perdue : elle devient une portion en
//   transport en commun sans ligne, plutôt qu'une réponse rejetée.

/**
 * La page de résultats.
 *
 * Les deux champs sont traités : `itineraries` porte les trajets dépendant des horaires,
 * `direct` les trajets qui n'en dépendent pas. Ignorer `direct` viderait les onglets Voiture,
 * Vélo et À pied, qui ne reçoivent jamais rien d'autre (SPEC.md § 5.2).
 */
internal fun PlanResponseDto.toDomain(): JourneyPage = JourneyPage(
  journeys = itineraries.map { it.toDomain() },
  direct = direct.map { it.toDomain() },
  previousPageCursor = previousPageCursor.trimToNull(),
  nextPageCursor = nextPageCursor.trimToNull(),
)

/**
 * Un trajet proposé.
 *
 * L'API ne donne pas d'horaire théorique au niveau de l'itinéraire : il se déduit de la première
 * et de la dernière portion, comme le documente le modèle de domaine.
 */
internal fun PlanItineraryDto.toDomain(): Journey {
  val mapped = legs.map { it.toDomain() }
  val start = instantOrNull(startTime) ?: mapped.firstOrNull()?.startTime ?: Instant.EPOCH
  val end = instantOrNull(endTime) ?: mapped.lastOrNull()?.endTime ?: start
  return Journey(
    id = id.trimToNull(),
    startTime = start,
    endTime = end,
    scheduledStartTime = mapped.firstOrNull()?.scheduledStartTime ?: start,
    scheduledEndTime = mapped.lastOrNull()?.scheduledEndTime ?: end,
    duration = Duration.ofSeconds(duration),
    transfers = transfers,
    legs = mapped,
  )
}

/**
 * Choisit le sous-type de portion d'après le mode annoncé par le serveur.
 *
 * Les modes hors périmètre de la v1 ne créent pas de sixième sous-type (docs/architecture.md § 4) :
 * `HGV`, `CAR_PARKING` et `CAR_DROPOFF` sont des variantes de la voiture ; `ODM`, `RIDE_SHARING` et
 * `FLEX` sont à cheval sur la rue et la base horaire, et se rangent donc en transport en commun
 * quand ils portent une course (`tripId`), en voiture sinon.
 */
internal fun PlanLegDto.toDomain(): JourneyLeg {
  val basics = basics()
  return when (transitModeOf(mode)) {
    TransitMode.WALK -> walkLeg(basics)

    TransitMode.BIKE -> bikeLeg(basics)

    TransitMode.RENTAL -> rentalLeg(basics)

    TransitMode.CAR, TransitMode.HGV, TransitMode.CAR_PARKING, TransitMode.CAR_DROPOFF -> carLeg(basics)

    TransitMode.ODM, TransitMode.RIDE_SHARING, TransitMode.FLEX ->
      if (tripId.trimToNull() != null) transitLeg(basics) else carLeg(basics)

    else -> transitLeg(basics)
  }
}

/** Les champs que porte toute portion, quel que soit son mode (docs/architecture.md § 4). */
private data class LegBasics(
  val startTime: Instant,
  val endTime: Instant,
  val scheduledStartTime: Instant,
  val scheduledEndTime: Instant,
  val duration: Duration,
  val from: Place,
  val to: Place,
  val distanceMeters: Double?,
  val geometry: List<LatLon>,
  val realTime: Boolean,
  val cancelled: Boolean,
  val alerts: List<Disruption>,
)

private fun PlanLegDto.basics(): LegBasics {
  val scheduledStart = instantOrNull(scheduledStartTime) ?: instantOrNull(startTime) ?: Instant.EPOCH
  val scheduledEnd = instantOrNull(scheduledEndTime) ?: instantOrNull(endTime) ?: scheduledStart
  val start = instantOrNull(startTime) ?: scheduledStart
  val end = instantOrNull(endTime) ?: scheduledEnd
  return LegBasics(
    startTime = start,
    endTime = end,
    scheduledStartTime = scheduledStart,
    scheduledEndTime = scheduledEnd,
    // La durée annoncée par le serveur, et non `end - start` : sur un cheminement de
    // correspondance, MOTIS y a déjà intégré la marge demandée par l'usager.
    duration = Duration.ofSeconds(duration),
    from = from?.toDomain(prefersDeparture = true, fallback = start) ?: unknownPlace(start),
    to = to?.toDomain(prefersDeparture = false, fallback = end) ?: unknownPlace(end),
    distanceMeters = distance,
    geometry = legGeometry.decodePolyline(),
    realTime = realTime,
    cancelled = cancelled,
    alerts = alerts.map { it.toDomain() },
  )
}

private fun PlanLegDto.transitLeg(basics: LegBasics): JourneyLeg.Transit {
  val line = displayName.trimToNull() ?: routeShortName.trimToNull() ?: routeLongName.trimToNull().orEmpty()
  return JourneyLeg.Transit(
    startTime = basics.startTime,
    endTime = basics.endTime,
    scheduledStartTime = basics.scheduledStartTime,
    scheduledEndTime = basics.scheduledEndTime,
    duration = basics.duration,
    from = basics.from,
    to = basics.to,
    distanceMeters = basics.distanceMeters,
    geometry = basics.geometry,
    realTime = basics.realTime,
    cancelled = basics.cancelled,
    alerts = basics.alerts,
    mode = transitModeOf(mode),
    tripId = tripId.trimToNull(),
    lineName = line,
    // Le numéro court n'est repris que s'il apporte quelque chose de plus que le libellé affiché.
    routeShortName = routeShortName.trimToNull()?.takeIf { it != line },
    headsign = headsign.trimToNull(),
    agencyName = agencyName.trimToNull(),
    routeColor = hexColorOrNull(routeColor),
    routeTextColor = hexColorOrNull(routeTextColor),
    intermediateStops = intermediateStops.map { it.toStopVisit(basics.startTime) },
    wheelchairAccessible = wheelchairAccessOf(wheelchairAccessible),
    bikesAllowed = bikesAllowed,
    interlineWithPreviousLeg = interlineWithPreviousLeg,
  )
}

private fun PlanLegDto.walkLeg(basics: LegBasics): JourneyLeg.Walk = JourneyLeg.Walk(
  startTime = basics.startTime,
  endTime = basics.endTime,
  scheduledStartTime = basics.scheduledStartTime,
  scheduledEndTime = basics.scheduledEndTime,
  duration = basics.duration,
  from = basics.from,
  to = basics.to,
  distanceMeters = basics.distanceMeters,
  geometry = basics.geometry,
  realTime = basics.realTime,
  cancelled = basics.cancelled,
  alerts = basics.alerts,
  steps = steps.map { it.toDomain() },
)

private fun PlanLegDto.bikeLeg(basics: LegBasics): JourneyLeg.Bike = JourneyLeg.Bike(
  startTime = basics.startTime,
  endTime = basics.endTime,
  scheduledStartTime = basics.scheduledStartTime,
  scheduledEndTime = basics.scheduledEndTime,
  duration = basics.duration,
  from = basics.from,
  to = basics.to,
  distanceMeters = basics.distanceMeters,
  geometry = basics.geometry,
  realTime = basics.realTime,
  cancelled = basics.cancelled,
  alerts = basics.alerts,
  steps = steps.map { it.toDomain() },
)

private fun PlanLegDto.carLeg(basics: LegBasics): JourneyLeg.Car = JourneyLeg.Car(
  startTime = basics.startTime,
  endTime = basics.endTime,
  scheduledStartTime = basics.scheduledStartTime,
  scheduledEndTime = basics.scheduledEndTime,
  duration = basics.duration,
  from = basics.from,
  to = basics.to,
  distanceMeters = basics.distanceMeters,
  geometry = basics.geometry,
  realTime = basics.realTime,
  cancelled = basics.cancelled,
  alerts = basics.alerts,
  steps = steps.map { it.toDomain() },
)

private fun PlanLegDto.rentalLeg(basics: LegBasics): JourneyLeg.Rental = JourneyLeg.Rental(
  startTime = basics.startTime,
  endTime = basics.endTime,
  scheduledStartTime = basics.scheduledStartTime,
  scheduledEndTime = basics.scheduledEndTime,
  duration = basics.duration,
  from = basics.from,
  to = basics.to,
  distanceMeters = basics.distanceMeters,
  geometry = basics.geometry,
  realTime = basics.realTime,
  cancelled = basics.cancelled,
  alerts = basics.alerts,
  rental = rental?.toDomain(),
  steps = steps.map { it.toDomain() },
)

/** Repli pour une portion dont le serveur aurait omis une extrémité, ce que le schéma interdit. */
private fun unknownPlace(time: Instant): Place = Place(
  name = "",
  coordinates = LatLon(lat = 0.0, lon = 0.0),
  stopId = null,
  track = null,
  scheduledTime = time,
  time = time,
)
