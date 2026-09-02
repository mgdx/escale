package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Les deux lieux nommés que SPEC.md § 5.5 distingue de tous les autres favoris. */
enum class SavedPlaceKind {
  HOME,
  WORK,
}

/** Une des dernières recherches, telle qu'une puce d'accès rapide la rejoue (SPEC.md § 5.5). */
data class RecentSearch(val from: Location, val to: Location)

/**
 * Domicile et travail, pour l'entrée en tête de liste et la puce d'accès rapide (SPEC.md § 5.1).
 *
 * **À BRANCHER AU JALON 10.** Les favoris sont du ressort de `FavoritesRepository`, qui n'existe
 * pas encore : cette interface est le point d'accroche, et [EmptySavedPlacesSource] la remplit de
 * vide en attendant. Le jalon 10 n'aura qu'à fournir une autre implémentation à la fabrique de
 * [SearchViewModel] ; l'écran, lui, ne change pas.
 *
 * SPEC.md § 5.5 est formel sur un point que l'implémentation vide respecte déjà : **l'application
 * ne réclame jamais d'elle-même** de renseigner le domicile ou le travail. Un lieu absent ne
 * produit ni entrée, ni puce, ni invite (SPEC.md § 5.1 : « une puce absente n'est simplement pas
 * affichée »).
 */
interface SavedPlacesSource {
  val home: Flow<Location?>
  val work: Flow<Location?>
}

/**
 * Les dernières recherches, pour les puces d'accès rapide (SPEC.md § 5.1 et § 5.5).
 *
 * **À BRANCHER AU JALON 10**, sur `HistoryRepository`. Voir [SavedPlacesSource].
 */
interface RecentSearchesSource {
  val recentSearches: Flow<List<RecentSearch>>
}

/** Source vide, en attendant le jalon 10. Aucun domicile, aucun travail, donc aucune puce. */
object EmptySavedPlacesSource : SavedPlacesSource {
  override val home: Flow<Location?> = flowOf(null)
  override val work: Flow<Location?> = flowOf(null)
}

/** Source vide, en attendant le jalon 10. Aucune recherche passée, donc aucune puce. */
object EmptyRecentSearchesSource : RecentSearchesSource {
  override val recentSearches: Flow<List<RecentSearch>> = flowOf(emptyList())
}
