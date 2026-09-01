package io.github.mgdx.escale.core.model

/**
 * Arrêt affiché sur la carte ou enregistré en favori.
 *
 * Volontairement plus pauvre que [Place] : il ne porte aucune heure, puisqu'il ne s'inscrit dans
 * aucun trajet.
 */
data class Stop(
  val id: String,
  val name: String,
  val coordinates: LatLon,
  /** Modes desservis, tels que renvoyés par `/api/v6/map/stops`. */
  val modes: List<TransitMode> = emptyList(),
)
