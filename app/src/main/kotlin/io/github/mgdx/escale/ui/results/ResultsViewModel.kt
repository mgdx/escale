package io.github.mgdx.escale.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.geo.isTraced
import io.github.mgdx.escale.core.model.CategoryOrder
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyFeed
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.core.query.RealtimeRefreshPolicy
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
import java.time.Instant

/**
 * La feuille de résultats (SPEC.md § 5.2).
 *
 * Trois règles de sobriété réseau se jouent ici, et nulle part ailleurs dans l'interface :
 *
 * - **les quatre onglets sont chargés en série, l'onglet consulté d'abord** (§ 5.2, § 7.3). Chaque
 *   onglet annonce sous son libellé la durée du trajet le plus rapide qu'il propose : les quatre
 *   catégories doivent donc être connues, et [loadAll] les demande une par une, jamais quatre
 *   requêtes lancées ensemble ;
 * - **un onglet déjà chargé ne recharge pas** (§ 7.5). Le cache mémoire de `PlanRepository` rendrait
 *   la réponse sans réseau, mais on ne va même pas jusque-là : un onglet qui a déjà sa liste est
 *   affiché tel quel, et la chaîne de chargement l'enjambe ;
 * - **une nouvelle recherche annule ce qui court** (§ 7.2). Les travaux en cours sont annulés, les
 *   quatre onglets repartent de zéro, et la chaîne recommence. Un réglage de recherche modifié
 *   (§ 5.6) produit exactement le même effet : les trajets affichés ont été calculés avec les
 *   anciens réglages, ils sont périmés.
 *
 * `EscaleError.Superseded` n'est jamais montré : il signifie qu'un résultat plus récent est en
 * route, et afficher « une erreur est survenue » à quelqu'un qui vient de relancer sa recherche
 * serait un défaut visible (docs/architecture.md § 6).
 *
 * **Le temps réel se rafraîchit sur geste, jamais tout seul** (§ 7.4). Il n'y a ici ni minuterie,
 * ni boucle, ni rafraîchissement périodique : [onPullToRefresh] rafraîchit parce que l'usager l'a
 * demandé, [onForeground] parce que l'application revient au premier plan **et** que
 * `RealtimeRefreshPolicy` juge les horaires périmés. Les deux ne touchent que l'onglet consulté :
 * les durées annoncées par les trois autres restent celles de leur chargement.
 *
 * **La mise en évidence d'un trajet est un état, son ouverture est un événement.** SPEC.md § 5.1
 * veut que la carte « cadre le trajet sélectionné » : dès qu'une liste arrive, son premier trajet
 * est publié dans [SelectedJourneyStore] sans aucun appui, et la sélection suit ensuite la liste
 * affichée ([syncSelection]). L'appui, lui, ajoute une demande d'ouverture de l'écran de détail
 * sur [openDetail], à consommation unique.
 *
 * **Le trajet mis en évidence, et lui seul, obtient sa géométrie réelle** (§ 5.1, § 7 règle 6). La
 * liste continue d'être demandée sans géométrie : ses trajets n'ont donc que leurs extrémités, et
 * la carte n'en tirerait qu'une ligne droite entre le départ et l'arrivée. [requestTrace] va donc
 * chercher le tracé du seul trajet dessiné, en une requête, dont la réponse est mémorisée le temps
 * de la recherche. C'est la quatrième règle de sobriété de cet écran : une requête de plus par
 * trajet mis en évidence, aucune pour les autres.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * il manipule des adresses et des coordonnées, que SPEC.md § 11 interdit d'écrire dans une trace,
 * y compris en débogage.
 */
class ResultsViewModel(
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val searchPreferences: Flow<SearchPreferences>,
  private val displayPreferences: Flow<DisplayPreferences>,
  private val selection: SelectedJourneyStore,
  private val savedState: SavedStateHandle,
  private val now: () -> Instant = Instant::now,
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

  /**
   * Les trajets dont on a rapatrié la géométrie réelle, par `stableKey()`.
   *
   * La clé n'est pas l'identifiant d'itinéraire : les trajets directs des onglets À pied, Vélo et
   * Voiture n'en reçoivent pas, et il leur faut pourtant une entrée ici.
   *
   * Ce cache tient le temps d'une recherche et pas davantage : [restart] le vide, parce que les
   * trajets de la recherche précédente ne sont plus affichés.
   */
  private val traced = mutableMapOf<String, Journey>()

  /** La requête de tracé en vol, au plus une : seul le trajet mis en évidence en mérite une. */
  private var traceJob: Job? = null

  /** La chaîne qui charge les quatre onglets l'un après l'autre, annulable d'un bloc. */
  private var searchJob: Job? = null

  /** Les réglages de SPEC.md § 5.6, tels que le dépôt les a émis en dernier. */
  private var preferences = SearchPreferences()

  /** Faux tant que le dépôt n'a rien émis : aucune recherche ne part avant de les connaître. */
  private var preferencesKnown = false

  /**
   * Vrai dès que l'usager a lui-même choisi un onglet, et **après la mort du processus** si c'était
   * le cas avant elle.
   *
   * C'est ce qui départage les deux lectures possibles de l'ordre des catégories : tant que
   * personne n'a touché aux languettes, la première de la liste est celle qu'on ouvre ; dès qu'un
   * onglet a été choisi, c'est lui qu'on retrouve, et un ordre modifié entre-temps ne le déloge pas.
   */
  private var categoryChosen = savedState.contains(KEY_CATEGORY)

  init {
    viewModelScope.launch {
      searchPreferences.collect(::onPreferencesChanged)
    }
    viewModelScope.launch {
      displayPreferences.collect(::onDisplayPreferencesChanged)
    }
  }

  /**
   * L'ordre des onglets a changé (SPEC.md § 5.6).
   *
   * **Aucune requête n'en découle** : les listes affichées ont été calculées avec les mêmes
   * paramètres, seule leur disposition change. C'est toute la différence avec un réglage de
   * recherche, qui périme les résultats et fait tout repartir.
   */
  private fun onDisplayPreferencesChanged(display: DisplayPreferences) {
    val order = CategoryOrder.sanitized(display.categoryOrder)
    state.update { it.copy(categoryOrder = order) }
    if (!categoryChosen) switchTo(order.first())
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
   *   anciens réglages. Les quatre onglets repartent de zéro et sont rechargés en série, l'onglet
   *   consulté d'abord (SPEC.md § 7.3).
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
   * L'usager change d'onglet.
   *
   * Il n'y a normalement **rien à charger** : les quatre onglets ont été demandés au départ de la
   * recherche, et celui-ci a déjà sa liste, son erreur, ou sa requête en vol. Le trajet mis en
   * évidence suit la nouvelle liste, et la carte le trace aussitôt ([syncSelection], SPEC.md
   * § 5.1). Le chargement ne subsiste que pour l'onglet qu'aucune chaîne n'a atteint — une
   * recherche relancée pendant que l'usager change d'onglet, par exemple.
   *
   * Un onglet en erreur n'émet rien : la reprise est un geste explicite ([onRetry]), sans quoi un
   * serveur en panne serait interrogé à chaque aller-retour entre deux onglets.
   */
  fun onCategorySelected(category: JourneyCategory) {
    savedState[KEY_CATEGORY] = category.name
    categoryChosen = true
    switchTo(category)
  }

  /**
   * Passe à un autre onglet, que l'usager l'ait demandé ou que l'ordre réglé l'ait désigné.
   *
   * Seul [onCategorySelected] en garde la trace : un onglet ouvert parce qu'il est en tête de
   * l'ordre n'est pas un onglet choisi, et il doit pouvoir céder la place au suivant si l'usager
   * range ses catégories autrement.
   */
  private fun switchTo(category: JourneyCategory) {
    if (state.value.category == category) return
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

  /**
   * « Tirer pour rafraîchir » (SPEC.md § 7.4).
   *
   * Le geste de l'usager rafraîchit **toujours**, sans condition de fraîcheur : c'est lui qui
   * décide. La liste reste affichée pendant la requête, qui repart de la première page avec les
   * horaires temps réel du moment — d'où `fresh`, sans quoi le cache mémoire du § 7.5 rendrait la
   * réponse précédente et le geste n'aurait aucun effet.
   */
  fun onPullToRefresh() = refresh(state.value.category)

  /**
   * Le retour au premier plan (SPEC.md § 7.4 : « ou au retour au premier plan si les données ont
   * plus de 60 secondes »).
   *
   * **La règle n'est pas ici** : elle est dans `RealtimeRefreshPolicy`, en Kotlin pur et vérifiée
   * en JVM, seuil compris. Cet écran fournit les deux instants et applique la réponse. Et il ne
   * réveille que l'onglet consulté : les autres restent muets (§ 7.3).
   */
  fun onForeground() {
    val category = state.value.category
    val tab = state.value.tabs[category] ?: return
    if (RealtimeRefreshPolicy.shouldRefreshOnForeground(tab.loadedAt, now())) refresh(category)
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
   * Repart de zéro : tout ce qui court est annulé, les quatre onglets sont oubliés, et la chaîne
   * de chargement recommence — s'il y a une recherche à faire.
   */
  private fun restart(open: Boolean) {
    searchJob?.cancel()
    searchJob = null
    jobs.values.forEach(Job::cancel)
    jobs.clear()
    traceJob?.cancel()
    traceJob = null
    // Les identifiants d'itinéraire de la recherche précédente ne désignent plus rien d'affiché.
    traced.clear()
    val category = state.value.category
    // Les quatre onglets sont en attente **avant** la première requête : leur languette annonce
    // qu'une réponse arrive, et non qu'il n'y a rien à proposer (SPEC.md § 5.2). L'ordre réglé
    // n'est pas un résultat : il traverse la remise à zéro intacte.
    state.value = ResultsUiState(
      open = open,
      category = category,
      categoryOrder = state.value.categoryOrder,
      tabs = if (open) awaiting() else emptyMap(),
    )
    // Plus aucun résultat à montrer : la carte n'a plus rien à tracer, jusqu'à ce que la nouvelle
    // recherche en rende. Le premier trajet du nouveau jeu sera mis en évidence tout seul.
    syncSelection()
    if (open) loadAll()
  }

  /** Les quatre onglets, en attente de leur réponse. */
  private fun awaiting(): Map<JourneyCategory, TabResults> =
    JourneyCategory.entries.associateWith { TabResults(loading = true) }

  /**
   * Charge les quatre onglets **l'un après l'autre**, l'onglet consulté d'abord (SPEC.md § 5.2).
   *
   * En série, et non en parallèle : l'usager attend la catégorie qu'il regarde, et trois requêtes
   * lancées en même temps qu'elle ne feraient que retarder sa réponse. Les trois autres arrivent
   * ensuite, et chacune remplit la durée annoncée sous sa languette au fur et à mesure.
   *
   * Un onglet qui a déjà sa liste, ou dont l'usager a lui-même relancé la requête, est enjambé :
   * sa réponse est au moins aussi récente que celle qu'on irait chercher (SPEC.md § 7.5).
   */
  private fun loadAll() {
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      for (category in searchOrder()) {
        if (state.value.tabs[category]?.feed != null || jobs[category]?.isActive == true) continue
        runLoad(category)
      }
    }
  }

  /**
   * L'onglet consulté, puis les trois autres **dans l'ordre des languettes** (SPEC.md § 5.2).
   *
   * L'ordre est celui que l'usager a réglé (SPEC.md § 5.6) : les onglets qu'il a mis en tête sont
   * ceux dont il attend la durée en premier.
   */
  private fun searchOrder(): List<JourneyCategory> {
    val category = state.value.category
    return listOf(category) + state.value.categoryOrder.filterNot { it == category }
  }

  /**
   * Relance l'onglet demandé avec les horaires du moment, si tant est qu'il ait déjà quelque chose
   * à rafraîchir.
   *
   * Un onglet en cours de chargement, déjà en train de se rafraîchir ou qui n'a encore rien reçu
   * n'émet rien : rafraîchir une liste qui n'existe pas serait une requête de plus pour rien
   * (SPEC.md § 7).
   */
  private fun refresh(category: JourneyCategory) {
    val tab = state.value.tabs[category] ?: return
    if (tab.loading || tab.refreshing || tab.feed == null) return
    load(category, refresh = true)
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

  /** Une requête déclenchée par un geste : elle a son propre travail, annulable à elle seule. */
  private fun load(
    category: JourneyCategory,
    cursor: String? = null,
    page: ResultsPage? = null,
    refresh: Boolean = false,
  ) {
    jobs[category]?.cancel()
    jobs[category] = viewModelScope.launch { runLoad(category, cursor, page, refresh) }
  }

  /**
   * Une requête, et l'état de l'onglet pendant qu'elle court.
   *
   * Suspendue plutôt que lancée : c'est ce qui permet à [loadAll] d'enchaîner les quatre onglets
   * sans qu'ils se recouvrent, et de tout annuler d'un seul travail.
   */
  private suspend fun runLoad(
    category: JourneyCategory,
    cursor: String? = null,
    page: ResultsPage? = null,
    refresh: Boolean = false,
  ) {
    val query = session.toQuery(category, preferences) ?: return
    updateTab(category) { tab ->
      when {
        page != null -> tab.copy(paging = page, error = null)

        // Un rafraîchissement garde la liste sous les yeux : elle est périmée de quelques
        // secondes, pas fausse, et la remplacer par un écran d'attente serait un recul.
        refresh -> tab.copy(refreshing = true, error = null)

        else -> TabResults(loading = true)
      }
    }
    when (val outcome = planRepository.plan(query, cursor, fresh = refresh)) {
      is Outcome.Success -> updateTab(category) { it.extendedWith(outcome.value, page) }
      is Outcome.Failure -> onFailure(category, outcome.error)
    }
  }

  private fun onFailure(category: JourneyCategory, error: EscaleError) {
    // Une requête supplantée ne s'affiche jamais : le résultat plus récent arrive derrière, et
    // c'est lui qui remplacera l'état de chargement en cours (SPEC.md § 7.2).
    if (error == EscaleError.Superseded) return
    updateTab(category) { it.copy(loading = false, paging = null, refreshing = false, error = error) }
  }

  /** Recolle une page à la liste déjà affichée, ou la remplace s'il s'agit d'une nouvelle recherche. */
  private fun TabResults.extendedWith(page: JourneyPage, direction: ResultsPage?): TabResults {
    val previous = feed
    val next = when {
      direction == null || previous == null -> JourneyFeed.of(page)
      direction == ResultsPage.EARLIER -> previous.earlier(page)
      else -> previous.later(page)
    }
    // L'heure du chargement sert au seul retour au premier plan (SPEC.md § 7.4) : c'est elle que
    // `RealtimeRefreshPolicy` compare au seuil de 60 secondes.
    return TabResults(feed = next, loadedAt = now())
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

  /**
   * Publie le trajet mis en évidence, **dans la meilleure version connue**.
   *
   * La version affichée dans la liste part tout de suite : la carte a de quoi dessiner sans
   * attendre le réseau. Si sa géométrie manque — c'est le cas normal, la liste demandant
   * `detailedLegs=false` (SPEC.md § 7, règle 6) —, [requestTrace] va chercher le tracé réel et le
   * republie derrière.
   */
  private fun select(journey: Journey?) {
    val key = journey?.stableKey()
    val changed = key != state.value.selectedKey
    state.update { it.copy(selectedKey = key) }
    selection.select(journey?.let { traced[key] ?: it })
    // La requête ne part qu'au changement de trajet mis en évidence : `syncSelection` est appelée
    // à chaque arrivée d'onglet, et republier le même trajet ne justifie aucune requête (§ 7).
    if (changed) requestTrace(journey)
  }

  /**
   * Va chercher la géométrie réelle du trajet mis en évidence (SPEC.md § 5.1).
   *
   * **Une requête, pour un seul trajet.** La liste reste demandée sans géométrie, comme l'exige la
   * règle 6 du § 7 : c'est le seul trajet que la carte dessine qui vaut le détour par le réseau,
   * et sa réponse est mémorisée le temps de la recherche pour qu'un aller-retour entre deux onglets
   * n'en redemande pas.
   *
   * Rien ne part quand le trajet porte déjà sa géométrie, ni quand on l'a déjà rapatriée. Un échec
   * ne se voit pas non plus : le tracé approché reste sur la carte, ce qui vaut mieux qu'un bandeau
   * d'erreur pour une requête que personne n'a demandée.
   */
  private fun requestTrace(journey: Journey?) {
    traceJob?.cancel()
    traceJob = null
    if (journey == null || journey.isTraced) return
    val key = journey.stableKey()
    if (traced.containsKey(key)) return
    traceJob = viewModelScope.launch {
      val detailed = tracedVersionOf(journey) ?: return@launch
      traced[key] = detailed
      // Le trajet mis en évidence a pu changer pendant la requête : on ne remplace que le sien.
      if (state.value.selectedKey == key) selection.select(detailed)
    }
  }

  /**
   * Le trajet dans sa version tracée, par le chemin le moins coûteux d'abord.
   *
   * Le rafraîchissement d'itinéraire ne demande que l'identifiant du trajet : c'est la requête la
   * plus légère qui rende sa géométrie. Encore faut-il que le serveur ait donné cet identifiant —
   * **les trajets directs des onglets À pied, Vélo et Voiture n'en ont pas**, vérifié sur un
   * serveur MOTIS —, et qu'il n'ait pas expiré entre-temps. Dans ces deux cas, et dans ces deux
   * cas seulement, on passe par [replanned].
   */
  private suspend fun tracedVersionOf(journey: Journey): Journey? {
    val itineraryId = journey.id
    if (itineraryId != null) {
      val outcome = planRepository.refresh(itineraryId, detailedLegs = true)
      if (outcome is Outcome.Success) return outcome.value
      if (!JourneyRefresh.invalidatesItineraryId((outcome as Outcome.Failure).error)) return null
    }
    return replanned(journey)
  }

  /**
   * Le repli : rejouer la requête de l'onglet consulté, détaillée cette fois, et y retrouver le
   * trajet mis en évidence par son heure de départ.
   *
   * L'appariement est celui de `JourneyRefresh`, en Kotlin pur et déjà éprouvé pour l'écran de
   * détail : `detailedTransfers` accompagne `detailedLegs`, et le serveur peut alors découper une
   * correspondance en portions supplémentaires — comparer les trajets par leur forme serait
   * fragile, leur heure de départ ne l'est pas.
   *
   * La réponse détaillée a sa propre entrée dans le cache de `PlanRepository` : revenir plus tard
   * sur le même onglet ne la redemande pas (SPEC.md § 7.5).
   */
  private suspend fun replanned(journey: Journey): Journey? {
    val query = session.toQuery(state.value.category, preferences) ?: return null
    val outcome = planRepository.plan(query, cursor = null, detailedLegs = true)
    if (outcome !is Outcome.Success) return null
    val page = outcome.value
    return JourneyRefresh.closestToDeparture(page.journeys + page.direct, journey.startTime)
  }

  /**
   * L'onglet à ouvrir avant que le moindre réglage ne soit connu : celui que l'usager consultait,
   * à défaut le premier de l'ordre par défaut. L'ordre réglé, lui, arrive quelques instants plus
   * tard et prend le relais dans [onDisplayPreferencesChanged].
   */
  private fun restoredCategory(): JourneyCategory {
    val saved = savedState.get<String>(KEY_CATEGORY)
    return JourneyCategory.entries.firstOrNull { it.name == saved } ?: CategoryOrder.DEFAULT.first()
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
          displayPreferences = container.preferencesRepository.displayPreferences,
          selection = container.selectedJourneyStore,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
