package io.github.mgdx.escale.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Accès aux trajets surveillés (SPEC.md § 5.5.1). Il ne planifie rien : il persiste. */
@Dao
internal interface WatchedJourneysDao {

  @Query("SELECT * FROM watched_journeys ORDER BY createdAt ASC, journeyId ASC")
  fun observeWatched(): Flow<List<WatchedJourneyEntity>>

  /** Lu dans la transaction d'écriture pour appliquer la limite de cinq. */
  @Query("SELECT journeyId FROM watched_journeys")
  suspend fun watchedIds(): List<Long>

  /** La surveillance en place, pour ne pas perdre ce qu'une reconfiguration n'a pas à changer. */
  @Query("SELECT * FROM watched_journeys WHERE journeyId = :journeyId")
  suspend fun find(journeyId: Long): WatchedJourneyEntity?

  @Upsert
  suspend fun upsert(entity: WatchedJourneyEntity)

  @Query("DELETE FROM watched_journeys WHERE journeyId = :journeyId")
  suspend fun delete(journeyId: Long)

  @Query(
    "UPDATE watched_journeys SET itineraryId = :itineraryId, itineraryCapturedAt = :capturedAt " +
      "WHERE journeyId = :journeyId",
  )
  suspend fun updateItinerary(journeyId: Long, itineraryId: String?, capturedAt: Long)

  @Query("UPDATE watched_journeys SET lastViewedAt = :viewedAt WHERE journeyId = :journeyId")
  suspend fun updateLastViewed(journeyId: Long, viewedAt: Long)
}
