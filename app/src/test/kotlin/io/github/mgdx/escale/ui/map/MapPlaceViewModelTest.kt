package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiComplement
import io.github.mgdx.escale.core.model.PoiDetailKey
import io.github.mgdx.escale.core.model.PoiTypeKey
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.FakeServerRepository
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La fiche d'un point d'intérêt, et la seule requête qu'elle provoque (SPEC.md § 7, règle 11).
 *
 * « Une requête de géocodage inverse, à l'ouverture de la fiche, **une par fiche**, annulée à sa
 * fermeture. » Ce sont les compteurs du faux dépôt, et non l'état affiché, qui le démontrent : une
 * fiche qui se referme peut très bien avoir l'air correcte tout en laissant un appel en vol.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapPlaceViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val servers = FakeServerRepository()
  private val styles = FakeStyleSource()
  private val cameras = FakeCameraMemory()
  private val locations = FakeLocationSource()
  private val maps = FakeMapRepository()
  private val geocode = FakeGeocodeRepository()
  private val selection = MapSelection()
  private val searchSession = SearchSession()
  private val journeys = SelectedJourneyStore()
  private val stops = FakeStopsRepository()
  private val rentals = FakeRentalsRepository()
  private val preferences = FakePreferencesRepository()
  private val departures = StopDepartureRequests()

  private val bakery = SelectedPlace(
    point = LatLon(48.8566, 2.3522),
    name = "Au bon pain",
    category = PoiCategory.FOOD,
    typeKey = PoiTypeKey.BAKERY,
    houseNumber = "12",
  )

  private val address = Location(
    id = null,
    name = "12 Rue de Rivoli",
    description = "Paris",
    coordinates = LatLon(48.8566, 2.3522),
    kind = PlaceKind.ADDRESS,
  )

  private fun viewModel() = MapViewModel(
    serverRepository = servers,
    mapRepository = maps,
    geocodeRepository = geocode,
    stopsRepository = stops,
    rentalsRepository = rentals,
    preferencesRepository = preferences,
    styles = styles,
    cameraStore = cameras,
    locationSource = locations,
    selection = selection,
    searchSession = searchSession,
    selectedJourneys = journeys,
    departureRequests = departures,
    computeDispatcher = UnconfinedTestDispatcher(),
  )

  // --- L'ouverture et sa requête ----------------------------------------------------------------

  @Test
  fun `ouvrir une fiche declenche une requete d'adresse, et une seule`() = runTest {
    geocode.addressLabel = address
    val model = viewModel()

    model.uiState.value.placeActions.onPlaceClick(bakery)
    advanceUntilIdle()

    assertEquals(listOf(bakery.point), geocode.addressed)
    assertTrue("l'appui long garde sa propre voie, la fiche ne l'emprunte pas", geocode.reversed.isEmpty())
    val place = model.uiState.value.selectedPlace
    assertNotNull(place)
    assertEquals(address, place!!.address)
    assertEquals(false, place.addressLoading)
  }

  @Test
  fun `la fiche s'affiche avant l'adresse, avec le numero de la tuile`() = runTest {
    geocode.delayMillis = 1_000
    val model = viewModel()

    model.uiState.value.placeActions.onPlaceClick(bakery)

    val place = model.uiState.value.selectedPlace
    assertNotNull(place)
    assertTrue(place!!.addressLoading)
    assertNull(place.address)
    assertEquals("12", place.houseNumber)
  }

  @Test
  fun `fermer la fiche avant la reponse annule la requete`() = runTest {
    geocode.delayMillis = 1_000
    val model = viewModel()

    model.uiState.value.placeActions.onPlaceClick(bakery)
    model.uiState.value.placeActions.onDismissPlace()
    advanceUntilIdle()

    assertNull(model.uiState.value.selectedPlace)
    // Partie, mais jamais revenue : c'est exactement ce que « annulée à sa fermeture » veut dire.
    assertEquals(1, geocode.addressed.size)
    assertEquals(0, geocode.completed)
  }

  @Test
  fun `ouvrir une seconde fiche abandonne la requete de la premiere`() = runTest {
    geocode.delayMillis = 1_000
    val model = viewModel()
    val other = bakery.copy(point = LatLon(48.86, 2.36), name = "Chez Marcel")

    model.uiState.value.placeActions.onPlaceClick(bakery)
    model.uiState.value.placeActions.onPlaceClick(other)
    advanceUntilIdle()

    assertEquals(listOf(bakery.point, other.point), geocode.addressed)
    assertEquals("une fiche, une requête servie", 1, geocode.completed)
    assertEquals(other.point, model.uiState.value.selectedPlace?.point)
  }

  // --- La priorité entre fiches -----------------------------------------------------------------

  @Test
  fun `un appui sur un arret referme la fiche de lieu, et l'inverse`() = runTest {
    val model = viewModel()

    model.uiState.value.placeActions.onPlaceClick(bakery)
    advanceUntilIdle()
    model.onStopClick(SelectedStop(id = "a", name = "Châtelet", mode = TransitMode.BUS))

    assertNull("deux fiches superposées seraient illisibles", model.uiState.value.selectedPlace)

    model.uiState.value.placeActions.onPlaceClick(bakery)
    advanceUntilIdle()

    assertNull(model.uiState.value.selectedStop)
    assertNotNull(model.uiState.value.selectedPlace)
  }

  @Test
  fun `eteindre la categorie d'une fiche ouverte la referme`() = runTest {
    val model = viewModel()
    // Un repère, allumé au premier lancement : c'est la seule façon d'avoir une fiche ouverte sur
    // une catégorie qu'on puisse ensuite éteindre.
    val theatre = bakery.copy(
      name = "Théâtre du Châtelet",
      category = PoiCategory.CULTURE,
      typeKey = PoiTypeKey.THEATRE,
    )
    model.uiState.value.placeActions.onPlaceClick(theatre)
    advanceUntilIdle()

    preferences.updateDisplayPreferences(
      DisplayPreferences(visiblePoiCategories = PoiCategory.DEFAULT_VISIBLE - PoiCategory.CULTURE),
    )
    advanceUntilIdle()

    assertNull("la carte a effacé le pictogramme, la fiche ne peut pas rester", model.uiState.value.selectedPlace)
  }

  @Test
  fun `rallumer une categorie ne rouvre aucune fiche`() = runTest {
    val model = viewModel()

    preferences.updateDisplayPreferences(DisplayPreferences(visiblePoiCategories = PoiCategory.DEFAULT_VISIBLE))
    advanceUntilIdle()

    assertNull(model.uiState.value.selectedPlace)
    assertTrue("un réglage de couches ne coûte aucun octet de réseau", geocode.addressed.isEmpty())
  }

  // --- « Partir d'ici » / « Aller ici » ----------------------------------------------------------

  @Test
  fun `choisir une destination depose le point, avec l'adresse deja rendue`() = runTest {
    geocode.addressLabel = address
    val model = viewModel()
    model.uiState.value.placeActions.onPlaceClick(bakery)
    advanceUntilIdle()

    model.uiState.value.placeActions.onPick(MapPickPurpose.DESTINATION)

    val pick = selection.pick.value
    assertEquals(MapPickPurpose.DESTINATION, pick?.purpose)
    assertEquals(bakery.point, pick?.point)
    assertEquals(address, pick?.label)
    assertNull("la fiche se referme derrière le choix", model.uiState.value.selectedPlace)
  }

  @Test
  fun `choisir avant l'arrivee de l'adresse n'emet aucune seconde requete`() = runTest {
    geocode.delayMillis = 1_000
    val model = viewModel()
    model.uiState.value.placeActions.onPlaceClick(bakery)

    model.uiState.value.placeActions.onPick(MapPickPurpose.DEPARTURE)
    advanceUntilIdle()

    assertEquals(MapPickPurpose.DEPARTURE, selection.pick.value?.purpose)
    assertNull("un point sans nom reste un point utilisable", selection.pick.value?.label)
    assertEquals("une fiche vaut une requête, pas deux", 1, geocode.addressed.size)
  }

  @Test
  fun `sans fiche ouverte, aucun choix ne peut etre depose`() = runTest {
    val model = viewModel()

    model.uiState.value.placeActions.onPick(MapPickPurpose.DESTINATION)

    assertNull(selection.pick.value)
  }

  // --- Le complément de type --------------------------------------------------------------------

  @Test
  fun `le complement de type voyage avec la fiche`() = runTest {
    val model = viewModel()
    val restaurant = bakery.copy(
      category = PoiCategory.DINING,
      typeKey = PoiTypeKey.RESTAURANT,
      complement = PoiComplement.Named(PoiDetailKey.ITALIAN),
    )

    model.uiState.value.placeActions.onPlaceClick(restaurant)
    advanceUntilIdle()

    assertEquals(PoiComplement.Named(PoiDetailKey.ITALIAN), model.uiState.value.selectedPlace?.complement)
  }
}
