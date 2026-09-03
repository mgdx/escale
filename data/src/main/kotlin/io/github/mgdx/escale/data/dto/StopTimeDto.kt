package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `GET /api/v6/stoptimes` (SPEC.md § 5.4).
 *
 * Ces DTO ne sortent jamais de `:data` (docs/architecture.md § 1 et § 2). Tous les champs sont
 * facultatifs et les énumérations sont lues en `String` : une valeur ajoutée par une version plus
 * récente du serveur ne doit pas faire échouer la lecture de la réponse entière.
 *
 * Le schéma `StopTime` porte encore `tripFrom`, `tripTo`, `previousStops`, `nextStops`,
 * `reservation` et `source`, que l'écran n'affiche pas : `tripFrom` / `tripTo` disent l'origine et
 * le terminus de la course, ce que la girouette (`headsign`) dit déjà à l'usager, et les deux
 * listes de `Place` ne sont rendues qu'avec `fetchStops=true`, que l'application ne demande pas —
 * la desserte complète est justement l'objet de `/api/v6/trip`.
 */
@Serializable
internal data class StopTimesResponseDto(
  val stopTimes: List<StopTimeDto> = emptyList(),
  /** L'arrêt interrogé, tel que le serveur le décrit : son nom et ses modes alimentent l'écran. */
  val place: StopTimePlaceDto? = null,
  val previousPageCursor: String? = null,
  val nextPageCursor: String? = null,
)

/** Décalque du schéma `StopTime` : un départ ou une arrivée à un arrêt. */
@Serializable
internal data class StopTimeDto(
  val place: StopTimePlaceDto = StopTimePlaceDto(),
  val mode: String? = null,
  /** Faux : aucune donnée temps réel, l'entrée ne doit jamais être annoncée « à l'heure ». */
  val realTime: Boolean = false,
  val headsign: String? = null,
  val agencyName: String? = null,
  val routeColor: String? = null,
  val routeTextColor: String? = null,
  val tripId: String? = null,
  val routeShortName: String? = null,
  val routeLongName: String? = null,
  /** Libellé de ligne déjà arbitré par le serveur entre le numéro court et le nom long. */
  val displayName: String? = null,
  /** Ce passage-ci est supprimé, que la course entière le soit ou non. */
  val cancelled: Boolean = false,
  val tripCancelled: Boolean = false,
)

/**
 * Décalque du schéma `Place` **tel que `stoptimes` le remplit**.
 *
 * Il ne peut pas partager le DTO de `plan` : celui-ci ne lit ni `alerts` ni `modes`, dont l'écran
 * des prochains départs a précisément besoin — les perturbations arrivent **dans le `place` de
 * chaque `StopTime`**, et non à la racine de l'entrée, ce que le paramètre `withAlerts` dit à sa
 * façon (« alerts are omitted in the metadata of place »).
 */
@Serializable
internal data class StopTimePlaceDto(
  val name: String = "",
  val stopId: String? = null,
  val lat: Double = 0.0,
  val lon: Double = 0.0,
  val arrival: String? = null,
  val departure: String? = null,
  val scheduledArrival: String? = null,
  val scheduledDeparture: String? = null,
  /** Quai courant, mis à jour en temps réel quand le serveur le sait. */
  val track: String? = null,
  /** Quai de la base horaire, repli quand le temps réel n'en donne pas. */
  val scheduledTrack: String? = null,
  val cancelled: Boolean = false,
  /** `modes` du schéma `Place` : les modes desservis, rendus sur l'arrêt interrogé. */
  val modes: List<String> = emptyList(),
  /** Perturbations de l'arrêt et de la course, rendues quand `withAlerts=true`. */
  val alerts: List<PlanAlertDto> = emptyList(),
)
