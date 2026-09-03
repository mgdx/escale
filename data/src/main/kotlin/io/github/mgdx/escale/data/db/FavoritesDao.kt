package io.github.mgdx.escale.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Accès aux favoris : domicile, travail, lieux, arrêts et trajets (SPEC.md § 5.5). */
@Dao
internal abstract class FavoritesDao {

  /** Rend `null` quand l'emplacement n'est pas renseigné : c'est l'absence de ligne qui le dit. */
  @Query("SELECT * FROM named_locations WHERE slot = :slot")
  abstract fun observeNamedLocation(slot: String): Flow<NamedLocationEntity?>

  @Upsert
  abstract suspend fun upsertNamedLocation(entity: NamedLocationEntity)

  @Query("DELETE FROM named_locations WHERE slot = :slot")
  abstract suspend fun deleteNamedLocation(slot: String)

  @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC, id DESC")
  abstract fun observePlaces(): Flow<List<FavoritePlaceEntity>>

  @Insert
  abstract suspend fun insertPlace(entity: FavoritePlaceEntity): Long

  /**
   * Supprime un lieu favori **par l'identifiant que l'insertion a rendu**.
   *
   * Le désigner par son nom et ses coordonnées, comme le faisait la première version, effaçait
   * d'un coup deux favoris homonymes au même point : le café et l'appartement au-dessus.
   */
  @Query("DELETE FROM favorite_places WHERE id = :id")
  abstract suspend fun deletePlace(id: Long)

  @Query("SELECT * FROM favorite_stops ORDER BY createdAt DESC, stopId DESC")
  abstract fun observeStops(): Flow<List<FavoriteStopEntity>>

  @Upsert
  abstract suspend fun upsertStop(entity: FavoriteStopEntity)

  @Query("DELETE FROM favorite_stops WHERE stopId = :stopId")
  abstract suspend fun deleteStop(stopId: String)

  @Query("SELECT * FROM favorite_journeys ORDER BY createdAt DESC, id DESC")
  abstract fun observeJourneys(): Flow<List<FavoriteJourneyEntity>>

  @Insert
  abstract suspend fun insertJourney(entity: FavoriteJourneyEntity): Long

  /**
   * L'identifiant du favori qui désigne déjà ce trajet, ou `null`.
   *
   * Les colonnes comparées sont **exactement celles de l'index unique** : le nom et les
   * coordonnées des deux points, et la catégorie. Le `stopId` n'en fait pas partie — nul pour une
   * adresse, et deux `NULL` ne sont jamais égaux en SQL.
   */
  @Query(
    """
    SELECT id FROM favorite_journeys
    WHERE from_name = :fromName AND from_lat = :fromLat AND from_lon = :fromLon
      AND to_name = :toName AND to_lat = :toLat AND to_lon = :toLon
      AND category = :category
    LIMIT 1
    """,
  )
  abstract suspend fun findJourney(
    fromName: String,
    fromLat: Double,
    fromLon: Double,
    toName: String,
    toLat: Double,
    toLon: Double,
    category: String,
  ): Long?

  /**
   * Enregistre un trajet favori **au plus une fois**, et rend l'identifiant dans les deux cas.
   *
   * La recherche et l'insertion sont dans la même transaction : deux appuis très rapprochés sur
   * l'étoile ne peuvent pas passer tous les deux entre la question et la réponse. L'index unique
   * de l'entité reste la garantie de dernier ressort — celle qui vaut aussi pour un futur appelant
   * qui aurait oublié cette fonction —, mais c'est ici que le second appui obtient une réponse
   * utile plutôt qu'une erreur de contrainte.
   */
  @Transaction
  open suspend fun insertJourneyOnce(entity: FavoriteJourneyEntity): Long = findJourney(
    fromName = entity.from.name,
    fromLat = entity.from.lat,
    fromLon = entity.from.lon,
    toName = entity.to.name,
    toLat = entity.to.lat,
    toLon = entity.to.lon,
    category = entity.category,
  ) ?: insertJourney(entity)

  @Query("DELETE FROM favorite_journeys WHERE id = :id")
  abstract suspend fun deleteJourney(id: Long)
}
