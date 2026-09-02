package io.github.mgdx.escale.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyFeed
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.session.SearchDraft
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La feuille de résultats (SPEC.md § 5.2).
 *
 * Trois règles de sobriété réseau se jouent ici, et nulle part ailleurs dans l'interface :
 *
 * - **une requête par onglet, à l'ouverture de l'onglet** (§ 5.2, § 7.3). L'onglet consulté est
 *   chargé, les trois autres ne le sont pas. [onCategorySelected] est le seul chemin qui déclenche
 *   une requête en dehors du départ d'une recherche, et il ne le fait que pour l'onglet demandé ;
 * - **un onglet déjà chargé ne recharge pas** (§ 7.5). Le cache mémoire de `PlanRepository` rendrait
 *   la réponse sans réseau, mais on ne va même pas jusque-là : un onglet qui a déjà sa liste est
 *   affiché tel quel ;
 * - **une nouvelle recherche annule ce qui court** (§ 7.2). Les travaux en cours sont annulés, les
 *   quatre onglets repartent de zéro, et seul l'onglet consulté est relancé.
 *
 * `EscaleError.Superseded` n'est jamais montré : il signifie qu'un résultat plus récent est en
 * route, et afficher « une erreur est survenue » à quelqu'un qui vient de relancer sa recherche
 * serait un défaut visible (docs/architecture.md § 6).
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * il manipule des adresses et des coordonnées, que SPEC.md § 11 interdit d'écrire dans une trace,
 * y compris en débogage.
 */
class ResultsViewModel(
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val selection: SelectedJourneyStore,
  private val savedState: SavedStateHandle,
) : ViewModel() {

  private val state = MutableStateFlow(ResultsUiState(category = restoredCategory()))

  val uiState: StateFlow<ResultsUiState> = state.asStateFlow()

  /** Un travail par onglet, au plus. C'est ce qui permet d'annuler sans toucher aux autres. */
  private val jobs = mutableMapOf<JourneyCategory, Job>()

  init {
    viewModelScope.launch {
      session.draft.collect(::onDraftChanged)
    }
  }

  /**
   * L'usager change d'onglet : c'est **ici**, et seulement ici, qu'un onglet est chargé pour la
   * première fois (SPEC.md § 5.2).
   *
   * Un onglet déjà chargé n'émet rien. Un onglet en erreur n'émet rien non plus : la reprise est
   * un geste explicite ([onRetry]), sans quoi un serveur en panne serait interrogé à chaque
   * aller-retour entre deux onglets.
   */
  fun onCategorySelected(category: JourneyCategory) {
    if (state.value.category == category) return
    savedState[KEY_CATEGORY] = category.name
    state.update { it.copy(category = category) }
    val tab = state.value.tabs[category]
    if (tab == null) load(category)
  }

  /** Le bouton « Réessayer » du bandeau d'erreur (SPEC.md § 8). */
  fun onRetry() {
    val category = state.value.category
    updateTab(category) { TabResults() }
    load(category)
  }

  /** « Plus tôt » : la requête d'origine, seul le curseur change (SPEC.md § 5.2). */
  fun onEarlier() = paginate(ResultsPage.EARLIER)

  /** « Plus tard » : idem, dans l'autre sens. */
  fun onLater() = paginate(ResultsPage.LATER)

  /**
   * L'usager choisit un trajet.
   *
   * L'écran de détail (SPEC.md § 5.3) relève du sprint suivant : pour l'instant, le geste désigne
   * le trajet que la carte cadrera, et le publie dans [SelectedJourneyStore].
   */
  fun onJourneySelected(journey: Journey) {
    state.update { it.copy(selectedKey = journey.stableKey()) }
    selection.select(journey)
  }

  fun onBikeFilterChanged(filter: BikeFilter) {
    state.update { it.copy(bikeFilter = filter) }
  }

  /**
   * Le brouillon de recherche a changé.
   *
   * Tant qu'il est incomplet, la feuille n'existe pas : la carte occupe tout l'écran, et aucune
   * requête n'est envoyée. Dès qu'il est complet, la recherche part — SPEC.md § 5.1 ne prévoit pas
   * de bouton « Rechercher » — mais **pour le seul onglet consulté**.
   */
  private fun onDraftChanged(draft: SearchDraft) {
    jobs.values.forEach(Job::cancel)
    jobs.clear()
    selection.select(null)
    val category = state.value.category
    state.value = ResultsUiState(open = draft.isComplete, category = category)
    if (draft.isComplete) load(category)
  }

  private fun paginate(page: ResultsPage) {
    val category = state.value.category
    val tab = state.value.tabs[category] ?: return
    if (tab.paging != null) return
    val cursor = when (page) {
      ResultsPage.EARLIER -> tab.feed?.previousPageCursor
      ResultsPage.LATER -> tab.feed?.nextPageCursor
    } ?: return
    load(category, cursor, page)
  }

  private fun load(category: JourneyCategory, cursor: String? = null, page: ResultsPage? = null) {
    val query = session.toQuery(category, preferences) ?: return
    jobs[category]?.cancel()
    jobs[category] = viewModelScope.launch {
      updateTab(category) { tab ->
        if (page == null) TabResults(loading = true) else tab.copy(paging = page, error = null)
      }
      when (val outcome = planRepository.plan(query, cursor)) {
        is Outcome.Success -> updateTab(category) { it.extendedWith(outcome.value, page) }
        is Outcome.Failure -> onFailure(category, outcome.error)
      }
    }
  }

  private fun onFailure(category: JourneyCategory, error: EscaleError) {
    // Une requête supplantée ne s'affiche jamais : le résultat plus récent arrive derrière, et
    // c'est lui qui remplacera l'état de chargement en cours (SPEC.md § 7.2).
    if (error == EscaleError.Superseded) return
    updateTab(category) { it.copy(loading = false, paging = null, error = error) }
  }

  /** Recolle une page à la liste déjà affichée, ou la remplace s'il s'agit d'une nouvelle recherche. */
  private fun TabResults.extendedWith(page: JourneyPage, direction: ResultsPage?): TabResults {
    val previous = feed
    val next = when {
      direction == null || previous == null -> JourneyFeed.of(page)
      direction == ResultsPage.EARLIER -> previous.earlier(page)
      else -> previous.later(page)
    }
    return TabResults(feed = next)
  }

  private fun updateTab(category: JourneyCategory, transform: (TabResults) -> TabResults) {
    state.update { current ->
      val tab = current.tabs[category] ?: TabResults()
      current.copy(tabs = current.tabs + (category to transform(tab)))
    }
  }

  private fun restoredCategory(): JourneyCategory {
    val saved = savedState.get<String>(KEY_CATEGORY)
    return JourneyCategory.entries.firstOrNull { it.name == saved } ?: JourneyCategory.TRANSIT
  }

  companion object {
    /**
     * L'onglet consulté, restitué après une rotation **comme après la mort du processus**.
     *
     * Les résultats, eux, ne sont délibérément pas sauvegardés : une liste de trajets porte
     * l'origine et la destination de l'usager, et `SavedStateHandle` finit sur le disque du
     * système. SPEC.md § 7.5 et § 11 veulent ces données en mémoire et nulle part ailleurs ;
     * elles se retrouvent en une requête à la reprise.
     */
    private const val KEY_CATEGORY = "results.category"

    /**
     * Les réglages de recherche de SPEC.md § 5.6 n'ont pas encore de dépôt : `AppContainer`
     * n'expose pas de `PreferencesRepository`. Les valeurs par défaut sont celles du serveur, si
     * bien qu'une requête assemblée avec elles est exactement celle qu'attend MOTIS. Le jour où le
     * dépôt existera, c'est ce champ, et lui seul, qu'il faudra remplacer par son flux.
     */
    private val preferences = SearchPreferences()

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        ResultsViewModel(
          session = container.searchSession,
          planRepository = container.planRepository,
          selection = SelectedJourneyStore.shared,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
