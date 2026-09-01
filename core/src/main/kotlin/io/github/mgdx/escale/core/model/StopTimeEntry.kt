package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Un départ (ou une arrivée) à un arrêt, décalque du schéma `StopTime` de l'OpenAPI MOTIS.
 * Alimente l'écran « prochains départs » (SPEC.md § 5.4).
 */
data class StopTimeEntry(
  /** Identifiant de la course, à passer à `/api/v6/trip` pour la desserte complète. */
  val tripId: String,
  val mode: TransitMode,
  /**
   * Libellé de la ligne tel qu'il doit être affiché (`displayName`), déjà arbitré par le serveur
   * entre `routeShortName` et `routeLongName`.
   */
  val lineName: String,
  /** Girouette du véhicule (`headsign`), c'est-à-dire la direction annoncée à l'usager. */
  val headsign: String,
  /** Transporteur. */
  val agencyName: String,
  /** Quai ou voie. Nul quand ni la base horaire ni le temps réel ne le donnent. */
  val track: String?,
  val scheduledTime: Instant,
  /** Heure effective, égale à [scheduledTime] quand [realTime] est faux. */
  val time: Instant,
  /** Vrai si une donnée temps réel existe : sans elle, aucun retard ne doit être affiché. */
  val realTime: Boolean = false,
  /** Ce passage précis est supprimé (arrêt sauté ou course annulée). */
  val cancelled: Boolean = false,
  /** La course entière est annulée, pas seulement ce passage. */
  val tripCancelled: Boolean = false,
  /** Couleur de la ligne, au format `#RRGGBB`. Nulle quand le transporteur n'en publie pas. */
  val routeColor: String? = null,
  /** Couleur du texte à poser sur [routeColor], au format `#RRGGBB`. */
  val routeTextColor: String? = null,
  val alerts: List<Disruption> = emptyList(),
)
