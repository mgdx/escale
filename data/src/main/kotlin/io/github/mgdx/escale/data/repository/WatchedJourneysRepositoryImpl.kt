package io.github.mgdx.escale.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import io.github.mgdx.escale.core.model.JourneyWatchLimit
import io.github.mgdx.escale.core.model.WatchDecision
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.toEntity
import io.github.mgdx.escale.data.db.toWatchedJourney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Les trajets surveillés, persistés en Room (SPEC.md § 5.5.1).
 *
 * **Ce dépôt ne planifie rien** : ni `WorkManager`, ni notification, ni permission. Il enregistre
 * ce que le lot suivant lira — l'heure, les jours, et l'`id` d'itinéraire à rejouer.
 *
 * La limite de cinq n'est pas décidée ici : elle est dans `JourneyWatchLimit`, en Kotlin pur et
 * couverte en JVM. Ce dépôt se contente de l'appliquer **dans la transaction d'écriture**, pour
 * que deux activations simultanées ne puissent pas passer à six.
 */
class WatchedJourneysRepositoryImpl(
  private val database: EscaleDatabase,
  private val clock: () -> Instant = Instant::now,
) : WatchedJourneysRepository {

  private val dao = database.watchedJourneysDao()

  override val watched: Flow<List<WatchedJourney>> =
    dao.observeWatched().map { rows -> rows.map { it.toWatchedJourney() } }

  override suspend fun watch(journeyId: Long, schedule: WatchSchedule): Outcome<WatchDecision> = write {
    database.withTransaction {
      val decision = JourneyWatchLimit.decide(dao.watchedIds(), journeyId, schedule)
      if (decision == WatchDecision.ACCEPTED) {
        // Une reconfiguration conserve l'`id` d'itinéraire et la date de dernière consultation :
        // ce sont des faits observés, que changer l'heure de départ ne rend pas faux.
        val previous = dao.find(journeyId)?.toWatchedJourney()
        dao.upsert(
          WatchedJourney(
            journeyId = journeyId,
            schedule = schedule,
            itineraryId = previous?.itineraryId,
            itineraryCapturedAt = previous?.itineraryCapturedAt,
            lastViewedAt = previous?.lastViewedAt,
            createdAt = previous?.createdAt ?: clock(),
          ).toEntity(),
        )
      }
      decision
    }
  }

  override suspend fun unwatch(journeyId: Long): Outcome<Unit> = write { dao.delete(journeyId) }

  override suspend fun rememberItinerary(journeyId: Long, itineraryId: String?, capturedAt: Instant): Outcome<Unit> =
    write { dao.updateItinerary(journeyId, itineraryId, capturedAt.toEpochMilli()) }

  override suspend fun markViewed(journeyId: Long, viewedAt: Instant): Outcome<Unit> = write {
    dao.updateLastViewed(journeyId, viewedAt.toEpochMilli())
  }

  /** Voir `FavoritesRepositoryImpl` : le nom de l'exception, et rien de son contexte. */
  private suspend fun <T> write(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
  } catch (failure: SQLException) {
    Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
  }
}
