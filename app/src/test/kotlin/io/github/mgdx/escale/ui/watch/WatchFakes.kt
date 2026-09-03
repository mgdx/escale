package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyWatchLimit
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.core.model.WatchDecision
import io.github.mgdx.escale.core.model.WatchNotice
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.work.WatchAlarms
import io.github.mgdx.escale.work.WatchNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Les favoris, réduits aux trajets : le reste du contrat n'est pas sollicité par cet écran. */
internal class FakeFavoritesRepository(initial: List<FavoriteJourney> = emptyList()) : FavoritesRepository {

  private val state = MutableStateFlow(initial)

  override val home: Flow<Location?> = MutableStateFlow(null)
  override val work: Flow<Location?> = MutableStateFlow(null)
  override val places: Flow<List<FavoritePlace>> = MutableStateFlow(emptyList())
  override val stops: Flow<List<Stop>> = MutableStateFlow(emptyList())
  override val journeys: Flow<List<FavoriteJourney>> = state.asStateFlow()

  override suspend fun setHome(location: Location?): Outcome<Unit> = Outcome.Success(Unit)
  override suspend fun setWork(location: Location?): Outcome<Unit> = Outcome.Success(Unit)
  override suspend fun addPlace(location: Location, label: String?): Outcome<Long> = Outcome.Success(0)
  override suspend fun removePlace(id: Long): Outcome<Unit> = Outcome.Success(Unit)
  override suspend fun addStop(stop: Stop): Outcome<Unit> = Outcome.Success(Unit)
  override suspend fun removeStop(stopId: String): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun addJourney(
    from: Location,
    to: Location,
    category: JourneyCategory,
    label: String?,
  ): Outcome<Long> = Outcome.Success(0)

  override suspend fun removeJourney(id: Long): Outcome<Unit> = Outcome.Success(Unit)
}

/**
 * Les surveillances persistées, avec la vraie limite de cinq.
 *
 * Elle est appliquée par `JourneyWatchLimit`, comme le fait le dépôt Room : un faux qui accepterait
 * tout ferait passer un test que l'application réelle échouerait.
 */
internal class FakeWatchedJourneysRepository(initial: List<WatchedJourney> = emptyList()) : WatchedJourneysRepository {

  private val state = MutableStateFlow(initial)

  var lastItineraryId: String? = null
  var viewedAt: Instant? = null

  override val watched: Flow<List<WatchedJourney>> = state.asStateFlow()

  override suspend fun watch(journeyId: Long, schedule: WatchSchedule): Outcome<WatchDecision> {
    val decision = JourneyWatchLimit.decide(state.value.map { it.journeyId }, journeyId, schedule)
    if (decision == WatchDecision.ACCEPTED) {
      state.update { current ->
        current.filterNot { it.journeyId == journeyId } +
          WatchedJourney(journeyId = journeyId, schedule = schedule, createdAt = Instant.EPOCH)
      }
    }
    return Outcome.Success(decision)
  }

  override suspend fun unwatch(journeyId: Long): Outcome<Unit> {
    state.update { current -> current.filterNot { it.journeyId == journeyId } }
    return Outcome.Success(Unit)
  }

  override suspend fun rememberItinerary(journeyId: Long, itineraryId: String?, capturedAt: Instant): Outcome<Unit> {
    lastItineraryId = itineraryId
    return Outcome.Success(Unit)
  }

  override suspend fun markViewed(journeyId: Long, viewedAt: Instant): Outcome<Unit> {
    this.viewedAt = viewedAt
    return Outcome.Success(Unit)
  }
}

/** Les réglages, en mémoire. */
internal class FakeWatchSettingsStore : WatchSettingsStore {

  private val state = MutableStateFlow(WatchAlertSettings())
  private val records = MutableStateFlow(emptyMap<Long, WatchCheckRecord>())

  override val settings: Flow<WatchAlertSettings> = state.asStateFlow()

  override suspend fun update(settings: WatchAlertSettings) {
    state.value = settings
  }

  override fun lastCheck(journeyId: Long): Flow<WatchCheckRecord?> = records.map { it[journeyId] }

  override suspend fun record(journeyId: Long, record: WatchCheckRecord) {
    records.update { it + (journeyId to record) }
  }

  override suspend fun forget(journeyId: Long) {
    records.update { it - journeyId }
  }
}

/** La programmation, réduite à ce qu'elle a reçu : c'est ce que les tests vérifient. */
internal class FakeWatchAlarms : WatchAlarms {

  val scheduled = mutableListOf<WatchedJourney>()
  val cancelled = mutableListOf<Long>()
  var synchronised: List<WatchedJourney>? = null

  override fun schedule(watch: WatchedJourney, after: Instant): Instant? {
    scheduled += watch
    return after
  }

  override fun cancel(journeyId: Long) {
    cancelled += journeyId
  }

  override fun sync(watched: List<WatchedJourney>) {
    synchronised = watched
  }
}

/** Les notifications, avec une permission qui peut être refusée. */
internal class FakeWatchNotifier(var allowed: Boolean = true) : WatchNotifier {

  val cancelled = mutableListOf<Long>()
  val emitted = mutableListOf<WatchNotice>()

  override fun canNotify(): Boolean = allowed

  override fun notify(journeyId: Long, notice: WatchNotice, use24Hour: Boolean): Boolean {
    if (!allowed) return false
    emitted += notice
    return true
  }

  override fun cancel(journeyId: Long) {
    cancelled += journeyId
  }
}
