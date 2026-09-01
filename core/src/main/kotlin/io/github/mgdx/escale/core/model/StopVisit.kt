package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Passage à un arrêt intermédiaire d'une portion en transport en commun (SPEC.md § 5.3).
 *
 * [arrival] et [departure] peuvent être nuls au terminus d'origine et au terminus final, où l'API
 * ne renseigne qu'une des deux heures.
 */
data class StopVisit(
  val place: Place,
  val arrival: Instant?,
  val departure: Instant?,
  /** Arrêt supprimé de la desserte par une mise à jour temps réel. */
  val cancelled: Boolean = false,
)
