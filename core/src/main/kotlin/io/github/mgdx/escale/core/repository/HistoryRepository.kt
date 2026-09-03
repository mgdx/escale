package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.TimeChoice
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

  /**
   * Enregistre une recherche. Sans effet si l'historique est désactivé.
   *
   * Une recherche, au moment où elle part, c'est un départ, une arrivée et une heure — pas une
   * [io.github.mgdx.escale.core.model.SearchQuery]. Celle-ci porte en plus les préférences de
   * SPEC.md § 5.6, qui sont des réglages globaux : les figer dans une entrée d'historique ferait
   * rejouer, des semaines plus tard, une recherche avec des réglages que l'usager a changés depuis.
   * Elle porte aussi la catégorie, c'est-à-dire l'onglet de résultats, qui appartient à la feuille
   * de résultats et non à la recherche.
   */
  suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit>

  suspend fun delete(id: Long): Outcome<Unit>

  /** Bouton « Tout effacer » des réglages. */
  suspend fun clear(): Outcome<Unit>

  companion object {
    /** N = 50, valeur fixée par SPEC.md § 5.5. */
    const val MAX_ENTRIES = 50
  }
}
