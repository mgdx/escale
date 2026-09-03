package io.github.mgdx.escale.ui.detail

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.RentalPropulsionType
import io.github.mgdx.escale.core.model.RentalReturnConstraint
import io.github.mgdx.escale.core.model.RentalVehicleKind
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.session.SearchSession
import java.time.Duration
import java.time.Instant

/**
 * Un dépôt d'itinéraires qui note ce qu'on lui demande, y compris **si le détail a été réclamé**.
 *
 * C'est le seul moyen de prouver la règle de SPEC.md § 7.6 : l'écran de détail, et lui seul,
 * demande `detailedLegs=true`. Un test qui regarderait le seul état affiché ne verrait pas la
 * différence entre un trajet détaillé et le trajet sommaire venu de la liste.
 */
class RecordingPlanRepository : PlanRepository {

  val refreshCalls = mutableListOf<Pair<String, Boolean>>()

  val planCalls = mutableListOf<Boolean>()

  var refreshAnswer: Outcome<Journey> = Outcome.Success(journeyOf("detaille"))

  var planAnswer: Outcome<JourneyPage> = Outcome.Success(JourneyPage(journeys = emptyList()))

  override suspend fun plan(
    query: SearchQuery,
    cursor: String?,
    detailedLegs: Boolean,
    fresh: Boolean,
  ): Outcome<JourneyPage> {
    planCalls += detailedLegs
    return planAnswer
  }

  override suspend fun refresh(itineraryId: String, detailedLegs: Boolean): Outcome<Journey> {
    refreshCalls += itineraryId to detailedLegs
    return refreshAnswer
  }

  override suspend fun clearCache(): Outcome<Unit> = Outcome.Success(Unit)
}

internal val ORIGIN: Instant = Instant.parse("2026-09-01T08:00:00Z")

internal fun at(minutes: Long): Instant = ORIGIN.plus(Duration.ofMinutes(minutes))

internal fun place(name: String, time: Instant) = Place(
  name = name,
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  stopId = null,
  track = null,
  scheduledTime = time,
  time = time,
)

internal fun walkLeg(from: Long, to: Long) = JourneyLeg.Walk(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("Rue de Bercy", at(from)),
  to = place("Gare de Lyon", at(to)),
)

internal fun transitLeg(from: Long, to: Long, stops: List<StopVisit> = emptyList()) = JourneyLeg.Transit(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("Gare de Lyon", at(from)),
  to = place("Nation", at(to)),
  realTime = true,
  mode = TransitMode.SUBWAY,
  lineName = "1",
  intermediateStops = stops,
)

internal fun journeyOf(id: String?, legs: List<JourneyLeg> = listOf(walkLeg(0, 5), transitLeg(5, 20))) = Journey(
  id = id,
  startTime = legs.first().startTime,
  endTime = legs.last().endTime,
  scheduledStartTime = legs.first().scheduledStartTime,
  scheduledEndTime = legs.last().scheduledEndTime,
  duration = Duration.between(legs.first().startTime, legs.last().endTime),
  transfers = 0,
  legs = legs,
)

/**
 * Une portion à pied dont les deux bouts sont anonymes : c'est ce que rend le serveur pour un
 * trajet exprimé en coordonnées, où il pose ses marqueurs `"START"` et `"END"` que `:data`
 * traduit en absence de nom.
 */
internal fun anonymousWalkLeg(from: Long, to: Long) = JourneyLeg.Walk(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("", at(from)),
  to = place("", at(to)),
)

/** Une recherche complète : sans elle, `SearchSession.toQuery` rend `null` et le repli est impossible. */
internal fun sessionWithSearch(): SearchSession = SearchSession().apply {
  setFrom(Location(null, "Bercy", null, LatLon(48.84, 2.38), PlaceKind.ADDRESS))
  setTo(Location(null, "Nation", null, LatLon(48.85, 2.39), PlaceKind.ADDRESS))
}

/**
 * Un dépôt de libre-service qui note **les points réellement interrogés**.
 *
 * C'est la seule façon de prouver les deux règles de sobriété de la portion partagée : aucune
 * requête tant que la portion n'est pas dépliée, et aucune requête du tout pour un véhicule en
 * free-floating, qui n'a pas de station dont compter les vélos (SPEC.md § 5.3 et § 7).
 */
class RecordingRentalsRepository : RentalsRepository {

  val calls = mutableListOf<LatLon>()

  var answer: Outcome<List<RentalAvailability>> = Outcome.Success(emptyList())

  override suspend fun stationsIn(area: BoundingBox): Outcome<List<RentalAvailability>> = answer

  override suspend fun availabilityNear(point: LatLon, radiusMeters: Int): Outcome<List<RentalAvailability>> {
    calls += point
    return answer
  }
}

/** Les deux extrémités du trajet à vélo partagé de `plan_rental_direct.json`, à Berlin. */
internal val PICKUP_POINT = LatLon(lat = 52.52369, lon = 13.37076)

internal val DROPOFF_POINT = LatLon(lat = 52.52025, lon = 13.41348)

internal const val PICKUP_STATION = "Jelbi S+U Hauptbahnhof / Washingtonplatz"

internal const val DROPOFF_STATION = "Jelbi S+U Alexanderplatz / Grunerstraße"

/**
 * Une portion en véhicule partagé.
 *
 * Les deux noms de stations sont réglables parce que c'est eux, et eux seuls, qui distinguent une
 * portion en station d'un véhicule en free-floating (SPEC.md § 5.3).
 */
internal fun rentalLeg(
  from: Long,
  to: Long,
  pickupName: String? = PICKUP_STATION,
  dropoffName: String? = DROPOFF_STATION,
) = JourneyLeg.Rental(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = Place(
    name = pickupName.orEmpty(),
    coordinates = PICKUP_POINT,
    stopId = null,
    track = null,
    scheduledTime = at(from),
    time = at(from),
  ),
  to = Place(
    name = dropoffName.orEmpty(),
    coordinates = DROPOFF_POINT,
    stopId = null,
    track = null,
    scheduledTime = at(to),
    time = at(to),
  ),
  rental = RentalInfo(
    systemId = "callabike",
    systemName = "Call a Bike",
    providerId = "de-CallaBike",
    color = null,
    url = "https://www.callabike.de",
    fromStationName = pickupName,
    toStationName = dropoffName,
    rentalUriAndroid = "https://www.callabike.de/app",
    formFactor = RentalFormFactor.BICYCLE,
    propulsionType = RentalPropulsionType.HUMAN,
    returnConstraint = RentalReturnConstraint.ANY_STATION,
  ),
)

/** Une station, telle que `/api/v1/rentals` la rend une fois traduite dans le domaine. */
internal fun availability(name: String, at: LatLon, vehicles: Int, retrievedAt: Instant) = RentalAvailability(
  stationId = name,
  name = name,
  coordinates = at,
  numVehiclesAvailable = vehicles,
  vehicleTypesAvailable = mapOf("CAB:VehicleType:885345af" to vehicles),
  retrievedAt = retrievedAt,
  vehicleKinds = mapOf(
    "CAB:VehicleType:885345af" to RentalVehicleKind(RentalFormFactor.BICYCLE, RentalPropulsionType.HUMAN),
  ),
)
