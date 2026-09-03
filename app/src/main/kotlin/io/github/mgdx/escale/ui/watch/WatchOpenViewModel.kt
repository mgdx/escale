package io.github.mgdx.escale.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.JourneyWatchCheck
import io.github.mgdx.escale.core.model.WatchPlanning
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * L'état de l'ouverture d'un trajet depuis sa notification.
 *
 * [ready] porte le jeton de la demande servie : c'est un **événement**, que la navigation acquitte
 * avec [WatchOpenViewModel.onOpened]. L'observer comme un état ferait rouvrir l'écran de détail à
 * chaque rotation.
 */
data class WatchOpenUiState(val loading: Boolean = false, val error: EscaleError? = null, val ready: Long? = null)

/**
 * Ouvrir le détail du trajet rafraîchi depuis la notification (SPEC.md § 5.5.1).
 *
 * **Le trajet n'est pas transporté, il est redemandé.** La notification ne porte que l'identifiant
 * du favori ; le trajet lui-même est obtenu ici, par le même chemin que la vérification de fond —
 * `JourneyWatchCheck.locate`, donc `refresh-itinerary` puis le repli sur `plan` en 400/404. Trois
 * raisons, dans cet ordre :
 *
 * 1. **Rien de nouveau à persister** : l'identifiant d'itinéraire l'est déjà (`WatchedJourney`),
 *    et une photographie du trajet dans un fichier serait une donnée de déplacement de plus sur le
 *    disque, pour une valeur qui se périme en quelques minutes (SPEC.md § 11).
 * 2. **Rien à faire passer par un objet partagé mutable** : la tâche de fond ne publie rien dans
 *    `SelectedJourneyStore`, qui contient peut-être le trajet que l'usager est en train de
 *    regarder. Elle ne fait que notifier.
 * 3. **C'est plus juste** : la requête part au moment du geste de l'usager, au premier plan
 *    (SPEC.md § 7.7), et montre l'état du trajet maintenant, non celui d'il y a une heure.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien**.
 */
class WatchOpenViewModel(
  private val watched: WatchedJourneysRepository,
  private val favorites: FavoritesRepository,
  private val plans: PlanRepository,
  private val preferences: PreferencesRepository,
  private val requests: WatchOpenRequests,
  private val selection: SelectedJourneyStore,
  private val zone: ZoneId = ZoneId.systemDefault(),
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(WatchOpenUiState())

  val uiState: StateFlow<WatchOpenUiState> = state.asStateFlow()

  /** Au plus une ouverture en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  /** La demande servie en dernier, pour que « Réessayer » n'ait rien à redemander à l'appelant. */
  private var pending: WatchOpenRequest? = null

  init {
    viewModelScope.launch {
      requests.request.filterNotNull().collect { request ->
        pending = request
        load(request)
      }
    }
  }

  /** La navigation a eu lieu : la demande est acquittée, et ne sera pas rejouée à la rotation. */
  fun onOpened() {
    state.update { it.copy(ready = null) }
    requests.consume()
  }

  /** L'usager referme le message d'échec : on abandonne l'ouverture plutôt que d'insister. */
  fun onDismissError() {
    state.update { it.copy(error = null) }
    pending = null
    requests.consume()
  }

  /** Le bouton « Réessayer » du message d'échec (SPEC.md § 8). */
  fun onRetry() {
    val request = pending ?: return
    load(request)
  }

  private fun load(request: WatchOpenRequest) {
    work?.cancel()
    work = viewModelScope.launch {
      state.update { it.copy(loading = true, error = null) }
      val target = target(request.journeyId)
      if (target == null) {
        // La surveillance ou le favori a disparu depuis l'envoi de la notification : il n'y a plus
        // rien à ouvrir, et le dire serait parler d'un trajet que l'usager vient de supprimer.
        state.update { it.copy(loading = false) }
        requests.consume()
        return@launch
      }
      publish(request, target.first, target.second)
    }
  }

  private suspend fun publish(request: WatchOpenRequest, watch: WatchedJourney, favorite: FavoriteJourney) {
    val outcome = JourneyWatchCheck(plans, watched).locate(
      watch = watch,
      favorite = favorite,
      departure = departure(watch),
      preferences = preferences.searchPreferences.first(),
    )
    val journey = (outcome as? Outcome.Success)?.value
    if (journey == null) {
      // Échec réseau, ou plus aucun trajet à cette heure-là : SPEC.md § 8 impose de le dire, un
      // appui qui n'aboutit à rien étant le pire des deux maux.
      val error = (outcome as? Outcome.Failure)?.error ?: EscaleError.Unknown(cause = NO_MATCH)
      state.update { it.copy(loading = false, error = error) }
      return
    }
    // C'est le geste de l'usager qui publie le trajet, jamais la tâche de fond : le trajet mis en
    // évidence sur la carte n'est remplacé que parce qu'il vient de demander celui-ci.
    selection.select(journey)
    watched.markViewed(watch.journeyId, now())
    state.update { it.copy(loading = false, error = null, ready = request.token) }
  }

  /** La surveillance et son favori, ou `null` si l'un des deux n'existe plus. */
  private suspend fun target(journeyId: Long): Pair<WatchedJourney, FavoriteJourney>? {
    val watch = watched.watched.first().firstOrNull { it.journeyId == journeyId } ?: return null
    val favorite = favorites.journeys.first().firstOrNull { it.id == journeyId } ?: return null
    return watch to favorite
  }

  /**
   * L'occurrence dont il est question : le départ surveillé le plus proche.
   *
   * La référence est reculée d'une heure pour qu'un appui **après** l'heure de départ ouvre encore
   * le trajet qui a motivé l'alerte, et non celui du lendemain. Elle ne sert qu'au repli sur
   * `plan` ; le chemin normal, `refresh-itinerary`, n'a besoin que de l'identifiant.
   */
  private fun departure(watch: WatchedJourney): Instant {
    val reference = now().minus(WatchPlanning.LEAD_TIME)
    return WatchPlanning.nextDeparture(watch.schedule, after = reference, zone = zone) ?: now()
  }

  companion object {
    /** Le serveur a répondu, mais sans aucun trajet où retrouver celui-ci. */
    private const val NO_MATCH = "NoMatchingItinerary"

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        WatchOpenViewModel(
          watched = container.watchedJourneysRepository,
          favorites = container.favoritesRepository,
          plans = container.planRepository,
          preferences = container.preferencesRepository,
          requests = container.watchOpenRequests,
          selection = container.selectedJourneyStore,
        )
      }
    }
  }
}
