package io.github.mgdx.escale.ui.search

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.AutocompleteRules
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.map.FakeCameraMemory
import io.github.mgdx.escale.ui.map.FakeLocationSource
import io.github.mgdx.escale.ui.map.MapPickPurpose
import io.github.mgdx.escale.ui.map.MapSelection
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/**
 * La carte de recherche, eprouvee sans Android : le `ViewModel` ne depend que d'interfaces.
 *
 * Aucun cas d'essai ne journalise d'adresse ni de coordonnee (SPEC.md § 8 et § 11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

  private val scheduler = TestCoroutineScheduler()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher(scheduler))

  private val gareDeLyon = stop("de:0800:1234", "Gare de Lyon")
  private val bastille = address("Place de la Bastille")

  private val geocode = FakeSearchGeocodeRepository(suggestions = Outcome.Success(listOf(gareDeLyon)))
  private val session = SearchSession()
  private val selection = MapSelection()
  private val locations = FakeLocationSource()
  private val cameras = FakeCameraMemory()
  private val savedState = SavedStateHandle()

  private fun viewModel(
    savedPlaces: SavedPlacesSource = EmptySavedPlacesSource,
    recent: RecentSearchesSource = EmptyRecentSearchesSource,
    geocodeRepository: FakeSearchGeocodeRepository = geocode,
  ) = SearchViewModel(
    geocodeRepository = geocodeRepository,
    session = session,
    selection = selection,
    locationSource = locations,
    cameraMemory = cameras,
    savedPlaces = savedPlaces,
    recentSearches = recent,
    savedState = savedState,
    zone = ZoneId.of("Europe/Paris"),
  )

  @Test
  fun `l'anti-rebond de la spec est celui de core, pas un second pose par-dessus`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenField(SearchField.FROM)

    viewModel.onQueryChange("g")
    viewModel.onQueryChange("ga")
    viewModel.onQueryChange("gar")

    // Moins de 350 ms apres la derniere frappe : rien n'est encore parti (SPEC.md § 7.1).
    advanceTimeBy(AutocompleteRules.DEBOUNCE_MILLIS - 1)
    assertTrue(geocode.requests.isEmpty())

    // Juste apres : une requete, et une seule. Un anti-rebond ajoute ici en aurait retarde
    // l'envoi de 350 ms de plus.
    advanceTimeBy(2)
    advanceUntilIdle()
    assertEquals(listOf("gar"), geocode.requests)
  }

  @Test
  fun `sous trois caracteres, aucune requete n'est emise`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenField(SearchField.TO)

    viewModel.onQueryChange("ga")
    advanceUntilIdle()

    assertTrue(geocode.requests.isEmpty())
    assertEquals(AutocompleteState.Idle, viewModel.uiState.value.suggestions)
  }

  @Test
  fun `une suggestion choisie entre intacte dans la recherche`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenField(SearchField.FROM)

    viewModel.onSuggestionSelected(gareDeLyon)
    advanceUntilIdle()

    // docs/architecture.md § 11.3 : l'identifiant d'arret part tel quel dans `SearchSession`.
    assertEquals(gareDeLyon, session.draft.value.from)
    assertEquals("de:0800:1234", session.draft.value.from?.id)
    assertEquals(PlaceKind.STOP, session.draft.value.from?.kind)
    assertNull(viewModel.uiState.value.activeField)
  }

  @Test
  fun `deux points renseignes suffisent, il n'y a pas de bouton Rechercher`() = runTest(scheduler) {
    val viewModel = viewModel()

    viewModel.onOpenField(SearchField.FROM)
    viewModel.onSuggestionSelected(bastille)
    viewModel.onOpenField(SearchField.TO)
    viewModel.onSuggestionSelected(gareDeLyon)
    advanceUntilIdle()

    assertTrue(session.draft.value.isComplete)
  }

  @Test
  fun `le bouton d'inversion echange les deux points`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenField(SearchField.FROM)
    viewModel.onSuggestionSelected(bastille)

    viewModel.onSwap()
    advanceUntilIdle()

    assertEquals(bastille, session.draft.value.to)
    assertNull(session.draft.value.from)
    assertEquals(bastille, viewModel.uiState.value.to)
  }

  @Test
  fun `un point choisi sur la carte est nomme par le geocodage inverse`() = runTest(scheduler) {
    viewModel()
    val point = LatLon(48.8443, 2.3737)

    selection.select(MapPickPurpose.DESTINATION, point)
    selection.attachLabel(point, gareDeLyon)
    advanceUntilIdle()

    assertEquals(gareDeLyon, session.draft.value.to)
    // Le point est acquitte des que son libelle est arrive.
    assertNull(selection.pick.value)
  }

  @Test
  fun `un point que le serveur ne nomme pas reste utilisable`() = runTest(scheduler) {
    val viewModel = viewModel()
    val point = LatLon(48.8443, 2.3737)

    selection.select(MapPickPurpose.DEPARTURE, point)
    advanceUntilIdle()

    assertEquals("48.84430, 2.37370", session.draft.value.from?.name)
    assertEquals(point, session.draft.value.from?.coordinates)
    assertNull(viewModel.uiState.value.activeField)
  }

  @Test
  fun `ma position n'apparait que si une position est deja connue`() = runTest(scheduler) {
    val viewModel = viewModel()

    viewModel.onOpenField(SearchField.FROM)
    advanceUntilIdle()
    assertEquals(listOf(SearchShortcut.PICK_ON_MAP), viewModel.uiState.value.shortcuts)

    locations.coarseGranted = true
    locations.lastKnown = LatLon(48.85, 2.35)
    viewModel.onOpenField(SearchField.FROM)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.shortcuts.contains(SearchShortcut.MY_LOCATION))
  }

  @Test
  fun `ma position prend le libelle du geocodage inverse`() = runTest(scheduler) {
    locations.coarseGranted = true
    locations.lastKnown = LatLon(48.85, 2.35)
    geocode.reverse = Outcome.Success(bastille)
    val viewModel = viewModel()

    viewModel.onOpenField(SearchField.FROM)
    viewModel.onShortcutSelected(SearchShortcut.MY_LOCATION)
    advanceUntilIdle()

    assertEquals(bastille, session.draft.value.from)
  }

  @Test
  fun `choisir sur la carte referme le plein ecran et attend un appui long`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenField(SearchField.TO)

    viewModel.onShortcutSelected(SearchShortcut.PICK_ON_MAP)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.activeField)
    assertTrue(viewModel.uiState.value.awaitingMapPick)
  }

  @Test
  fun `le choix d'heure remonte dans la session partagee`() = runTest(scheduler) {
    val viewModel = viewModel()

    viewModel.onOpenTimePicker()
    viewModel.onTimeModeSelected(TimeMode.ARRIVE_BY)
    // Minuit UTC le 5 septembre 2025, tel que le rend un `DatePicker`.
    viewModel.onDateSelected(1_757_030_400_000)
    viewModel.onTimeSelected(hour = 9, minute = 0)
    advanceUntilIdle()

    val time = session.draft.value.time
    assertTrue(time is TimeChoice.ArriveBy)
    assertEquals("2025-09-05T07:00:00Z", (time as TimeChoice.ArriveBy).instant.toString())
    assertNull(viewModel.uiState.value.timePicker)
  }

  @Test
  fun `partir maintenant reste le choix par defaut`() = runTest(scheduler) {
    val viewModel = viewModel()

    assertEquals(TimeChoice.Now, viewModel.uiState.value.time)
  }

  @Test
  fun `la recherche survit a la mort du processus`() = runTest(scheduler) {
    val first = viewModel()
    first.onOpenField(SearchField.FROM)
    first.onSuggestionSelected(gareDeLyon)
    advanceUntilIdle()

    // Le processus meurt : la session applicative disparait, le `SavedStateHandle` reste.
    val revived = SearchSession()
    val restored = SearchViewModel(
      geocodeRepository = geocode,
      session = revived,
      selection = MapSelection(),
      locationSource = locations,
      cameraMemory = cameras,
      savedPlaces = EmptySavedPlacesSource,
      recentSearches = EmptyRecentSearchesSource,
      savedState = savedState,
      zone = ZoneId.of("Europe/Paris"),
    )
    advanceUntilIdle()

    assertEquals(gareDeLyon, revived.draft.value.from)
    assertEquals(gareDeLyon, restored.uiState.value.from)
  }

  @Test
  fun `les lieux enregistres alimentent puces et entrees de tete`() = runTest(scheduler) {
    val home: Location = address("Domicile", lat = 48.86, lon = 2.34)
    val viewModel = viewModel(savedPlaces = FakeSavedPlacesSource(home = home))

    viewModel.onOpenField(SearchField.TO)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.shortcuts.contains(SearchShortcut.HOME))
    viewModel.onShortcutSelected(SearchShortcut.HOME)
    advanceUntilIdle()
    assertEquals(home, session.draft.value.to)
  }
}
