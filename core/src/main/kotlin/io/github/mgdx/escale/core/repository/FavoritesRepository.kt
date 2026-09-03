package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Domicile, travail, lieux, arrêts et trajets favoris (SPEC.md § 5.5).
 *
 * Domicile et travail sont facultatifs et ne sont jamais réclamés d'eux-mêmes : un `null` est un
 * état normal, pas une anomalie.
 */
interface FavoritesRepository {
  val home: Flow<Location?>

  val work: Flow<Location?>

  val places: Flow<List<FavoritePlace>>

  val stops: Flow<List<Stop>>

  val journeys: Flow<List<FavoriteJourney>>

  /** [location] nul supprime le domicile enregistré. */
  suspend fun setHome(location: Location?): Outcome<Unit>

  /** [location] nul supprime le lieu de travail enregistré. */
  suspend fun setWork(location: Location?): Outcome<Unit>

  /**
   * Enregistre un lieu nommé et **rend l'identifiant attribué**, comme [addJourney].
   *
   * Le libellé n'est pas décoratif : SPEC.md § 5.5 parle de « lieux nommés », et c'est
   * [FavoritePlace.label] qui le relit. Un libellé nul est un lieu enregistré sous son propre nom.
   */
  suspend fun addPlace(location: Location, label: String?): Outcome<Long>

  /**
   * Supprime un lieu favori **par son identifiant**, et non par son nom et ses coordonnées.
   *
   * Deux favoris peuvent désigner le même point sous deux noms — le café et l'appartement
   * au-dessus — ou porter le même nom à deux endroits. Seul l'identifiant les départage.
   */
  suspend fun removePlace(id: Long): Outcome<Unit>

  suspend fun addStop(stop: Stop): Outcome<Unit>

  suspend fun removeStop(stopId: String): Outcome<Unit>

  /** Rend l'identifiant attribué au trajet enregistré. */
  suspend fun addJourney(from: Location, to: Location, category: JourneyCategory, label: String?): Outcome<Long>

  suspend fun removeJourney(id: Long): Outcome<Unit>
}
