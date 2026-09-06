package io.github.mgdx.escale.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.format.SearchTime
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.AutocompleteQuery
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.autocompleteStream
import io.github.mgdx.escale.core.repository.matchingKnownPlaces
import io.github.mgdx.escale.core.repository.withoutKnownPlaces
import io.github.mgdx.escale.core.result.getOrNull
import io.github.mgdx.escale.ui.map.LocationSource
import io.github.mgdx.escale.ui.map.MapCameraMemory
import io.github.mgdx.escale.ui.map.MapPick
import io.github.mgdx.escale.ui.map.MapPickPurpose
import io.github.mgdx.escale.ui.map.MapSelection
import io.github.mgdx.escale.ui.session.SearchDraft
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

/**
 * La carte de recherche flottante et son autocomplétion (SPEC.md § 5.1).
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne lance aucune requête
 * `plan`** : il remplit [SearchSession], et c'est la feuille de résultats qui réagit au brouillon.
 * « Dès que Départ et Arrivée sont renseignés, la recherche se lance » : il n'y a donc pas de
 * bouton « Rechercher », et rien ici pour en tenir lieu.
 *
 * **C'est ici qu'une recherche est enregistrée dans l'historique** (SPEC.md § 5.5), et c'est
 * délibéré : une recherche est lancée au moment où le brouillon devient complet — « dès que Départ
 * et Arrivée sont renseignés, la recherche se lance » — et non à chaque fois qu'un champ change.
 * [recordIfComplete] n'écrit donc qu'une fois par recherche distincte, heure comprise. La bascule
 * « conserver les recherches récentes » n'est pas relue ici : `HistoryRepository` la respecte déjà,
 * et la vérifier deux fois serait une règle de plus à maintenir à deux endroits.
 *
 * Il ne réimplémente pas davantage les règles de sobriété de SPEC.md § 7.1 : l'anti-rebond de
 * 350 ms, le minimum de trois caractères et l'annulation de la requête précédente vivent dans
 * `autocompleteStream`, dans `:core`, où ils se testent en temps virtuel. Ici, on ne fait que
 * pousser les frappes dans un flux et collecter ce qui en sort.
 *
 * **Aucune adresse, aucune coordonnée, aucune saisie n'est journalisée** (SPEC.md § 8 et § 11) :
 * cet écran manipule exactement les données que la spec interdit d'écrire dans une trace.
 */
class SearchViewModel(
  private val geocodeRepository: GeocodeRepository,
  private val session: SearchSession,
  private val selection: MapSelection,
  private val locationSource: LocationSource,
  private val cameraMemory: MapCameraMemory,
  private val savedPlaces: SavedPlacesSource,
  private val recentSearches: RecentSearchesSource,
  private val savedState: SavedStateHandle,
  private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val state = MutableStateFlow(SearchUiState())
  val uiState: StateFlow<SearchUiState> = state.asStateFlow()

  /**
   * Les frappes successives, telles que `autocompleteStream` les attend.
   *
   * Un flux partagé et non un `StateFlow` : rejouer une requête après un échec suppose de repasser
   * par l'état vide, et un `StateFlow` fusionnerait les deux valeurs émises coup sur coup.
   */
  private val queries = MutableSharedFlow<AutocompleteQuery>(replay = 1, extraBufferCapacity = QUERY_BUFFER)

  /** La dernière frappe émise, pour pouvoir la rejouer telle quelle. */
  private var lastQuery = AutocompleteQuery("")

  /**
   * La saisie en cours, telle que le bloc « déjà utilisés » la filtre (SPEC.md § 5.1).
   *
   * Un `StateFlow` et non [queries] : ce bloc n'a ni anti-rebond ni minimum de trois caractères à
   * respecter — il ne demande rien à personne — et il doit s'afficher **dès la première lettre**.
   */
  private val queryText = MutableStateFlow("")

  /** Ce que le serveur a rendu, avant le retrait des lieux que le bloc montre déjà. */
  private val serverSuggestions = MutableStateFlow<AutocompleteState>(AutocompleteState.Idle)

  /** La dernière recherche écrite dans l'historique, pour ne pas l'y écrire deux fois. */
  private var lastRecorded: RecordedSearch? = null

  /** Vrai quand une position est déjà connue sans rien demander à l'usager. */
  private val myLocationKnown = MutableStateFlow(false)

  /** Les deux lieux enregistrés, tenus à part de l'état d'écran : ce n'est pas de l'affichage. */
  private var savedLocations: Map<SavedPlaceKind, Location?> = emptyMap()

  /**
   * Le biais géographique de l'autocomplétion (SPEC.md § 5.1).
   *
   * Deux sources, dans cet ordre : le centre de la carte, et **à défaut** la dernière position
   * connue — au premier lancement, ou après un vidage du cache, aucun cadrage n'est mémorisé et
   * une requête sans biais ramènerait des lieux du bout du monde. Aucune permission n'est demandée
   * pour cela : la position n'est lue que si elle est déjà accessible. Sans ni l'une ni l'autre,
   * le biais reste nul.
   *
   * Il est relu à l'ouverture d'un champ, et non à chaque frappe : la carte ne bouge pas pendant
   * qu'on tape, et une lecture par caractère serait du gaspillage.
   */
  private var bias: LatLon? = null

  /** La langue de l'interface, transmise telle quelle au serveur de géocodage. */
  private val language: String = Locale.getDefault().language

  init {
    restoreSavedState()
    observeSuggestions()
    observeKnownPlaces()
    observeDraft()
    observeMapPicks()
  }

  /** Le champ passe en plein écran, avec la liste d'autocomplétion (SPEC.md § 5.1). */
  fun onOpenField(field: SearchField) {
    val known = knownPoint()
    myLocationKnown.value = known != null
    setQuery("")
    state.update { it.copy(activeField = field, awaitingMapPick = false) }
    savedState[SAVED_FIELD] = field.name
    viewModelScope.launch { bias = cameraMemory.lastCamera()?.center ?: known }
  }

  /**
   * La position déjà connue, ou `null` si l'application n'a pas le droit de la lire.
   *
   * La permission est vérifiée avant toute lecture, et **rien n'est demandé à l'usager** : la
   * position sert ici de repli, elle ne vaut pas qu'on ouvre une boîte de dialogue.
   */
  private fun knownPoint(): LatLon? =
    if (locationSource.hasCoarsePermission()) locationSource.lastKnownLocation() else null

  /** Retour depuis le plein écran, sans rien choisir. */
  fun onCloseField() {
    setQuery("")
    state.update { it.copy(activeField = null) }
    savedState[SAVED_FIELD] = null
  }

  fun onQueryChange(text: String) {
    setQuery(text)
  }

  /**
   * Une suggestion choisie entre dans la recherche **telle quelle**.
   *
   * C'est la garantie de docs/architecture.md § 11.3 : l'objet rendu par l'autocomplétion garde son
   * `id` et son `kind`, donc `PlanQueryBuilder` enverra le `stopId` d'une gare et non sa position.
   */
  fun onSuggestionSelected(location: Location) {
    fill(state.value.activeField, location)
    onCloseField()
  }

  /** Une des entrées en tête de liste (SPEC.md § 5.1). */
  fun onShortcutSelected(shortcut: SearchShortcut) {
    when (shortcut) {
      SearchShortcut.MY_LOCATION -> useDeviceLocation()
      SearchShortcut.HOME -> useSavedPlace(SavedPlaceKind.HOME)
      SearchShortcut.WORK -> useSavedPlace(SavedPlaceKind.WORK)
      SearchShortcut.PICK_ON_MAP -> awaitMapPick()
    }
  }

  /** Abandon de « Choisir sur la carte » sans avoir posé de point. */
  fun onMapPickCancelled() {
    state.update { it.copy(awaitingMapPick = false) }
  }

  /**
   * Appui long sur une puce Domicile ou Travail : elle devient modifiable et supprimable
   * (SPEC.md § 5.5). Les puces de dernière recherche n'ouvrent rien : elles s'effacent depuis
   * l'écran des favoris, où elles sont listées avec leur heure.
   */
  fun onChipLongPressed(chip: QuickChip) {
    val saved = chip as? QuickChip.Saved ?: return
    state.update { it.copy(savedPlaceMenu = saved.kind) }
  }

  fun onDismissSavedPlaceMenu() {
    state.update { it.copy(savedPlaceMenu = null) }
  }

  /** « Supprimer » depuis l'appui long : le lieu redevient non renseigné, sans puce (SPEC.md § 5.5). */
  fun onRemoveSavedPlace(kind: SavedPlaceKind) {
    onDismissSavedPlaceMenu()
    viewModelScope.launch { savedPlaces.clear(kind) }
  }

  /** Une puce d'accès rapide (SPEC.md § 5.1). */
  fun onChipSelected(chip: QuickChip) {
    when (chip) {
      // Un lieu enregistré est d'abord une destination : c'est l'arrivée qu'on remplit, et le
      // départ seulement si l'arrivée est déjà connue.
      is QuickChip.Saved -> fill(
        if (session.draft.value.to == null) SearchField.TO else SearchField.FROM,
        chip.location,
      )

      // Une dernière recherche se rejoue **entièrement**, heure comprise : sans elle, la puce
      // lancerait une autre recherche sous le même libellé (SPEC.md § 5.1).
      is QuickChip.Recent -> {
        session.setFrom(chip.search.from)
        session.setTo(chip.search.to)
        session.setTime(chip.search.time)
      }
    }
  }

  /** Vide un des deux champs depuis la carte de recherche. */
  fun onClearField(field: SearchField) {
    fill(field, null)
  }

  /** Le bouton d'inversion de SPEC.md § 5.1. La règle appartient à [SearchSession]. */
  fun onSwap() {
    session.swap()
  }

  fun onOpenTimePicker() {
    state.update { it.copy(timePicker = TimePickerUi(TimePickerStep.CHOICE)) }
  }

  fun onDismissTimePicker() {
    state.update { it.copy(timePicker = null) }
  }

  /** « Partir maintenant » : l'heure de la requête sera celle de son émission, pas celle-ci. */
  fun onTimeNowSelected() {
    session.setTime(TimeChoice.Now)
    onDismissTimePicker()
  }

  /** « Partir à… » ou « Arriver avant… » : la date, puis l'heure. */
  fun onTimeModeSelected(mode: TimeMode) {
    state.update { it.copy(timePicker = TimePickerUi(TimePickerStep.DATE, mode)) }
  }

  fun onDateSelected(dateUtcMillis: Long) {
    state.update { current ->
      val picker = current.timePicker ?: return@update current
      current.copy(timePicker = picker.copy(step = TimePickerStep.TIME, dateUtcMillis = dateUtcMillis))
    }
  }

  fun onTimeSelected(hour: Int, minute: Int) {
    val picker = state.value.timePicker
    val mode = picker?.mode
    val date = picker?.dateUtcMillis
    if (mode == null || date == null) return
    val instant = SearchTime.instantAt(date, hour, minute, zone)
    session.setTime(
      when (mode) {
        TimeMode.DEPART_AT -> TimeChoice.DepartAt(instant)
        TimeMode.ARRIVE_BY -> TimeChoice.ArriveBy(instant)
      },
    )
    onDismissTimePicker()
  }

  private fun setQuery(text: String) {
    state.update { it.copy(query = text) }
    queryText.value = text
    lastQuery = AutocompleteQuery(text = text, bias = bias, language = language)
    queries.tryEmit(lastQuery)
  }

  /**
   * Bouton « Réessayer » d'un échec d'autocomplétion (SPEC.md § 8).
   *
   * `autocompleteStream` ignore deux frappes identiques d'affilée — c'est ce qui évite une requête
   * par recomposition. Rejouer la saisie suppose donc de repasser explicitement par l'état vide.
   */
  fun onRetryQuery() {
    if (lastQuery.text.isEmpty()) return
    queries.tryEmit(AutocompleteQuery(text = "", bias = bias, language = language))
    queries.tryEmit(lastQuery)
  }

  /** Écrit un point dans un des deux champs du brouillon partagé, ou l'efface si [location] est nul. */
  private fun fill(field: SearchField?, location: Location?) {
    when (field) {
      SearchField.FROM -> session.setFrom(location)
      SearchField.TO -> session.setTo(location)
      null -> Unit
    }
  }

  /**
   * « Choisir sur la carte » : le plein écran se referme et le clavier avec lui (SPEC.md § 5.1).
   * La carte doit être visible pour qu'un appui long y soit possible.
   */
  private fun awaitMapPick() {
    setQuery("")
    state.update { it.copy(activeField = null, awaitingMapPick = true) }
    savedState[SAVED_FIELD] = null
  }

  /**
   * « Ma position », dont le libellé lisible vient du géocodage inverse (SPEC.md § 5.1).
   *
   * Aucune permission n'est demandée ici : l'entrée n'est proposée que si une position est déjà
   * connue. Si le serveur ne sait rien de cet endroit, le point garde ses coordonnées pour nom.
   */
  private fun useDeviceLocation() {
    val point = locationSource.lastKnownLocation() ?: return
    val field = state.value.activeField
    onCloseField()
    viewModelScope.launch {
      val named = geocodeRepository.reverseGeocode(point, language).getOrNull() ?: point.asUnnamedLocation()
      fill(field, named)
    }
  }

  private fun useSavedPlace(kind: SavedPlaceKind) {
    val place = savedLocations[kind] ?: return
    fill(state.value.activeField, place)
    onCloseField()
  }

  private fun observeSuggestions() {
    viewModelScope.launch {
      geocodeRepository.autocompleteStream(queries).collect { suggestions ->
        serverSuggestions.value = suggestions
      }
    }
  }

  /**
   * Le bloc « déjà utilisés » et les suggestions du serveur, assemblés (SPEC.md § 5.1).
   *
   * Les deux sont posés dans l'état par la **même** émission, et c'est ce qui compte : les
   * suggestions affichées sont celles dont le bloc a été retiré, et les publier séparément
   * laisserait passer, le temps d'une image, une ligne en double.
   *
   * La règle de filtrage, elle, vit dans `:core` : cet écran ne fait que la brancher.
   */
  private fun observeKnownPlaces() {
    val known = combine(
      queryText,
      savedPlaces.home,
      savedPlaces.work,
      savedPlaces.places,
      recentSearches.recentSearches,
    ) { query, home, work, places, recent ->
      matchingKnownPlaces(query, listOfNotNull(home, work) + places, recent)
    }
    viewModelScope.launch {
      known.combine(serverSuggestions) { block, server -> block to server }
        .collect { (block, server) ->
          state.update { it.copy(knownPlaces = block, suggestions = server.withoutKnown(block)) }
        }
    }
  }

  /** Les suggestions du serveur, moins celles que le bloc « déjà utilisés » montre déjà. */
  private fun AutocompleteState.withoutKnown(known: List<Location>): AutocompleteState =
    if (this is AutocompleteState.Suggestions) {
      AutocompleteState.Suggestions(locations.withoutKnownPlaces(known))
    } else {
      this
    }

  /**
   * Le brouillon partagé, les lieux enregistrés et l'historique, en une seule vue.
   *
   * Les puces et les entrées de tête dépendent des trois à la fois : les combiner ici évite qu'un
   * changement de l'un laisse les autres en retard d'un cycle.
   */
  private fun observeDraft() {
    viewModelScope.launch {
      combine(
        session.draft,
        savedPlaces.home,
        savedPlaces.work,
        recentSearches.recentSearches,
        myLocationKnown,
      ) { draft, home, work, recent, myLocation ->
        savedLocations = mapOf(SavedPlaceKind.HOME to home, SavedPlaceKind.WORK to work)
        savedState[SAVED_DRAFT] = encodeSearchDraft(draft)
        Snapshot(draft, quickChips(draft, home, work, recent), searchShortcuts(myLocation, home, work))
      }.collect(::applySnapshot)
    }
  }

  private fun applySnapshot(snapshot: Snapshot) {
    recordIfComplete(snapshot.draft)
    state.update {
      it.copy(
        from = snapshot.draft.from,
        to = snapshot.draft.to,
        time = snapshot.draft.time,
        chips = snapshot.chips,
        shortcuts = snapshot.shortcuts,
      )
    }
  }

  /**
   * Enregistre la recherche **au moment où elle part** (SPEC.md § 5.5).
   *
   * Le brouillon complet est le seul signal disponible, et c'est le bon : il n'y a pas de bouton
   * « Rechercher », et c'est exactement à cet instant que la feuille de résultats émet sa requête.
   * Un champ qui change ne suffit pas — une arrivée saisie sans départ n'est pas une recherche —,
   * et la même recherche affichée à nouveau après une rotation ou un retour d'écran n'en est pas
   * une seconde : [lastRecorded] retient la dernière écrite, heure comprise.
   */
  private fun recordIfComplete(draft: SearchDraft) {
    val from = draft.from ?: return
    val to = draft.to ?: return
    val search = RecordedSearch(from, to, draft.time)
    if (search == lastRecorded) return
    lastRecorded = search
    viewModelScope.launch { recentSearches.record(from, to, draft.time) }
  }

  /**
   * Le point choisi par appui long sur la carte (SPEC.md § 5.1).
   *
   * Il est appliqué dès sa première émission, avec ses coordonnées pour nom, puis de nouveau quand
   * le géocodage inverse lui attache un libellé lisible. Le délai de grâce ne sert qu'à cela :
   * acquitter tout de suite viderait [MapSelection] avant que le libellé y arrive, et ne jamais
   * acquitter réappliquerait le point à chaque rotation, par-dessus une saisie plus récente.
   * `collectLatest` annule l'attente dès que le libellé arrive : dans le cas courant, l'acquittement
   * est immédiat. Rien n'est bloqué pendant ce temps, le point est déjà dans le champ.
   */
  private fun observeMapPicks() {
    viewModelScope.launch {
      selection.pick.filterNotNull().collectLatest { pick ->
        applyPick(pick)
        if (pick.label == null) delay(MAP_PICK_LABEL_GRACE_MILLIS)
        selection.consume()
      }
    }
  }

  private fun applyPick(pick: MapPick) {
    val location = pick.toLocation()
    when (pick.purpose) {
      MapPickPurpose.DEPARTURE -> session.setFrom(location)
      MapPickPurpose.DESTINATION -> session.setTo(location)
    }
    state.update { it.copy(awaitingMapPick = false) }
  }

  /**
   * Restitue la recherche après une mort du processus en arrière-plan.
   *
   * [SearchSession] vit dans l'`AppContainer` et survit à la rotation : si elle contient déjà
   * quelque chose, elle fait foi et l'état sauvegardé n'a rien à y écraser.
   */
  private fun restoreSavedState() {
    val encoded: String? = savedState[SAVED_DRAFT]
    val restored = encoded?.let(::decodeSearchDraft)
    if (restored != null && session.draft.value == SearchDraft()) {
      session.setFrom(restored.from)
      session.setTo(restored.to)
      session.setTime(restored.time)
    }
    // Une recherche restituée après la mort du processus a déjà été enregistrée avant elle : la
    // retenir ici évite d'en écrire un doublon à la reprise.
    val current = session.draft.value
    val restoredFrom = current.from
    val restoredTo = current.to
    if (restoredFrom != null && restoredTo != null) {
      lastRecorded = RecordedSearch(restoredFrom, restoredTo, current.time)
    }
    val field: String? = savedState[SAVED_FIELD]
    val restoredField = SearchField.entries.firstOrNull { it.name == field }
    if (restoredField != null) state.update { it.copy(activeField = restoredField) }
  }

  /** Ce qui identifie une recherche pour l'historique : deux points et une heure, rien de plus. */
  private data class RecordedSearch(val from: Location, val to: Location, val time: TimeChoice)

  private data class Snapshot(val draft: SearchDraft, val chips: List<QuickChip>, val shortcuts: List<SearchShortcut>)

  companion object {
    /**
     * Le temps laissé au géocodage inverse pour nommer un point choisi sur la carte avant que
     * celui-ci soit acquitté. Généreux : il ne retarde rien à l'écran, le point y est déjà.
     */
    private const val MAP_PICK_LABEL_GRACE_MILLIS = 8_000L

    /** De quoi encaisser une rafale de frappes sans jamais bloquer le fil principal. */
    private const val QUERY_BUFFER = 8

    private const val SAVED_DRAFT = "draft"
    private const val SAVED_FIELD = "field"

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        SearchViewModel(
          geocodeRepository = container.geocodeRepository,
          session = container.searchSession,
          selection = container.mapSelection,
          locationSource = container.deviceLocationSource,
          cameraMemory = container.mapCameraStore,
          savedPlaces = FavoritesSavedPlacesSource(container.favoritesRepository),
          recentSearches = HistoryRecentSearchesSource(container.historyRepository),
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
