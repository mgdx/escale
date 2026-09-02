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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
 *   quatre onglets repartent de zéro, et seul l'onglet consulté est relancé. Un réglage de
 *   recherche modifié (§ 5.6) produit exactement le même effet : les trajets affichés ont été
 *   calculés avec les anciens réglages, ils sont périmés.
 *
 * `EscaleError.Superseded` n'est jamais montré : il signifie qu'un résultat plus récent est en
 * route, et afficher « une erreur est survenue » à quelqu'un qui vient de relancer sa recherche
 * serait un défaut visible (docs/architecture.md § 6).
 *
 * **La mise en évidence d'un trajet est un état, son ouverture est un événement.** SPEC.md § 5.1
 * veut que la carte « cadre le trajet sélectionné » : dès qu'une liste arrive, son premier trajet
 * est publié dans [SelectedJourneyStore] sans aucun appui, et la sélection suit ensuite la liste
 * affichée ([syncSelection]). L'appui, lui, ajoute une demande d'ouverture de l'écran de détail
 * sur [openDetail], à consommation unique.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * il manipule des adresses et des coordonnées, que SPEC.md § 11 interdit d'écrire dans une trace,
 * y compris en débogage.
 */
class ResultsViewModel(
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val searchPreferences: Flow<SearchPreferences>,
  private val selection: SelectedJourneyStore,
  private val savedState: SavedStateHandle,
) : ViewModel() {

  private val state = MutableStateFlow(ResultsUiState(category = restoredCategory()))

  val uiState: StateFlow<ResultsUiState> = state.asStateFlow()

  /**
   * **L'ouverture de l'écran de détail, et rien d'autre : un événement à consommation unique.**
   *
   * Ce n'est délibérément pas une valeur observable. Le trajet choisi, lui, en est une — il vit
   * dans [SelectedJourneyStore] et dure tant qu'il y a des résultats — et c'est justement pour ça
   * qu'il ne peut pas servir à naviguer : une `StateFlow` ne republie pas une valeur égale, si
   * bien qu'un second appui sur la même carte n'ouvrirait plus rien. Séparer les deux évite le
   * contournement qui remettait la sélection à `null` en quittant l'écran de détail, et avec lui
   * la disparition du tracé au retour en arrière (SPEC.md § 5.1).
   *
   * Le canal est conflaté : deux appuis très rapprochés n'empilent pas deux ouvertures, et un
   * événement émis pendant que la feuille n'est pas à l'écran ne s'accumule pas.
   */
  private val detailRequests = Channel<Unit>(Channel.CONFLATED)

  /** À collecter une seule fois, par la feuille de résultats, pour ouvrir l'écran de détail. */
  val openDetail: Flow<Unit> = detailRequests.receiveAsFlow()

  /** Un travail par onglet, au plus. C'est ce qui permet d'annuler sans toucher aux autres. */
  private val jobs = mutableMapOf<JourneyCategory, Job>()

  /** Les réglages de SPEC.md § 5.6, tels que le dépôt les a émis en dernier. */
  private var preferences = SearchPreferences()

  /** Faux tant que le dépôt n'a rien émis : aucune recherche ne part avant de les connaître. */
  private var preferencesKnown = false

  init {
    viewModelScope.launch {
      searchPreferences.collect(::onPreferencesChanged)
    }
  }

  /**
   * Les réglages de recherche ont changé (SPEC.md § 5.6).
   *
   * Deux moments à distinguer, et c'est tout l'objet de cette fonction :
   *
   * - **la première émission** n'est pas un changement. C'est seulement à ce moment qu'on sait avec
   *   quels réglages chercher, et c'est donc là qu'on commence à observer le brouillon de
   *   recherche. Observer les deux en parallèle ferait partir la première requête avec les valeurs
   *   par défaut, pour la relancer aussitôt : une requête pour rien, que SPEC.md § 7 proscrit ;
   * - **un changement réel** périme ce qui est affiché : ces trajets ont été calculés avec les
   *   anciens réglages. Les onglets repartent de zéro et **seul l'onglet consulté est rechargé**,
   *   les autres restant muets tant que l'usager ne les ouvre pas (SPEC.md § 7.3).
   *
   * Le cache de `PlanRepository` n'a rien à désactiver : sa clé contient la `SearchQuery`, donc les
   * préférences. Une préférence modifiée donne une clé différente, et la réponse précédente n'est
   * jamais rendue à sa place (SPEC.md § 7.5).
   */
  private fun onPreferencesChanged(updated: SearchPreferences) {
    val first = !preferencesKnown
    val changed = updated != preferences
    preferences = updated
    preferencesKnown = true
    when {
      first -> viewModelScope.launch { session.draft.collect(::onDraftChanged) }
      changed -> restart(open = state.value.open)
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
    if (tab == null) load(category) else syncSelection()
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
   * L'usager appuie sur une carte de résultat : **deux choses distinctes s'ensuivent**.
   *
   * Le trajet devient le trajet mis en évidence — c'est un état, que la carte trace et cadre
   * (SPEC.md § 5.1) et qui survivra au passage par l'écran de détail — et une ouverture de l'écran
   * de détail est demandée, une fois, par [openDetail] (SPEC.md § 5.3).
   */
  fun onJourneySelected(journey: Journey) {
    select(journey)
    detailRequests.trySend(Unit)
  }

  fun onBikeFilterChanged(filter: BikeFilter) {
    state.update { it.copy(bikeFilter = filter) }
    // Le filtre change la liste visible : le trajet mis en évidence peut ne plus en faire partie.
    syncSelection()
  }

  /**
   * Le brouillon de recherche a changé.
   *
   * Tant qu'il est incomplet, la feuille n'existe pas : la carte occupe tout l'écran, et aucune
   * requête n'est envoyée. Dès qu'il est complet, la recherche part — SPEC.md § 5.1 ne prévoit pas
   * de bouton « Rechercher » — mais **pour le seul onglet consulté**.
   */
  private fun onDraftChanged(draft: SearchDraft) = restart(open = draft.isComplete)

  /**
   * Repart de zéro : tout ce qui court est annulé, les quatre onglets sont oubliés, et le seul
   * onglet consulté est rechargé — s'il y a une recherche à faire.
   */
  private fun restart(open: Boolean) {
    jobs.values.forEach(Job::cancel)
    jobs.clear()
    val category = state.value.category
    state.value = ResultsUiState(open = open, category = category)
    // Plus aucun résultat à montrer : la carte n'a plus rien à tracer, jusqu'à ce que la nouvelle
    // recherche en rende. Le premier trajet du nouveau jeu sera mis en évidence tout seul.
    syncSelection()
    if (open) load(category)
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
    syncSelection()
  }

  /**
   * Remet la sélection en accord avec la liste affichée (SPEC.md § 5.1).
   *
   * La règle tient en une phrase : **le trajet mis en évidence est celui que l'usager a choisi
   * s'il est encore dans la liste, sinon le premier de la liste, et rien du tout si la liste est
   * vide.** C'est ce qui fait qu'une recherche trace son premier trajet sans qu'on ait à appuyer,
   * qu'un changement d'onglet met en évidence le premier trajet du nouveau jeu de résultats, et
   * qu'un jeu vide n'en laisse aucun derrière lui.
   *
   * Rien n'est publié en dehors de cette fonction et de [onJourneySelected] : le retour depuis
   * l'écran de détail ne passe par aucune des deux, la sélection et son tracé lui survivent donc.
   */
  private fun syncSelection() {
    val journeys = state.value.visibleJourneys
    val chosen = journeys.firstOrNull { it.stableKey() == state.value.selectedKey } ?: journeys.firstOrNull()
    select(chosen)
  }

  private fun select(journey: Journey?) {
    state.update { it.copy(selectedKey = journey?.stableKey()) }
    selection.select(journey)
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

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        ResultsViewModel(
          session = container.searchSession,
          planRepository = container.planRepository,
          searchPreferences = container.preferencesRepository.searchPreferences,
          selection = container.selectedJourneyStore,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
