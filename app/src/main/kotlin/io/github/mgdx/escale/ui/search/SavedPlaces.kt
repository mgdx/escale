package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Les deux lieux nommés que SPEC.md § 5.5 distingue de tous les autres favoris. */
enum class SavedPlaceKind {
  HOME,
  WORK,
}

/**
 * Une des dernières recherches, telle qu'une puce d'accès rapide la rejoue (SPEC.md § 5.5).
 *
 * [time] en fait partie : SPEC.md § 5.1 veut que la puce **relance la recherche**, et rejouer
 * « Bastille → Gare de Lyon » sans son « arriver avant 9 h 00 » en lancerait une autre.
 */
data class RecentSearch(val id: Long, val from: Location, val to: Location, val time: TimeChoice)

/**
 * Domicile et travail, pour l'entrée en tête de liste et la puce d'accès rapide (SPEC.md § 5.1).
 *
 * L'interface reste posée entre l'écran et le dépôt : elle permet aux tests et aux aperçus de
 * composer la carte de recherche sans base de données, avec [EmptySavedPlacesSource].
 *
 * SPEC.md § 5.5 est formel sur un point : **l'application ne réclame jamais d'elle-même** de
 * renseigner le domicile ou le travail. Un lieu absent ne produit ni entrée, ni puce, ni invite
 * (SPEC.md § 5.1 : « une puce absente n'est simplement pas affichée »).
 */
interface SavedPlacesSource {
  val home: Flow<Location?>
  val work: Flow<Location?>

  /**
   * Supprime le lieu enregistré, sur appui long sur sa puce (SPEC.md § 5.5).
   *
   * La suppression est portée par la source plutôt que par un second dépôt injecté dans
   * [SearchViewModel] : les deux lieux nommés forment un tout — les lire et les effacer relèvent du
   * même contrat — et l'écran n'a besoin de rien d'autre des favoris.
   */
  suspend fun clear(kind: SavedPlaceKind): Outcome<Unit>
}

/**
 * Les dernières recherches : celles qu'on affiche en puces, et celle qu'on vient de lancer
 * (SPEC.md § 5.1 et § 5.5).
 *
 * Lire et écrire sont ici le même contrat, comme pour [SavedPlacesSource] : c'est ce qui permet à
 * [SearchViewModel] de n'avoir qu'une dépendance pour l'historique, et aux aperçus de composer la
 * carte de recherche sans base de données.
 */
interface RecentSearchesSource {
  val recentSearches: Flow<List<RecentSearch>>

  /**
   * Enregistre la recherche qui part.
   *
   * La bascule « conserver les recherches récentes » n'est **pas** relue ici : `HistoryRepository`
   * la respecte déjà, et la vérifier deux fois serait une règle à maintenir à deux endroits.
   */
  suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit>
}

/** Domicile et travail tels que les favoris les portent (SPEC.md § 5.5). */
class FavoritesSavedPlacesSource(private val favorites: FavoritesRepository) : SavedPlacesSource {
  override val home: Flow<Location?> = favorites.home
  override val work: Flow<Location?> = favorites.work

  /** Un lieu supprimé redevient « non renseigné », c'est-à-dire l'absence de ligne. */
  override suspend fun clear(kind: SavedPlaceKind): Outcome<Unit> = when (kind) {
    SavedPlaceKind.HOME -> favorites.setHome(null)
    SavedPlaceKind.WORK -> favorites.setWork(null)
  }
}

/**
 * Les dernières recherches telles que l'historique les porte (SPEC.md § 5.5).
 *
 * Le plafond de cinquante est tenu par le stockage, et le nombre de puces affichées par
 * `quickChips` : il n'y a rien à couper ici.
 */
class HistoryRecentSearchesSource(private val history: HistoryRepository) : RecentSearchesSource {
  override val recentSearches: Flow<List<RecentSearch>> = history.recentSearches.map { entries ->
    entries.map { entry -> RecentSearch(id = entry.id, from = entry.from, to = entry.to, time = entry.time) }
  }

  override suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit> =
    history.record(from, to, time)
}

/** Source vide, pour les aperçus et les tests. Aucun domicile, aucun travail, donc aucune puce. */
object EmptySavedPlacesSource : SavedPlacesSource {
  override val home: Flow<Location?> = flowOf(null)
  override val work: Flow<Location?> = flowOf(null)

  override suspend fun clear(kind: SavedPlaceKind): Outcome<Unit> = Outcome.Success(Unit)
}

/** Source vide, pour les aperçus et les tests. Aucune recherche passée, donc aucune puce. */
object EmptyRecentSearchesSource : RecentSearchesSource {
  override val recentSearches: Flow<List<RecentSearch>> = flowOf(emptyList())

  override suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit> = Outcome.Success(Unit)
}
