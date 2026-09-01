package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.geo.PolylineDecoder
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TimeWindow
import io.github.mgdx.escale.core.model.TravelStep
import io.github.mgdx.escale.data.dto.PlanAlertDto
import io.github.mgdx.escale.data.dto.PlanPlaceDto
import io.github.mgdx.escale.data.dto.PlanPolylineDto
import io.github.mgdx.escale.data.dto.PlanRentalDto
import io.github.mgdx.escale.data.dto.PlanStepDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Une extrémité de portion n'utilise qu'une heure sur les deux que porte le schéma `Place` :
 * l'heure de départ à l'origine, l'heure d'arrivée à destination.
 *
 * @param prefersDeparture vrai pour l'origine de la portion, faux pour sa destination.
 * @param fallback heure retenue quand le serveur n'en donne aucune, ce qui arrive sur un trajet
 *   direct : le lieu prend alors l'heure de la portion elle-même.
 */
internal fun PlanPlaceDto.toDomain(prefersDeparture: Boolean, fallback: Instant): Place {
  val scheduledRaw = if (prefersDeparture) {
    scheduledDeparture ?: scheduledArrival
  } else {
    scheduledArrival ?: scheduledDeparture
  }
  val actualRaw = if (prefersDeparture) departure ?: arrival else arrival ?: departure
  val scheduled = instantOrNull(scheduledRaw) ?: instantOrNull(actualRaw) ?: fallback
  return Place(
    name = name,
    coordinates = LatLon(lat = lat, lon = lon),
    stopId = stopId.trimToNull(),
    // Le quai temps réel prime sur celui de la base horaire, qui sert de repli.
    track = track.trimToNull() ?: scheduledTrack.trimToNull(),
    scheduledTime = scheduled,
    time = instantOrNull(actualRaw) ?: scheduled,
    level = level,
  )
}

/**
 * Un arrêt intermédiaire garde ses deux heures : l'écran de détail affiche la desserte complète
 * (SPEC.md § 5.3). Une borne nulle est normale au terminus.
 */
internal fun PlanPlaceDto.toStopVisit(fallback: Instant): StopVisit = StopVisit(
  place = toDomain(prefersDeparture = true, fallback = fallback),
  arrival = instantOrNull(arrival),
  departure = instantOrNull(departure),
  cancelled = cancelled,
)

/** Une manœuvre pas-à-pas. Le tracé du segment est décodé avec la précision que le serveur annonce. */
internal fun PlanStepDto.toDomain(): TravelStep = TravelStep(
  direction = stepDirectionOf(relativeDirection),
  streetName = streetName,
  distanceMeters = distance,
  geometry = polyline.decodePolyline(),
  fromLevel = fromLevel,
  toLevel = toLevel,
  elevationUpMeters = elevationUp,
  elevationDownMeters = elevationDown,
  toll = toll,
  accessRestriction = accessRestriction.trimToNull(),
)

/**
 * Une perturbation. Seule la période d'impact est retenue : c'est celle qui dit quand le service
 * est réellement perturbé, là où la période de communication ne dit que quand montrer le message.
 */
internal fun PlanAlertDto.toDomain(): Disruption = Disruption(
  headerText = headerText,
  descriptionText = descriptionText,
  severity = disruptionSeverityOf(severityLevel),
  cause = disruptionCauseOf(cause),
  effect = disruptionEffectOf(effect),
  periods = impactPeriod.map { TimeWindow(start = instantOrNull(it.start), end = instantOrNull(it.end)) },
  url = url.trimToNull(),
)

/** Le système de libre-service emprunté sur une portion. */
internal fun PlanRentalDto.toDomain(): RentalInfo = RentalInfo(
  systemId = systemId,
  systemName = systemName.trimToNull(),
  providerId = providerId.trimToNull(),
  color = hexColorOrNull(color),
  url = url.trimToNull(),
  // Vides pour un véhicule en free-floating : l'interface s'en sert pour distinguer les deux cas.
  fromStationName = fromStationName.trimToNull(),
  toStationName = toStationName.trimToNull(),
  rentalUriAndroid = rentalUriAndroid.trimToNull(),
  formFactor = rentalFormFactorOf(formFactor),
  propulsionType = rentalPropulsionTypeOf(propulsionType),
  returnConstraint = rentalReturnConstraintOf(returnConstraint),
)

/**
 * **Le piège de SPEC.md § 4.3, désamorcé ici.** Les points d'entrée `v6` encodent leurs polylignes
 * en précision 6 et non 7 : la précision est lue dans le champ `precision` de la réponse plutôt que
 * supposée, ce qui reste juste si MOTIS en change. Une chaîne vide, cas normal avec
 * `detailedLegs=false`, rend une liste vide.
 */
internal fun PlanPolylineDto?.decodePolyline(): List<LatLon> =
  if (this == null) emptyList() else PolylineDecoder.decode(points, precision)

/**
 * Les couleurs GTFS arrivent sans dièse (`702082`), là où le domaine et l'interface attendent
 * `#RRGGBB`. Une couleur vide reste nulle : le schéma prévoit explicitement le cas.
 */
internal fun hexColorOrNull(raw: String?): String? {
  val value = raw.trimToNull() ?: return null
  return if (value.startsWith("#")) value else "#$value"
}

/**
 * Lit une date-heure ISO-8601, avec décalage horaire ou en `Z`. Rend `null` plutôt que de lever :
 * une portion à l'horaire illisible ne doit pas emporter toute la réponse.
 */
// L'exception est délibérément avalée : SPEC.md § 8 et § 11 interdisent de journaliser une donnée
// de requête, et son message reprendrait la valeur reçue.
@Suppress("SwallowedException")
internal fun instantOrNull(raw: String?): Instant? {
  if (raw.isNullOrBlank()) return null
  return try {
    OffsetDateTime.parse(raw).toInstant()
  } catch (malformed: DateTimeParseException) {
    null
  }
}

/** Une chaîne vide ou blanche de l'API vaut « champ absent » côté domaine. */
internal fun String?.trimToNull(): String? = this?.trim()?.ifEmpty { null }
