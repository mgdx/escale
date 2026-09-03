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
   * Supprime un lieu favori désigné par le lieu lui-même, faute d'identifiant local dans le
   * contrat de `FavoritesRepository` : par son `stopId` quand il en a un, par son nom et ses
   * coordonnées sinon. Les coordonnées comparées sont celles qui ont été enregistrées, à l'octet
   * près, puisqu'elles proviennent du même aller-retour.
   */
  @Query(
    """
    DELETE FROM favorite_places
    WHERE (:stopId IS NOT NULL AND stopId = :stopId)
       OR (:stopId IS NULL AND stopId IS NULL AND name = :name AND lat = :lat AND lon = :lon)
    """,
  )
  suspend fun deletePlace(stopId: String?, name: String, lat: Double, lon: Double)

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
