package io.github.mgdx.escale.ui.favorites

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.map.FakePreferencesRepository
import io.github.mgdx.escale.ui.search.FakeSearchGeocodeRepository
import io.github.mgdx.escale.ui.search.SavedPlaceKind
import io.github.mgdx.escale.ui.search.address
import io.github.mgdx.escale.ui.search.stop
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * L'écran des favoris, éprouvé sans Android : le `ViewModel` ne dépend que d'interfaces.
 *
 * Aucun cas d'essai ne journalise d'adresse ni de coordonnée (SPEC.md § 8 et § 11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

  private val scheduler = TestCoroutineScheduler()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher(scheduler))

  private val favorites = FakeFavoritesRepository()
  private val history = FakeHistoryRepository()
  private val geocode = FakeSearchGeocodeRepository()
  private val session = SearchSession()
  private val preferences = FakePreferencesRepository()

  private fun viewModel(trips: FakeStopsTripRepository = FakeStopsTripRepository(emptyMap())) = FavoritesViewModel(
    favorites = favorites,
    history = history,
    geocode = geocode,
    trips = trips,
    session = session,
    preferences = preferences,
    now = { NOW },
  )

  @Test
  fun `un lieu enregistre garde le nom que l usager lui donne`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onOpenPicker(PickerTarget.Place)
    viewModel.onPickerLabelChange("  Chez Maman  ")
    viewModel.onPickerSelected(address("8 avenue du Général-Leclerc"))
    advanceUntilIdle()

    val place = viewModel.uiState.value.places.single()
    // Le libellé est nettoyé, mais le nom du serveur reste intact (SPEC.md § 5.5 et § 5.6.1).
    assertEquals("Chez Maman", place.label)
    assertEquals("8 avenue du Général-Leclerc", place.location.name)
    assertEquals(FavoritesMessage.SAVED, viewModel.uiState.value.message)
  }

  @Test
  fun `un lieu se supprime par son identifiant, sans emporter son homonyme`() = runTest(scheduler) {
    val viewModel = viewModel()
    val lieu = address("Place de la Bastille")
    favorites.addPlace(lieu, label = "Le café")
    favorites.addPlace(lieu, label = "L'appartement au-dessus")
    advanceUntilIdle()

    viewModel.onRemovePlace(viewModel.uiState.value.places.first { it.label == "Le café" })
    advanceUntilIdle()

    assertEquals(listOf("L'appartement au-dessus"), viewModel.uiState.value.places.map { it.label })
  }

  @Test
  fun `le domicile se supprime, et redevient non renseigne`() = runTest(scheduler) {
    val viewModel = viewModel()
    favorites.setHome(address("12 rue des Lilas"))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.home != null)

    viewModel.onRemoveNamed(SavedPlaceKind.HOME)
    advanceUntilIdle()

    // « Non renseigné » est un état normal : aucune puce ne lui correspondra (SPEC.md § 5.5).
    assertNull(viewModel.uiState.value.home)
  }

  @Test
  fun `un identifiant refuse par le serveur est signale, et le favori reste`() = runTest(scheduler) {
    val trips = FakeStopsTripRepository(
      mapOf("de:06:9999" to Outcome.Failure(EscaleError.BadRequest(serverMessage = null))),
    )
    val viewModel = viewModel(trips)
    favorites.addStop(favoriteStop("de:06:1234", "Gare de Lyon"))
    favorites.addStop(favoriteStop("de:06:9999", "Châtelet"))
    advanceUntilIdle()

    val rows = viewModel.uiState.value.stops
    // Le favori refusé est toujours là — SPEC.md § 5.6.1 interdit de le supprimer tout seul —,
    // signalé, et ses coordonnées sont toujours dans l'état, prêtes à être affichées.
    assertEquals(2, rows.size)
    val refuse = rows.first { it.stop.id == "de:06:9999" }
    assertEquals(StopRecognition.UNRECOGNIZED, refuse.recognition)
    assertEquals(48.8443, refuse.stop.coordinates.lat, 1e-9)
    assertEquals(StopRecognition.KNOWN, rows.first { it.stop.id == "de:06:1234" }.recognition)
  }

  @Test
  fun `un reseau coupe ne signale rien, et l arret sera represente plus tard`() = runTest(scheduler) {
    val trips = FakeStopsTripRepository(mapOf("de:06:1234" to Outcome.Failure(EscaleError.NoNetwork)))
    val viewModel = viewModel(trips)
    favorites.addStop(favoriteStop("de:06:1234", "Gare de Lyon"))
    advanceUntilIdle()

    // Accuser un favori parce que le réseau manque serait pire que ne rien dire.
    assertEquals(StopRecognition.UNKNOWN, viewModel.uiState.value.stops.single().recognition)

    // Et le doute n'est pas retenu : une nouvelle émission le repose au serveur.
    favorites.addStop(favoriteStop("de:06:5678", "Nation"))
    advanceUntilIdle()
    assertEquals(listOf("de:06:1234", "de:06:1234", "de:06:5678"), trips.asked)
  }

  @Test
  fun `un arret deja reconnu n est pas redemande`() = runTest(scheduler) {
    val trips = FakeStopsTripRepository(emptyMap())
    val viewModel = viewModel(trips)
    favorites.addStop(favoriteStop("de:06:1234", "Gare de Lyon"))
    advanceUntilIdle()
    favorites.addStop(favoriteStop("de:06:5678", "Nation"))
    advanceUntilIdle()

    // Une requête par arrêt, jamais deux : SPEC.md § 7 ne tolère pas la requête pour rien.
    assertEquals(listOf("de:06:1234", "de:06:5678"), trips.asked)
    assertEquals(2, viewModel.uiState.value.stops.size)
  }

  @Test
  fun `une recherche passee se rejoue avec son heure`() = runTest(scheduler) {
    val viewModel = viewModel()
    val depart = Instant.parse("2026-03-02T09:00:00Z")
    history.record(address("Bastille"), stop("de:06:1234", "Gare de Lyon"), TimeChoice.ArriveBy(depart))
    advanceUntilIdle()

    viewModel.onSearchAgain(viewModel.uiState.value.recentSearches.single())

    assertEquals("Bastille", session.draft.value.from?.name)
    assertEquals("Gare de Lyon", session.draft.value.to?.name)
    // Sans l'heure, la puce lancerait une autre recherche sous le même libellé (SPEC.md § 5.1).
    assertEquals(TimeChoice.ArriveBy(depart), session.draft.value.time)
  }

  @Test
  fun `un lieu favori devient la destination de la recherche`() = runTest(scheduler) {
    val viewModel = viewModel()
    viewModel.onSearchPlace(address("Gare du Nord"))

    assertEquals("Gare du Nord", session.draft.value.to?.name)
    assertNull(session.draft.value.from)
  }

  @Test
  fun `tout effacer vide l historique, une fois la confirmation donnee`() = runTest(scheduler) {
    val viewModel = viewModel()
    history.record(address("Bastille"), address("Nation"), TimeChoice.Now)
    advanceUntilIdle()

    viewModel.onOpenDialog(FavoritesDialog.ClearHistory)
    assertEquals(FavoritesDialog.ClearHistory, viewModel.uiState.value.dialog)

    viewModel.onClearHistory()
    advanceUntilIdle()

    assertEquals(1, history.cleared)
    assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
    assertEquals(FavoritesMessage.HISTORY_CLEARED, viewModel.uiState.value.message)
  }

  @Test
  fun `l ecran ne reimplemente pas la bascule d historique, il la reflete`() = runTest(scheduler) {
    val preferences = FakePreferencesRepository(DisplayPreferences(historyEnabled = false))
    val viewModel = FavoritesViewModel(
      favorites = favorites,
      history = history,
      geocode = geocode,
      trips = FakeStopsTripRepository(emptyMap()),
      session = session,
      preferences = preferences,
      now = { NOW },
    )
    advanceUntilIdle()

    assertTrue(!viewModel.uiState.value.historyEnabled)
  }

  @Test
  fun `un echec d ecriture se dit, sans laisser croire que c est enregistre`() = runTest(scheduler) {
    favorites.failing = true
    val viewModel = viewModel()
    viewModel.onOpenPicker(PickerTarget.Place)
    viewModel.onPickerSelected(address("Place de la Bastille"))
    advanceUntilIdle()

    assertEquals(FavoritesMessage.FAILED, viewModel.uiState.value.message)
    assertTrue(viewModel.uiState.value.places.isEmpty())
  }

  @Test
  fun `un choix d arret ne propose que des arrets`() {
    val suggestions = AutocompleteState.Suggestions(
      listOf(
        stop("de:06:1234", "Gare de Lyon"),
        address("12 rue des Lilas"),
      ),
    )

    val filtered = FavoritesViewModel.stopSuggestions(suggestions) as AutocompleteState.Suggestions

    // Enregistrer une adresse comme arrêt donnerait un favori dont les départs n'existent pas.
    assertEquals(listOf(PlaceKind.STOP), filtered.locations.map { it.kind })
  }

  @Test
  fun `seul un refus du serveur met l identifiant en cause`() {
    assertEquals(StopRecognition.KNOWN, stopRecognition(null))
    assertEquals(StopRecognition.UNRECOGNIZED, stopRecognition(EscaleError.BadRequest(serverMessage = null)))
    assertEquals(StopRecognition.UNKNOWN, stopRecognition(EscaleError.NoNetwork))
    assertEquals(StopRecognition.UNKNOWN, stopRecognition(EscaleError.Timeout))
    // Un 404 devient `ApiVersionTooOld` (docs/architecture.md § 7) : le prendre pour un arrêt
    // inconnu signalerait tous les favoris d'un coup sur un serveur trop vieux.
    assertEquals(StopRecognition.UNKNOWN, stopRecognition(EscaleError.ApiVersionTooOld(endpoint = "/stoptimes")))
  }

  private companion object {
    val NOW: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}
