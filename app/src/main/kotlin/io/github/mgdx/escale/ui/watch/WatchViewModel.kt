package io.github.mgdx.escale.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoriteJourneyMatch
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.core.model.WatchDecision
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import io.github.mgdx.escale.work.WatchAlarms
import io.github.mgdx.escale.work.WatchNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * L'activation et la configuration d'une surveillance (SPEC.md § 5.5.1).
 *
 * **Ce `ViewModel` ne décide de rien qui soit vérifiable ailleurs** : la limite de cinq est
 * appliquée par `JourneyWatchLimit` et par la transaction d'écriture du dépôt, la prochaine
 * échéance est calculée par `WatchPlanning`, la reconnaissance du favori par
 * `FavoriteJourneyMatch`. Il enchaîne, il ne réimplémente pas.
 *
 * Il n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** : il manipule
 * un départ et une arrivée (SPEC.md § 11).
 */
class WatchViewModel(
  private val favorites: FavoritesRepository,
  private val watched: WatchedJourneysRepository,
  private val store: WatchSettingsStore,
  private val scheduler: WatchAlarms,
  private val notifications: WatchNotifier,
  private val selection: SelectedJourneyStore,
  private val session: SearchSession,
  private val zone: ZoneId = ZoneId.systemDefault(),
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(initialState())

  val uiState: StateFlow<WatchUiState> = state.asStateFlow()

  /** Vrai une fois les échéances remises en place, pour ne le faire qu'une fois par écran. */
  private var synchronised = false

  init {
    observeFavorite()
    observeLastCheck()
  }

  /**
   * L'écran de détail montre ce trajet : la règle des trente minutes s'en sert.
   *
   * SPEC.md § 5.5.1 : « la surveillance ne s'exécute pas si le trajet a déjà été consulté dans
   * l'application dans les 30 dernières minutes ». C'est ici, et nulle part ailleurs, que cette
   * consultation est datée.
   */
  fun onJourneyViewed() {
    val id = state.value.favoriteId ?: return
    viewModelScope.launch { watched.markViewed(id, now()) }
  }

  /**
   * La bascule « Me prévenir avant le départ ».
   *
   * L'activation passe par l'écran de configuration, qui porte l'avertissement de la spec ; c'est
   * donc surtout l'arrêt qui passe par ici. Arrêter une surveillance annule sa tâche, retire sa
   * notification et oublie son dernier résultat : rien ne doit rester derrière une bascule éteinte.
   */
  fun onWatchedChanged(enabled: Boolean) {
    val id = state.value.favoriteId ?: return
    viewModelScope.launch {
      if (enabled) {
        save(id)
      } else {
        watched.unwatch(id)
        scheduler.cancel(id)
        notifications.cancel(id)
        store.forget(id)
        state.update { it.copy(watched = false, limitReached = false, lastCheck = null) }
      }
    }
  }

  fun onTimeChanged(time: LocalTime) {
    state.update { it.copy(time = time, singleDate = nextDateFor(time)) }
    saveIfWatched()
  }

  fun onDayToggled(day: DayOfWeek) {
    state.update { current ->
      val days = if (day in current.days) current.days - day else current.days + day
      current.copy(days = days)
    }
    saveIfWatched()
  }

  fun onThresholdChanged(threshold: Duration) {
    update(state.value.settings.copy(delayThreshold = threshold))
  }

  fun onNotifyAlwaysChanged(enabled: Boolean) {
    update(state.value.settings.copy(notifyWhenNothingChanged = enabled))
  }

  /**
   * Réponse à la demande de permission de notification.
   *
   * Le refus **n'annule rien** : SPEC.md § 5.5.1 veut que « la surveillance soit proposée sans
   * notification, l'état étant alors visible à l'ouverture de l'application ».
   */
  fun onNotificationPermissionResult() {
    state.update { it.copy(notificationsAllowed = notifications.canNotify()) }
  }

  private fun update(settings: WatchAlertSettings) {
    state.update { it.copy(settings = settings) }
    viewModelScope.launch { store.update(settings) }
  }

  private fun saveIfWatched() {
    val id = state.value.favoriteId ?: return
    if (state.value.watched) viewModelScope.launch { save(id) }
  }

  /**
   * Enregistre la configuration, puis programme la prochaine occurrence.
   *
   * La limite de cinq n'est **pas** vérifiée ici : le dépôt l'applique dans sa transaction, et
   * l'interface se contente de traduire son refus. Deux activations simultanées ne peuvent donc pas
   * passer à six, ce qu'une vérification faite dans l'écran n'aurait pas garanti.
   */
  private suspend fun save(id: Long) {
    val schedule = state.value.schedule
    when (val outcome = watched.watch(id, schedule)) {
      is Outcome.Failure -> Unit

      is Outcome.Success -> when (outcome.value) {
        WatchDecision.ACCEPTED -> accept(id, schedule)

        WatchDecision.LIMIT_REACHED -> state.update { it.copy(limitReached = true) }

        // Impossible par construction : une configuration sans jour reçoit une date.
        WatchDecision.INVALID_SCHEDULE -> Unit
      }
    }
  }

  private suspend fun accept(id: Long, schedule: WatchSchedule) {
    // L'identifiant d'itinéraire du trajet affiché est retenu comme **optimisation** : il évitera
    // une requête `plan` complète à la première vérification, et le repli existe pour le jour où
    // le serveur le refusera (SPEC.md § 5.5.1).
    selection.selected.value?.id?.let { watched.rememberItinerary(id, it, now()) }
    scheduler.schedule(WatchedJourney(journeyId = id, schedule = schedule, createdAt = now()), after = now())
    state.update { it.copy(watched = true, limitReached = false, notificationsAllowed = notifications.canNotify()) }
  }

  /** Le favori correspondant au trajet affiché, et sa surveillance éventuelle. */
  private fun observeFavorite() {
    viewModelScope.launch {
      combine(favorites.journeys, watched.watched, store.settings, ::Triple).collect { (journeys, watches, settings) ->
        val favorite = match(journeys)
        val watch = favorite?.let { found -> watches.firstOrNull { it.journeyId == found.id } }
        // Après un redémarrage de l'appareil, androidx.work n'a rien reprogrammé faute de
        // RECEIVE_BOOT_COMPLETED (SPEC.md § 11) : l'application remet ses échéances en place la
        // première fois qu'un écran de surveillance s'ouvre.
        if (!synchronised) {
          synchronised = true
          scheduler.sync(watches)
        }
        state.update { current ->
          current.copy(
            favoriteId = favorite?.id,
            watched = watch != null,
            time = watch?.schedule?.departureTime ?: current.time,
            days = watch?.schedule?.days ?: current.days,
            singleDate = watch?.schedule?.date ?: current.singleDate,
            settings = settings,
            watchedCount = watches.size,
            notificationsAllowed = notifications.canNotify(),
          )
        }
      }
    }
  }

  /** Le résultat de la dernière vérification du favori affiché, réévalué quand il change. */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeLastCheck() {
    viewModelScope.launch {
      state.map { it.favoriteId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else store.lastCheck(id) }
        .collect { record -> state.update { it.copy(lastCheck = record) } }
    }
  }

  private fun match(journeys: List<FavoriteJourney>): FavoriteJourney? {
    val journey = selection.selected.value
    val category = journey?.let(JourneyRefresh::categoryOf) ?: JourneyCategory.TRANSIT
    val draft = session.draft.value
    return FavoriteJourneyMatch.find(journeys, draft.from, draft.to, category)
  }

  /**
   * L'heure de départ proposée : celle du trajet affiché, à la minute.
   *
   * Elle sert de référence à la comparaison qui décidera de notifier : la proposer d'après le
   * trajet lui-même évite qu'un écart de quelques minutes entre l'habitude annoncée et le départ
   * réel ne passe pour un retard.
   */
  private fun initialState(): WatchUiState {
    val departure = selection.selected.value?.startTime
    val time = departure?.atZone(zone)?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES)
      ?: DEFAULT_DEPARTURE_TIME
    return WatchUiState(time = time, singleDate = nextDateFor(time))
  }

  /** La date d'une surveillance sans récurrence : aujourd'hui si l'heure est à venir, sinon demain. */
  private fun nextDateFor(time: LocalTime): LocalDate {
    val here = now().atZone(zone)
    val today = here.toLocalDate()
    return if (time.isAfter(here.toLocalTime())) today else today.plusDays(1)
  }

  companion object {
    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        WatchViewModel(
          favorites = container.favoritesRepository,
          watched = container.watchedJourneysRepository,
          store = container.watchSettingsStore,
          scheduler = container.watchScheduler,
          notifications = container.watchNotifications,
          selection = container.selectedJourneyStore,
          session = container.searchSession,
        )
      }
    }
  }
}
