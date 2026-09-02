package io.github.mgdx.escale.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * L'écran de détail d'un trajet (SPEC.md § 5.3).
 *
 * **Ce qui se joue ici, et nulle part ailleurs :**
 *
 * - **La requête détaillée de SPEC.md § 7.6.** La liste de résultats demande `detailedLegs=false`
 *   pour alléger la réponse : le trajet reçu n'a donc ni arrêts intermédiaires, ni instructions
 *   pas-à-pas, ni géométrie. Ils sont demandés **à l'ouverture de cet écran, et seulement là**,
 *   avec `detailedLegs=true` et `detailedTransfers=true` — c'est `PlanQueryBuilder` qui pose les
 *   deux paramètres, ce `ViewModel` ne fait que réclamer la variante détaillée.
 * - **Le repli obligatoire de SPEC.md § 5.5.1.** Le chemin le moins coûteux est
 *   `/api/v6/refresh-itinerary`, qui reconstruit le trajet à partir de son seul identifiant et
 *   rapporte au passage le temps réel du moment. Mais cet identifiant est marqué « expérimental »
 *   côté MOTIS et devient invalide après une mise à jour d'horaires : sur un refus (400/422) ou un
 *   404, la requête `plan` d'origine est rejouée et le trajet le plus proche en heure de départ
 *   est retenu. La règle est dans `JourneyRefresh`, testée en JVM.
 * - **Le même chemin sert au bouton « Rafraîchir »** de SPEC.md § 5.3 : rafraîchir, c'est
 *   redemander le trajet détaillé. Il n'y a donc qu'un seul code réseau à lire et à vérifier.
 *
 * Aucun polling (SPEC.md § 7.4) : les deux seuls déclencheurs sont l'ouverture de l'écran et
 * l'appui de l'usager. Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et
 * **ne journalise rien** : il manipule des adresses et des coordonnées (SPEC.md § 11).
 */
class DetailViewModel(
  private val selection: SelectedJourneyStore,
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val savedState: SavedStateHandle,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(initialState())

  val uiState: StateFlow<DetailUiState> = state.asStateFlow()

  /** Au plus une requête détaillée en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  init {
    if (!state.value.closed) load()
  }

  /** Le bouton « Rafraîchir » (SPEC.md § 5.3) et le bouton « Réessayer » du bandeau (§ 8). */
  fun onRefresh() = load()

  fun onLegToggled(index: Int) {
    state.update { it.copy(expandedLegs = it.expandedLegs.toggled(index)) }
    remember(KEY_LEGS, state.value.expandedLegs)
  }

  fun onStopsToggled(index: Int) {
    state.update { it.copy(expandedStops = it.expandedStops.toggled(index)) }
    remember(KEY_STOPS, state.value.expandedStops)
  }

  fun onStepsToggled(index: Int) {
    state.update { it.copy(expandedSteps = it.expandedSteps.toggled(index)) }
    remember(KEY_STEPS, state.value.expandedSteps)
  }

  private fun load() {
    val current = state.value.journey ?: return
    work?.cancel()
    work = viewModelScope.launch {
      state.update { it.copy(loading = true, error = null) }
      when (val outcome = detailed(current)) {
        is Outcome.Success -> onDetailed(outcome.value)
        is Outcome.Failure -> onFailure(outcome.error)
      }
    }
  }

  /**
   * Le trajet détaillé, par le chemin le moins coûteux d'abord.
   *
   * Un trajet sans identifiant — le serveur ne le garantit pas — passe directement par le repli :
   * il n'y a rien à rafraîchir.
   */
  private suspend fun detailed(current: Journey): Outcome<Journey> {
    val itineraryId = current.id
    if (itineraryId != null) {
      val outcome = planRepository.refresh(itineraryId, detailedLegs = true)
      if (outcome is Outcome.Success) return outcome
      val error = (outcome as Outcome.Failure).error
      if (!JourneyRefresh.invalidatesItineraryId(error)) return outcome
    }
    return replanned(current)
  }

  /**
   * Le repli de SPEC.md § 5.5.1 : rejouer la requête `plan` d'origine, en détaillé cette fois, et
   * retenir le trajet le plus proche en heure de départ.
   *
   * La requête est reconstruite depuis `SearchSession`, qui détient toujours la recherche en cours,
   * et depuis la catégorie que `JourneyRefresh` relit dans les portions du trajet : un rabattement
   * à vélo vers une gare doit être rejoué sur l'onglet Transport, pas sur l'onglet Vélo.
   */
  private suspend fun replanned(current: Journey): Outcome<Journey> {
    val query = session.toQuery(JourneyRefresh.categoryOf(current), PREFERENCES)
      ?: return Outcome.Failure(EscaleError.Unknown(cause = NO_SEARCH))
    return when (val outcome = planRepository.plan(query, cursor = null, detailedLegs = true)) {
      is Outcome.Failure -> outcome

      is Outcome.Success -> {
        val page = outcome.value
        val closest = JourneyRefresh.closestToDeparture(page.journeys + page.direct, current.startTime)
        if (closest == null) Outcome.Failure(EscaleError.Unknown(cause = NO_MATCH)) else Outcome.Success(closest)
      }
    }
  }

  private fun onDetailed(journey: Journey) {
    val previous = state.value.journey
    // Le repli peut rendre un trajet recomposé : les positions de portions ne désignent alors plus
    // les mêmes portions, et un dépliage restitué au mauvais endroit serait pire que pas de
    // dépliage du tout.
    val sameShape = previous != null && previous.legs.size == journey.legs.size
    state.update {
      it.copy(
        journey = journey,
        detailed = true,
        loading = false,
        error = null,
        refreshedAt = now(),
        expandedLegs = if (sameShape) it.expandedLegs else emptySet(),
        expandedStops = if (sameShape) it.expandedStops else emptySet(),
        expandedSteps = if (sameShape) it.expandedSteps else emptySet(),
      )
    }
    // Le trajet détaillé porte la géométrie des portions, que le trajet sommaire n'avait pas : le
    // publier ici permet au lot « tracé » de dessiner le vrai tracé sans rien demander à cet écran.
    selection.select(journey)
  }

  private fun onFailure(error: EscaleError) {
    // Une requête supplantée n'est jamais montrée : un résultat plus récent arrive derrière
    // (docs/architecture.md § 6).
    if (error == EscaleError.Superseded) return
    state.update { it.copy(loading = false, error = error) }
  }

  private fun initialState(): DetailUiState {
    val chosen = selection.selected.value ?: return DetailUiState(closed = true)
    return DetailUiState(
      journey = chosen,
      expandedLegs = restored(KEY_LEGS),
      expandedStops = restored(KEY_STOPS),
      expandedSteps = restored(KEY_STEPS),
    )
  }

  private fun restored(key: String): Set<Int> = savedState.get<IntArray>(key)?.toSet().orEmpty()

  private fun remember(key: String, indexes: Set<Int>) {
    savedState[key] = indexes.toIntArray()
  }

  private fun Set<Int>.toggled(index: Int): Set<Int> = if (contains(index)) minus(index) else plus(index)

  companion object {
    /**
     * Les positions dépliées survivent à la rotation **comme à la mort du processus**. Ce sont des
     * numéros de portion : ils ne disent rien du trajet, et peuvent donc aller sur le disque, là
     * où le trajet lui-même ne le peut pas (SPEC.md § 11).
     */
    private const val KEY_LEGS = "detail.legs"
    private const val KEY_STOPS = "detail.stops"
    private const val KEY_STEPS = "detail.steps"

    /** Aucune recherche en cours : le repli sur `plan` est impossible, il n'y a pas de requête. */
    private const val NO_SEARCH = "NoSearchInProgress"

    /** Le serveur a répondu, mais sans aucun trajet où retrouver celui-ci. */
    private const val NO_MATCH = "NoMatchingItinerary"

    /**
     * Comme `ResultsViewModel` : les réglages de recherche de SPEC.md § 5.6 n'ont pas encore de
     * dépôt. Les valeurs par défaut du domaine sont celles du serveur, si bien que la requête
     * rejouée est exactement celle qu'a servie la liste de résultats.
     */
    private val PREFERENCES = SearchPreferences()

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        DetailViewModel(
          selection = SelectedJourneyStore.shared,
          session = container.searchSession,
          planRepository = container.planRepository,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
