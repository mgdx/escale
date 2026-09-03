package io.github.mgdx.escale.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Accès aux favoris : domicile, travail, lieux, arrêts et trajets (SPEC.md § 5.5). */
@Dao
internal interface FavoritesDao {

  /** Rend `null` quand l'emplacement n'est pas renseigné : c'est l'absence de ligne qui le dit. */
  @Query("SELECT * FROM named_locations WHERE slot = :slot")
  fun observeNamedLocation(slot: String): Flow<NamedLocationEntity?>

  @Upsert
  suspend fun upsertNamedLocation(entity: NamedLocationEntity)

  @Query("DELETE FROM named_locations WHERE slot = :slot")
  suspend fun deleteNamedLocation(slot: String)

  @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC, id DESC")
  fun observePlaces(): Flow<List<FavoritePlaceEntity>>

  @Insert
  suspend fun insertPlace(entity: FavoritePlaceEntity): Long

  /**
   * Supprime un lieu favori **par l'identifiant que l'insertion a rendu**.
   *
   * Le désigner par son nom et ses coordonnées, comme le faisait la première version, effaçait
   * d'un coup deux favoris homonymes au même point : le café et l'appartement au-dessus.
   */
  @Query("DELETE FROM favorite_places WHERE id = :id")
  suspend fun deletePlace(id: Long)

  @Query("SELECT * FROM favorite_stops ORDER BY createdAt DESC, stopId DESC")
  fun observeStops(): Flow<List<FavoriteStopEntity>>

  @Upsert
  suspend fun upsertStop(entity: FavoriteStopEntity)

  @Query("DELETE FROM favorite_stops WHERE stopId = :stopId")
  suspend fun deleteStop(stopId: String)

  @Query("SELECT * FROM favorite_journeys ORDER BY createdAt DESC, id DESC")
  fun observeJourneys(): Flow<List<FavoriteJourneyEntity>>

  @Insert
  suspend fun insertJourney(entity: FavoriteJourneyEntity): Long

  /** La clé étrangère de `watched_journeys` est en `CASCADE` : la surveillance part avec le favori. */
  @Query("DELETE FROM favorite_journeys WHERE id = :id")
  suspend fun deleteJourney(id: Long)
}
