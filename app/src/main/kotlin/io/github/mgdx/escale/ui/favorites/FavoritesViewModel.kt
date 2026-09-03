package io.github.mgdx.escale.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.repository.AutocompleteQuery
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.repository.autocompleteStream
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.search.SavedPlaceKind
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * L'écran des favoris et de l'historique (SPEC.md § 5.5).
 *
 * **Ce qui se joue ici, et nulle part ailleurs :**
 *
 * - **Domicile et travail se créent, se modifient et se suppriment** (SPEC.md § 5.5 : « ces deux
 *   emplacements se créent depuis les réglages »). C'est le seul écran qui les propose, et il ne
 *   les propose qu'à qui l'a ouvert exprès : l'application ne les réclame jamais d'elle-même, en
 *   particulier pas sur l'écran d'accueil ni à la première ouverture.
 * - **Un identifiant d'arrêt que le serveur ne reconnaît plus est signalé, jamais effacé**
 *   (SPEC.md § 5.6.1). La vérification a lieu **une fois par ouverture d'écran**, arrêt par arrêt,
 *   au plus [MAX_VERIFIED_STOPS] fois : c'est un geste délibéré de l'usager qui l'a déclenchée, et
 *   non une tâche de fond (SPEC.md § 7). Un échec qui ne met pas l'identifiant en cause — réseau
 *   coupé, serveur muet — ne signale rien du tout.
 * - **Un favori rejoué remplit la recherche partagée**, sans partir lui-même : c'est la feuille de
 *   résultats qui lance la requête dès que départ et arrivée sont connus (SPEC.md § 5.1), et il n'y
 *   a donc pas plus de bouton « Rechercher » ici qu'ailleurs.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * il manipule le domicile de l'usager, c'est-à-dire exactement ce que SPEC.md § 11 interdit
 * d'écrire dans une trace, y compris en débogage.
 */
class FavoritesViewModel(
  private val favorites: FavoritesRepository,
  private val history: HistoryRepository,
  private val geocode: GeocodeRepository,
  private val trips: TripRepository,
  private val session: SearchSession,
  preferences: PreferencesRepository,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(FavoritesUiState())
  val uiState: StateFlow<FavoritesUiState> = state.asStateFlow()

  /** Les frappes du choix de lieu, telles que `autocompleteStream` les attend. */
  private val queries = MutableSharedFlow<AutocompleteQuery>(replay = 1, extraBufferCapacity = QUERY_BUFFER)

  /** Ce que le serveur a répondu pour chaque identifiant d'arrêt déjà présenté. */
  private val recognitions = mutableMapOf<String, StopRecognition>()

  /** La langue de l'interface, transmise telle quelle au serveur de géocodage. */
  private val language: String = Locale.getDefault().language

  init {
    observe(favorites.home) { home -> state.update { it.copy(home = home) } }
    observe(favorites.work) { work -> state.update { it.copy(work = work) } }
    observe(favorites.places) { places -> state.update { it.copy(places = places) } }
    observe(favorites.journeys) { journeys -> state.update { it.copy(journeys = journeys) } }
    observe(favorites.stops, ::onStops)
    observe(history.recentSearches) { searches -> state.update { it.copy(recentSearches = searches) } }
    observe(preferences.displayPreferences) { display ->
      state.update { it.copy(historyEnabled = display.historyEnabled) }
    }
    viewModelScope.launch {
      geocode.autocompleteStream(queries).collect { suggestions ->
        state.update { current -> current.copy(picker = current.picker?.copy(suggestions = suggestions)) }
      }
    }
  }

  // --- Choix d'un lieu ---------------------------------------------------------------------

  /** Ouvre le choix de lieu, avec sa liste vide : rien n'est demandé au serveur avant une frappe. */
  fun onOpenPicker(target: PickerTarget) {
    state.update { it.copy(picker = PlacePickerUi(target = target)) }
    emitQuery("")
  }

  fun onDismissPicker() {
    state.update { it.copy(picker = null) }
    emitQuery("")
  }

  fun onPickerQueryChange(text: String) {
    state.update { current -> current.copy(picker = current.picker?.copy(query = text)) }
    emitQuery(text)
  }

  fun onPickerLabelChange(label: String) {
    state.update { current -> current.copy(picker = current.picker?.copy(label = label)) }
  }

  /**
   * Une suggestion choisie est enregistrée **telle quelle**.
   *
   * C'est la garantie de docs/architecture.md § 11.3 : le lieu garde son `id` et son `kind`, si
   * bien qu'un favori d'arrêt part plus tard en `stopId` et non en coordonnées.
   */
  fun onPickerSelected(location: Location) {
    val picker = state.value.picker ?: return
    val label = picker.label.trim().takeIf { it.isNotEmpty() }
    onDismissPicker()
    viewModelScope.launch {
      val outcome: Outcome<*> = when (val target = picker.target) {
        is PickerTarget.Named -> when (target.kind) {
          SavedPlaceKind.HOME -> favorites.setHome(location)
          SavedPlaceKind.WORK -> favorites.setWork(location)
        }

        PickerTarget.Place -> favorites.addPlace(location, label)

        // Un arrêt sans identifiant n'est pas un arrêt : la liste ne propose que des `STOP`, et
        // cette garde ferme le cas où le serveur en rendrait un sans `id`.
        PickerTarget.Stop -> location.toStop()
          ?.let { stop -> favorites.addStop(stop) }
          ?: Outcome.Failure(EscaleError.Unknown(cause = MISSING_STOP_ID))
      }
      show(if (outcome is Outcome.Success) FavoritesMessage.SAVED else FavoritesMessage.FAILED)
    }
  }

  // --- Suppressions ------------------------------------------------------------------------

  fun onRemoveNamed(kind: SavedPlaceKind) = write {
    when (kind) {
      SavedPlaceKind.HOME -> favorites.setHome(null)
      SavedPlaceKind.WORK -> favorites.setWork(null)
    }
  }

  fun onRemovePlace(place: FavoritePlace) = write { favorites.removePlace(place.id) }

  fun onRemoveStop(stop: Stop) = write { favorites.removeStop(stop.id) }

  fun onRemoveJourney(journey: FavoriteJourney) = write { favorites.removeJourney(journey.id) }

  /** Une entrée d'historique s'efface seule (SPEC.md § 5.5 : « une par une ou en bloc »). */
  fun onDeleteSearch(entry: SearchHistoryEntry) = write { history.delete(entry.id) }

  fun onOpenDialog(dialog: FavoritesDialog) = state.update { it.copy(dialog = dialog) }

  fun onDismissDialog() = state.update { it.copy(dialog = null) }

  /** « Tout effacer », confirmation faite (SPEC.md § 5.5 et § 5.6). */
  fun onClearHistory() {
    state.update { it.copy(dialog = null) }
    viewModelScope.launch {
      val cleared = history.clear() is Outcome.Success
      show(if (cleared) FavoritesMessage.HISTORY_CLEARED else FavoritesMessage.FAILED)
    }
  }

  // --- Relancer une recherche --------------------------------------------------------------

  /**
   * Un lieu favori devient la **destination** de la recherche en cours.
   *
   * Toujours la destination, jamais le départ : on met en favori l'endroit où l'on va. La règle des
   * puces d'accueil, qui remplit le départ quand l'arrivée est déjà connue, n'a pas cours ici —
   * l'usager vient d'ouvrir un écran pour désigner un but, pas pour compléter une saisie en cours.
   */
  fun onSearchPlace(location: Location) = session.setTo(location)

  fun onSearchJourney(journey: FavoriteJourney) {
    session.setFrom(journey.from)
    session.setTo(journey.to)
  }

  /** Une recherche passée se rejoue **avec son heure** : sans elle, ce serait une autre recherche. */
  fun onSearchAgain(entry: SearchHistoryEntry) {
    session.setFrom(entry.from)
    session.setTo(entry.to)
    session.setTime(entry.time)
  }

  /** Le message a été montré : on l'oublie, pour qu'une rotation ne le rejoue pas. */
  fun onMessageShown() = state.update { it.copy(message = null) }

  // --- Interne -----------------------------------------------------------------------------

  private fun onStops(stops: List<Stop>) {
    state.update { current ->
      current.copy(
        stops = stops.map { stop ->
          FavoriteStopUi(stop = stop, recognition = recognitions[stop.id] ?: StopRecognition.UNKNOWN)
        },
      )
    }
    verify(stops)
  }

  /**
   * Présente au serveur courant les identifiants d'arrêts jamais vérifiés (SPEC.md § 5.6.1).
   *
   * Une requête par arrêt, **séquentielle**, plafonnée : ce n'est pas une synchronisation, c'est la
   * vérification d'une poignée de favoris à l'ouverture d'un écran. Un arrêt déjà présenté n'est
   * pas redemandé, et un doute n'est pas une réponse : seul un refus explicite du serveur produit
   * un signalement, jamais une panne de réseau.
   */
  private fun verify(stops: List<Stop>) {
    val pending = stops.map { it.id }.filterNot(recognitions::containsKey).take(MAX_VERIFIED_STOPS)
    if (pending.isEmpty()) return
    viewModelScope.launch {
      pending.forEach { id ->
        val outcome = trips.departures(stopId = id, time = now(), count = 1)
        val recognition = stopRecognition((outcome as? Outcome.Failure)?.error)
        // Un doute ne s'enregistre pas : l'arrêt sera reproposé à la prochaine ouverture, quand le
        // réseau sera peut-être revenu.
        if (recognition == StopRecognition.UNKNOWN) return@forEach
        recognitions[id] = recognition
        state.update { current ->
          current.copy(
            stops = current.stops.map { row ->
              if (row.stop.id == id) row.copy(recognition = recognition) else row
            },
          )
        }
      }
    }
  }

  private fun emitQuery(text: String) {
    queries.tryEmit(AutocompleteQuery(text = text, language = language))
  }

  private fun write(block: suspend () -> Outcome<Unit>) {
    viewModelScope.launch {
      show(if (block() is Outcome.Success) FavoritesMessage.DELETED else FavoritesMessage.FAILED)
    }
  }

  private fun show(message: FavoritesMessage) = state.update { it.copy(message = message) }

  /**
   * Un arrêt à mettre en favori, ou `null` quand le lieu n'en est pas un.
   *
   * Les lignes desservies ne sont pas recopiées : elles changent avec l'horaire du serveur et se
   * rechargent à l'appui (voir `FavoriteStopEntity`).
   */
  private fun Location.toStop(): Stop? = id?.let { stopId ->
    Stop(id = stopId, name = name, coordinates = coordinates, modes = servedModes)
  }

  private fun <T> observe(flow: Flow<T>, block: (T) -> Unit) {
    viewModelScope.launch { flow.collect(block) }
  }

  companion object {
    /**
     * Le nombre d'identifiants d'arrêts présentés au serveur à l'ouverture de l'écran.
     *
     * Dix suffisent à couvrir une liste de favoris ordinaire ; au-delà, les suivants restent
     * affichés sans signalement, ce qui est exactement ce que SPEC.md § 5.6.1 demande par défaut —
     * le favori reste, quoi qu'il arrive.
     */
    const val MAX_VERIFIED_STOPS = 10

    private const val QUERY_BUFFER = 8

    /** Le serveur a rendu un arrêt sans identifiant : il n'y a rien à mettre en favori. */
    private const val MISSING_STOP_ID = "StopWithoutId"

    /** Les suggestions qu'un choix d'arrêt propose : les arrêts, et eux seuls. */
    fun stopSuggestions(state: AutocompleteState): AutocompleteState = when (state) {
      is AutocompleteState.Suggestions ->
        AutocompleteState.Suggestions(state.locations.filter { it.kind == PlaceKind.STOP && it.id != null })

      else -> state
    }

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        FavoritesViewModel(
          favorites = container.favoritesRepository,
          history = container.historyRepository,
          geocode = container.geocodeRepository,
          trips = container.tripRepository,
          session = container.searchSession,
          preferences = container.preferencesRepository,
        )
      }
    }
  }
}
