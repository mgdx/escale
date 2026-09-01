package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Un trajet mis en favori : un couple départ / arrivée, éventuellement avec des préférences.
 *
 * Le jalon des trajets surveillés (SPEC.md § 5.5.1) viendra y accrocher sa configuration de
 * surveillance ; ce type ne la porte pas encore.
 */
data class FavoriteJourney(
  val id: Long,
  /** Nom donné par l'usager, ou nul pour laisser l'interface composer « Départ → Arrivée ». */
  val label: String?,
  val from: Location,
  val to: Location,
  val category: JourneyCategory = JourneyCategory.TRANSIT,
  val createdAt: Instant,
)

/** Une recherche passée, conservée localement (SPEC.md § 5.5, 50 entrées au plus). */
data class SearchHistoryEntry(val id: Long, val from: Location, val to: Location, val searchedAt: Instant)
