package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Décalque du schéma `Leg` de l'OpenAPI MOTIS.
 *
 * `mode`, `wheelchairAccessible` et les autres énumérations sont lues en `String` : l'API en ajoute
 * régulièrement des valeurs, et une valeur inconnue ne doit pas faire échouer toute la réponse.
 */
@Serializable
internal data class PlanLegDto(
  val mode: String? = null,
  val from: PlanPlaceDto? = null,
  val to: PlanPlaceDto? = null,
  /** Durée de la portion en secondes, marge de correspondance déjà intégrée par le serveur. */
  val duration: Long = 0,
  val startTime: String? = null,
  val endTime: String? = null,
  val scheduledStartTime: String? = null,
  val scheduledEndTime: String? = null,
  /** Faux : aucune donnée temps réel, la portion ne doit pas être annoncée « à l'heure ». */
  val realTime: Boolean = false,
  /** Distance parcourue en mètres, absente sur une portion en transport en commun. */
  val distance: Double? = null,
  val interlineWithPreviousLeg: Boolean = false,
  val headsign: String? = null,
  val routeColor: String? = null,
  val routeTextColor: String? = null,
  val agencyName: String? = null,
  val tripId: String? = null,
  val routeShortName: String? = null,
  val routeLongName: String? = null,
  val displayName: String? = null,
  val cancelled: Boolean = false,
  val intermediateStops: List<PlanPlaceDto> = emptyList(),
  val legGeometry: PlanPolylineDto? = null,
  val steps: List<PlanStepDto> = emptyList(),
  val rental: PlanRentalDto? = null,
  val alerts: List<PlanAlertDto> = emptyList(),
  val bikesAllowed: Boolean? = null,
  /** `ACCESSIBLE` ou `NOT_ACCESSIBLE`. Absent quand le transporteur ne publie pas l'information. */
  val wheelchairAccessible: String? = null,
)

/** Décalque du schéma `Place` : extrémité de portion ou arrêt intermédiaire. */
@Serializable
internal data class PlanPlaceDto(
  val name: String = "",
  val stopId: String? = null,
  val lat: Double = 0.0,
  val lon: Double = 0.0,
  val level: Double? = null,
  val arrival: String? = null,
  val departure: String? = null,
  val scheduledArrival: String? = null,
  val scheduledDeparture: String? = null,
  val scheduledTrack: String? = null,
  /** Quai courant, mis à jour en temps réel quand le serveur le sait. */
  val track: String? = null,
  val cancelled: Boolean = false,
  /**
   * Perturbations propres à ce point.
   *
   * Le schéma `Place` porte son **propre** tableau d'alertes, distinct de celui de la portion :
   * `/api/v6/trip` y annonce ce qui ne concerne qu'un arrêt de la desserte. Ne pas le lire ferait
   * disparaître un « arrêt non desservi » qui ne figure nulle part ailleurs dans la réponse.
   */
  val alerts: List<PlanAlertDto> = emptyList(),
)

/**
 * Décalque du schéma `EncodedPolyline`.
 *
 * **Le champ [precision] est le remède au piège de SPEC.md § 4.3** : les points d'entrée `v6`
 * encodent en précision 6 et non 7. Le mapping le passe tel quel au décodeur plutôt que de le
 * supposer, ce qui rend l'application juste même si MOTIS changeait d'avis.
 */
@Serializable
internal data class PlanPolylineDto(
  val points: String = "",
  val precision: Int = DEFAULT_PRECISION,
  val length: Int = 0,
) {
  companion object {
    /** Précision de tous les points d'entrée `/api/v2/…` et au-delà, dont `v6`. */
    const val DEFAULT_PRECISION = 6
  }
}

/** Décalque du schéma `StepInstruction`. Absent quand la requête a demandé `detailedLegs=false`. */
@Serializable
internal data class PlanStepDto(
  val relativeDirection: String? = null,
  val distance: Double = 0.0,
  val fromLevel: Double? = null,
  val toLevel: Double? = null,
  val polyline: PlanPolylineDto? = null,
  val streetName: String = "",
  val toll: Boolean = false,
  val accessRestriction: String? = null,
  val elevationUp: Int? = null,
  val elevationDown: Int? = null,
)

/** Décalque du schéma `Rental`, marqué « Experimental » côté serveur. */
@Serializable
internal data class PlanRentalDto(
  val systemId: String = "",
  val systemName: String? = null,
  val providerId: String? = null,
  val color: String? = null,
  val url: String? = null,
  /** Vide pour un véhicule en free-floating. */
  val fromStationName: String? = null,
  /** Vide pour un véhicule en free-floating. */
  val toStationName: String? = null,
  val rentalUriAndroid: String? = null,
  val formFactor: String? = null,
  val propulsionType: String? = null,
  val returnConstraint: String? = null,
)

/** Décalque du schéma `Alert`. */
@Serializable
internal data class PlanAlertDto(
  val headerText: String = "",
  val descriptionText: String = "",
  val severityLevel: String? = null,
  val cause: String? = null,
  val effect: String? = null,
  /** Période pendant laquelle le service est perturbé, à ne pas confondre avec la période
   *  de communication du message. */
  val impactPeriod: List<PlanTimeRangeDto> = emptyList(),
  val url: String? = null,
)

/** Décalque du schéma `TimeRange`. Une borne absente vaut « depuis toujours » ou « sans terme ». */
@Serializable
internal data class PlanTimeRangeDto(val start: String? = null, val end: String? = null)
