package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Réponses de `GET /api/v6/map/stops` et de `GET /api/v6/stop` (SPEC.md § 5.7).
 *
 * Ces DTO ne sortent jamais de `:data` (docs/architecture.md § 1 et § 2). Tous les champs sont
 * facultatifs et les types énumérés sont lus en `String` : un serveur plus récent qui ajoute une
 * valeur ne doit pas faire échouer la lecture de la réponse entière.
 *
 * Les champs `arrival`, `departure`, `alerts` et les nombreux champs `flex*` du schéma `Place` ne
 * sont volontairement pas lus : un arrêt de carte ne s'inscrit dans aucun trajet, il n'a donc ni
 * heure ni annulation.
 */
@Serializable
internal data class StopPlaceDto(
  val name: String = "",
  /** Absent sur un point qui n'est pas un arrêt : un tel point n'a rien à faire sur la carte. */
  val stopId: String? = null,
  val lat: Double = 0.0,
  val lon: Double = 0.0,
  /** `modes` du schéma `Place` : « available transport modes for stops ». */
  val modes: List<String> = emptyList(),
)

/** Corps de `GET /api/v6/stop` : l'arrêt, et les lignes qui le desservent. */
@Serializable
internal data class StopInfoDto(val place: StopPlaceDto = StopPlaceDto(), val routes: List<StopRouteDto> = emptyList())

/** Décalque du schéma `Route` : une ligne desservant un arrêt. */
@Serializable
internal data class StopRouteDto(
  val routeId: String = "",
  val routeShortName: String = "",
  val routeLongName: String = "",
  val mode: String? = null,
  val agencyName: String = "",
  val routeColor: String? = null,
  val routeTextColor: String? = null,
)
