package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Historique des recherches, local uniquement (SPEC.md § 5.5).
 *
 * L'historique est plafonné à [MAX_ENTRIES] entrées et peut être désactivé depuis les réglages :
 * dans ce cas [record] ne conserve rien.
 */
interface HistoryRepository {
  /** Les dernières recherches, la plus récente d'abord. */
  val recentSearches: Flow<List<SearchHistoryEntry>>

  /** Enregistre une recherche. Sans effet si l'historique est désactivé. */
  suspend fun record(query: SearchQuery): Outcome<Unit>

  suspend fun delete(id: Long): Outcome<Unit>

  /** Bouton « Tout effacer » des réglages. */
  suspend fun clear(): Outcome<Unit>

  companion object {
    /** N = 50, valeur fixée par SPEC.md § 5.5. */
    const val MAX_ENTRIES = 50
  }
}
