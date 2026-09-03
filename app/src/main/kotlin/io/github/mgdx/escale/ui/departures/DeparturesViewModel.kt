package io.github.mgdx.escale.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.DepartureFeed
import io.github.mgdx.escale.core.model.DepartureFilters
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.query.RealtimeRefreshPolicy
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Les prochains départs à un arrêt (SPEC.md § 5.4).
 *
 * **Ce qui se joue ici, et nulle part ailleurs :**
 *
 * - **Le filtre par mode ne part jamais en parapluie.** `DepartureModeFilter` sépare les modes
 *   envoyés au serveur, qui ne sont que des feuilles, de ceux comparés à une réponse, qui
 *   acceptent le parapluie (docs/architecture.md § 11.5). Ce `ViewModel` ne fait que transmettre
 *   `filter.requestModes` : il ne recompose aucun ensemble de son côté, faute de quoi la règle
 *   existerait à deux endroits et divergerait.
 * - **Les puces offertes viennent des modes que le serveur annonce**, jamais d'une liste écrite à
 *   la main. Elles sont calculées une fois, sur une liste non filtrée, et ne rétrécissent plus :
 *   filtrer sur le bus ne doit pas faire disparaître la puce « train » avec laquelle on l'a
 *   choisi.
 * - **La desserte de l'arrêt se demande une fois, les horaires à chaque geste.** Le nom et les
 *   modes de l'arrêt arrivent déjà avec les départs ; seules les lignes desservies demandent
 *   `/api/v6/stop`, et elles ne changent pas d'une minute à l'autre. Un rafraîchissement du temps
 *   réel ne les redemande donc jamais (SPEC.md § 7).
 * - **La pagination étend la liste, elle ne la remplace pas** (SPEC.md § 5.4, « boutons plus tôt /
 *   plus tard »). Le recollement, les doublons et l'ordre reviennent à `DepartureFeed`, dans
 *   `:core`. La requête est renvoyée telle quelle, seul le curseur change.
 *
 * Aucun polling (SPEC.md § 7.4) : les seuls déclencheurs sont l'ouverture de l'écran, le geste de
 * l'usager, un changement de filtre, et le retour au premier plan sur des horaires de plus de 60
 * secondes — la même règle que la feuille de résultats et l'écran de détail, tenue par le même
 * `RealtimeRefreshPolicy`. Ni minuterie, ni boucle, ni tâche de fond.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * un identifiant d'arrêt dit où se trouve l'usager (SPEC.md § 8 et § 11).
 */
class DeparturesViewModel(
  private val stopId: String,
  stopName: String,
  private val tripRepository: TripRepository,
  private val stopsRepository: StopsRepository,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(DeparturesUiState(stopName = stopName))

  val uiState: StateFlow<DeparturesUiState> = state.asStateFlow()

  /** Au plus une requête en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  init {
    load()
    loadStop()
  }

  /** Le bouton « Actualiser » de la barre et le bouton « Réessayer » du bandeau d'erreur (§ 8). */
  fun onRefresh() = load()

  /**
   * Le retour au premier plan (SPEC.md § 7.4).
   *
   * Même règle et même juge que partout ailleurs : `RealtimeRefreshPolicy`, dans `:core`, qui porte
   * le seuil de 60 secondes et ses cas limites. Un écran encore en train de charger n'en redemande
   * pas.
   */
  fun onForeground() {
    if (state.value.loading) return
    if (RealtimeRefreshPolicy.shouldRefreshOnForeground(state.value.loadedAt, now())) load()
  }

  /**
   * Une puce de filtre, ou `null` pour « tous les modes ».
   *
   * Le filtrage est **serveur** : redemander la liste est le seul moyen d'obtenir vingt départs du
   * mode choisi. La filtrer côté client rendrait deux tramways là où l'usager en attend vingt.
   */
  fun onFilterSelected(filter: DepartureModeFilter?) {
    if (state.value.filter == filter) return
    state.update { it.copy(filter = filter) }
    load()
  }

  /** « Plus tôt » et « Plus tard » (SPEC.md § 5.4). */
  fun onPage(page: DeparturesPage) {
    val cursor = state.value.cursor(page) ?: return
    if (state.value.paging != null || state.value.loading) return
    work?.cancel()
    work = viewModelScope.launch {
      state.update { it.copy(paging = page, error = null) }
      when (val outcome = request(cursor)) {
        is Outcome.Success -> onPaged(page, outcome.value)
        is Outcome.Failure -> onFailure(outcome.error)
      }
    }
  }

  /**
   * (Re)charge la première page.
   *
   * La liste affichée n'est pas vidée : un rafraîchissement qui échoue doit laisser les horaires
   * précédents à l'écran plutôt qu'une page blanche (SPEC.md § 8).
   */
  private fun load() {
    work?.cancel()
    work = viewModelScope.launch {
      val first = state.value.feed == null
      state.update { it.copy(loading = first, refreshing = !first, paging = null, error = null) }
      when (val outcome = request(cursor = null)) {
        is Outcome.Success -> onLoaded(outcome.value)
        is Outcome.Failure -> onFailure(outcome.error)
      }
    }
  }

  /**
   * Les lignes desservant l'arrêt, pour l'en-tête (SPEC.md § 5.4).
   *
   * Un échec est **silencieux** : ce sont les départs qui font l'écran, et afficher un bandeau
   * d'erreur pour une liste de lignes absente ferait croire que les horaires ont échoué eux aussi.
   */
  private fun loadStop() {
    viewModelScope.launch {
      val outcome = stopsRepository.stop(stopId)
      if (outcome is Outcome.Success) {
        state.update { it.copy(lines = outcome.value.lines, stopName = outcome.value.name.ifBlank { it.stopName }) }
      }
    }
  }

  private suspend fun request(cursor: String?): Outcome<StopTimePage> = tripRepository.departures(
    stopId = stopId,
    time = now(),
    modes = state.value.filter?.requestModes.orEmpty(),
    cursor = cursor,
  )

  private fun onLoaded(page: StopTimePage) {
    state.update { current ->
      current.copy(
        stopName = page.stop?.name?.takeIf(String::isNotBlank) ?: current.stopName,
        feed = DepartureFeed.of(page),
        filters = current.filtersFor(page),
        loading = false,
        refreshing = false,
        paging = null,
        error = null,
        loadedAt = now(),
      )
    }
  }

  private fun onPaged(page: DeparturesPage, received: StopTimePage) {
    state.update { current ->
      val feed = current.feed ?: DepartureFeed()
      current.copy(
        feed = when (page) {
          DeparturesPage.EARLIER -> feed.earlier(received)
          DeparturesPage.LATER -> feed.later(received)
        },
        paging = null,
        error = null,
      )
    }
  }

  private fun onFailure(error: EscaleError) {
    // Une requête supplantée n'est jamais montrée : un résultat plus récent arrive derrière
    // (docs/architecture.md § 6).
    if (error == EscaleError.Superseded) return
    state.update { it.copy(loading = false, refreshing = false, paging = null, error = error) }
  }

  /**
   * Les puces à offrir, une fois pour toutes.
   *
   * Elles se déduisent des modes que le serveur annonce sur l'arrêt, complétés par ceux des
   * départs reçus — un arrêt dont le `place` ne porterait pas de `modes` garde ainsi ses puces.
   * **Elles ne sont calculées que sur une liste non filtrée** : une fois « bus » choisi, la réponse
   * ne contient plus que des bus, et recalculer ferait disparaître la puce qui a servi à filtrer.
   */
  private fun DeparturesUiState.filtersFor(page: StopTimePage): List<DepartureModeFilter> {
    if (filter != null) return filters
    val modes: List<TransitMode> = page.stop?.modes.orEmpty() + page.entries.map { it.mode }
    return DepartureFilters.available(modes)
  }

  companion object {
    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        // L'arrêt vient de la route typée : c'est un point public du réseau, pas une donnée de
        // l'usager, et `DeparturesRoute` dit pourquoi il a le droit d'y figurer.
        val route = createSavedStateHandle().toRoute<DeparturesRoute>()
        DeparturesViewModel(
          stopId = route.stopId,
          stopName = route.stopName,
          tripRepository = container.tripRepository,
          stopsRepository = container.stopsRepository,
        )
      }
    }
  }
}
