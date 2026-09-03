package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapLoadRules
import io.github.mgdx.escale.core.geo.MapViewport
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.FakeServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Les stations et véhicules en libre-service sur la carte : les règles de SPEC.md § 5.7, mesurées
 * en **requêtes réellement parties** vers `/api/v1/rentals` et non en pixels.
 *
 * Le point qui distingue ce lot de celui des arrêts est le **seuil** : le tableau du § 5.7 ne fait
 * apparaître les stations qu'au zoom 13, là où les gares apparaissent au zoom 11. Un zoom 12 doit
 * donc charger des arrêts et **aucune** station, et c'est ce que les deux compteurs vérifient
 * côte à côte.
 *
 * Aucune coordonnée n'est journalisée nulle part dans ce parcours, et ces cas ne le vérifient pas
 * autrement qu'en n'ayant aucun journal à lire : le code du lot n'appelle pas `Log`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapRentalsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val servers = FakeServerRepository()
  private val styles = FakeStyleSource()
  private val cameras = FakeCameraMemory()
  private val locations = FakeLocationSource()
  private val maps = FakeMapRepository()
  private val geocode = FakeGeocodeRepository()
  private val selection = MapSelection()
  private val journeys = SelectedJourneyStore()
  private val stops = FakeStopsRepository()
  private val rentals = FakeRentalsRepository()
  private val preferences = FakePreferencesRepository()
  private val departures = StopDepartureRequests()

  /** Une emprise de travail, et une emprise strictement incluse dedans. */
  private val wide = BoundingBox(min = LatLon(48.80, 2.30), max = LatLon(48.90, 2.40))
  private val nudged = BoundingBox(min = LatLon(48.801, 2.301), max = LatLon(48.899, 2.399))
  private val elsewhere = BoundingBox(min = LatLon(45.70, 4.80), max = LatLon(45.80, 4.90))

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
    selectedJourneys = journeys,
    departureRequests = departures,
    computeDispatcher = UnconfinedTestDispatcher(),
  )

  /** L'appui sur un marqueur, par le chemin exact qu'emprunte le canevas : [MapRentalActions]. */
  private fun MapViewModel.clickRental(rental: SelectedRental) = uiState.value.rentalActions.onRentalClick(rental)

  private fun MapViewModel.dismissRental() = uiState.value.rentalActions.onDismissRental()

  private fun MapViewModel.idleAt(area: BoundingBox, zoom: Double) {
    onCameraIdle(MapViewport(visibleArea = area, zoom = zoom), MapCamera(center = LatLon(48.85, 2.35), zoom = zoom))
  }

  /** Laisse passer l'anti-rebond de 300 ms de la règle 1, et rien de plus. */
  private fun TestScope.settle() {
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS + 1)
    advanceUntilIdle()
  }

  // --- Le seuil du zoom 13 : aucune requête en deçà ---------------------------------------------

  @Test
  fun `sous le zoom onze, ni arret ni libre-service`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 10.9)
    settle()

    assertTrue(stops.requests.isEmpty())
    assertTrue(rentals.requests.isEmpty())
  }

  @Test
  fun `au zoom douze, les arrets partent mais pas le libre-service`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 12.0)
    settle()

    assertEquals("les gares sont demandées dès le zoom 11", 1, stops.requests.size)
    assertTrue(
      "SPEC.md § 5.7 : les stations en libre-service n'apparaissent qu'au zoom 13",
      rentals.requests.isEmpty(),
    )
  }

  @Test
  fun `au zoom treize, le libre-service est demande`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()

    assertEquals(1, rentals.requests.size)
  }

  @Test
  fun `l'emprise demandee est celle de l'ecran elargie de trente pour cent`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()

    assertEquals(wide.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), rentals.requests.single())
  }

  @Test
  fun `un deplacement suivi d'un autre n'emet qu'une requete, la derniere`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS / 2)
    model.idleAt(elsewhere, zoom = 14.0)
    settle()

    assertEquals(1, rentals.requests.size)
    assertEquals(elsewhere.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), rentals.requests.single())
  }

  @Test
  fun `un petit deplacement retombe dans la zone deja chargee et n'emet rien`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()
    model.idleAt(nudged, zoom = 14.0)
    settle()

    assertEquals("l'emprise élargie de 30 % couvre déjà l'écran déplacé", 1, rentals.requests.size)
  }

  // --- Règle 5 : descendre d'un palier n'émet rien -----------------------------------------------

  @Test
  fun `franchir un seuil de zoom vers le bas ne declenche aucune requete`() = runTest {
    val model = viewModel()
    // Zoom 16 : stations et véhicules isolés sont chargés d'un coup.
    model.idleAt(wide, zoom = 16.0)
    settle()
    assertEquals(1, rentals.requests.size)

    // 16 → 14 : les véhicules isolés disparaissent, mais c'est le `minzoom` de leur couche qui les
    // masque. Rien ne repart sur le réseau (SPEC.md § 5.7, règle 5).
    model.idleAt(nudged, zoom = 14.0)
    settle()
    assertEquals(1, rentals.requests.size)

    // 14 → 12 : sous le seuil du libre-service, toujours rien.
    model.idleAt(nudged, zoom = 12.0)
    settle()
    assertEquals(1, rentals.requests.size)

    // 12 → 10 : sous le seuil des arrêts eux-mêmes, toujours rien.
    model.idleAt(nudged, zoom = 10.0)
    settle()
    assertEquals(1, rentals.requests.size)
  }

  @Test
  fun `passer du zoom quatorze au zoom seize ne redemande rien de plus qu'une fois`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()
    // Monter au palier des véhicules isolés sur une emprise déjà couverte : `/api/v1/rentals` ne
    // prend aucun filtre de palier, la réponse précédente contenait déjà les véhicules. Une
    // seconde requête est émise parce que le palier a changé, mais pas une de plus en redescendant.
    model.idleAt(nudged, zoom = 16.0)
    settle()
    val afterZoomIn = rentals.requests.size

    model.idleAt(nudged, zoom = 14.0)
    settle()
    assertEquals(afterZoomIn, rentals.requests.size)
  }

  // --- Règle 2 : annulation de la requête en vol --------------------------------------------------

  @Test
  fun `une requete en vol est abandonnee des que la camera rebouge`() = runTest {
    rentals.delayMillis = 5_000
    rentals.availabilities = listOf(station("velib-1"))
    val model = viewModel()

    model.idleAt(wide, zoom = 14.0)
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS + 1)
    assertEquals(1, rentals.requests.size)
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.rentalStationsGeoJson)

    rentals.delayMillis = 0
    rentals.availabilities = listOf(station("velib-2"))
    model.idleAt(elsewhere, zoom = 14.0)
    settle()

    assertEquals(2, rentals.requests.size)
    // Seule la seconde réponse a été posée ; la première a été abandonnée en vol.
    val posed = model.uiState.value.rentalStationsGeoJson
    assertTrue(posed.contains("velib-2"))
    assertTrue(!posed.contains("velib-1"))
  }

  // --- Règles 6, 7 et 8 : ce qui est posé, et sur quelle source -----------------------------------

  @Test
  fun `stations et vehicules isoles vont sur des sources distinctes`() = runTest {
    rentals.availabilities = listOf(station("velib-1"), freeFloating("trott-1"))
    val model = viewModel()
    model.idleAt(wide, zoom = 16.0)
    settle()

    val state = model.uiState.value
    assertTrue(state.rentalStationsGeoJson.contains("velib-1"))
    assertTrue(!state.rentalStationsGeoJson.contains("trott-1"))
    assertTrue(state.rentalVehiclesGeoJson.contains("trott-1"))
    assertTrue(!state.rentalVehiclesGeoJson.contains("velib-1"))
  }

  @Test
  fun `une famille absente de la reponse recoit une collection vide`() = runTest {
    rentals.availabilities = listOf(station("velib-1"))
    val model = viewModel()
    model.idleAt(wide, zoom = 16.0)
    settle()

    assertEquals(MapGeoJson.EMPTY, model.uiState.value.rentalVehiclesGeoJson)
  }

  @Test
  fun `en deca de deux cents points, chaque station garde son dessin`() = runTest {
    rentals.availabilities = manyStations(MapLoadRules.CLUSTER_THRESHOLD)
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()

    val state = model.uiState.value
    assertTrue(state.rentalStationsGeoJson.contains("\"Point\""))
    assertEquals(MapGeoJson.EMPTY, state.clusteredRentalStationsGeoJson)
  }

  @Test
  fun `au-dela de deux cents points, la source regroupante prend le relais`() = runTest {
    rentals.availabilities = manyStations(MapLoadRules.CLUSTER_THRESHOLD + 1)
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()

    val state = model.uiState.value
    assertEquals(MapGeoJson.EMPTY, state.rentalStationsGeoJson)
    assertTrue(state.clusteredRentalStationsGeoJson.contains("\"Point\""))
  }

  @Test
  fun `le seuil de regroupement s'applique par famille`() = runTest {
    // Deux cent une stations et un seul véhicule : les stations se regroupent, le véhicule non.
    rentals.availabilities = manyStations(MapLoadRules.CLUSTER_THRESHOLD + 1) + freeFloating("trott-1")
    val model = viewModel()
    model.idleAt(wide, zoom = 16.0)
    settle()

    val state = model.uiState.value
    assertEquals(MapGeoJson.EMPTY, state.rentalStationsGeoJson)
    assertTrue(state.clusteredRentalStationsGeoJson.contains("\"Point\""))
    assertTrue(state.rentalVehiclesGeoJson.contains("trott-1"))
    assertEquals(MapGeoJson.EMPTY, state.clusteredRentalVehiclesGeoJson)
  }

  @Test
  fun `une reponse en erreur laisse les marqueurs precedents en place`() = runTest {
    rentals.availabilities = listOf(station("velib-1"))
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()
    val posed = model.uiState.value.rentalStationsGeoJson
    assertTrue(posed.contains("velib-1"))

    rentals.failure = EscaleError.NoNetwork
    model.idleAt(elsewhere, zoom = 14.0)
    settle()

    assertEquals(2, rentals.requests.size)
    assertEquals(posed, model.uiState.value.rentalStationsGeoJson)
  }

  // --- Le réglage de visibilité, indépendant du zoom (SPEC.md § 5.7) ------------------------------

  @Test
  fun `libre-service masque par reglage, aucune requete n'est emise`() = runTest {
    preferences.updateDisplayPreferences(DisplayPreferences(showRentals = false))
    val model = viewModel()
    model.idleAt(wide, zoom = 16.0)
    settle()

    assertTrue(rentals.requests.isEmpty())
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.rentalStationsGeoJson)
    // Le réglage est indépendant de celui des arrêts, qui continuent de se charger.
    assertEquals(1, stops.requests.size)
  }

  @Test
  fun `rallumer le libre-service recharge l'ecran courant sans attendre un geste`() = runTest {
    preferences.updateDisplayPreferences(DisplayPreferences(showRentals = false))
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()
    assertTrue(rentals.requests.isEmpty())

    preferences.updateDisplayPreferences(DisplayPreferences(showRentals = true))
    settle()
    assertEquals(1, rentals.requests.size)
  }

  @Test
  fun `decocher le libre-service efface les quatre sources et referme l'infobulle`() = runTest {
    rentals.availabilities = listOf(station("velib-1"), freeFloating("trott-1"))
    val model = viewModel()
    model.idleAt(wide, zoom = 16.0)
    settle()
    model.clickRental(selectedStation())
    assertNotNull(model.uiState.value.selectedRental)

    preferences.updateDisplayPreferences(DisplayPreferences(showRentals = false))
    settle()

    val state = model.uiState.value
    assertEquals(MapGeoJson.EMPTY, state.rentalStationsGeoJson)
    assertEquals(MapGeoJson.EMPTY, state.clusteredRentalStationsGeoJson)
    assertEquals(MapGeoJson.EMPTY, state.rentalVehiclesGeoJson)
    assertEquals(MapGeoJson.EMPTY, state.clusteredRentalVehiclesGeoJson)
    assertNull(state.selectedRental)
  }

  @Test
  fun `masquer les arrets ne masque pas le libre-service`() = runTest {
    preferences.updateDisplayPreferences(DisplayPreferences(showStops = false))
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()

    assertTrue(stops.requests.isEmpty())
    assertEquals(1, rentals.requests.size)
  }

  // --- L'infobulle (SPEC.md § 5.7) ----------------------------------------------------------------

  @Test
  fun `l'infobulle s'ouvre sans le moindre appel reseau`() = runTest {
    val model = viewModel()
    model.clickRental(selectedStation())

    val selected = checkNotNull(model.uiState.value.selectedRental)
    assertEquals("Hôtel de Ville", selected.name)
    assertEquals(7, selected.vehiclesAvailable)
    assertEquals(4, selected.docksAvailable)
    assertTrue(rentals.requests.isEmpty())
  }

  @Test
  fun `ouvrir une station referme l'infobulle d'arret, et reciproquement`() = runTest {
    val model = viewModel()
    model.onStopClick(SelectedStop(id = "gare", name = "Gare", mode = TransitMode.RAIL))
    assertNotNull(model.uiState.value.selectedStop)

    model.clickRental(selectedStation())
    assertNull("deux fiches superposées seraient illisibles à 200 %", model.uiState.value.selectedStop)
    assertNotNull(model.uiState.value.selectedRental)

    model.onStopClick(SelectedStop(id = "gare", name = "Gare", mode = TransitMode.RAIL))
    assertNull(model.uiState.value.selectedRental)
  }

  @Test
  fun `refermer l'infobulle n'efface pas les marqueurs`() = runTest {
    rentals.availabilities = listOf(station("velib-1"))
    val model = viewModel()
    model.idleAt(wide, zoom = 14.0)
    settle()
    model.clickRental(selectedStation())

    model.dismissRental()
    assertNull(model.uiState.value.selectedRental)
    assertTrue(model.uiState.value.rentalStationsGeoJson.contains("velib-1"))
  }

  private fun selectedStation() = SelectedRental(
    id = "velib-1",
    name = "Hôtel de Ville",
    kind = RentalMarkerKind.STATION,
    icon = RentalIcon.BICYCLE,
    vehiclesAvailable = 7,
    docksAvailable = 4,
    isRenting = true,
    isReturning = true,
  )

  private fun station(id: String) = RentalAvailability(
    stationId = id,
    name = id,
    coordinates = LatLon(48.85, 2.35),
    numVehiclesAvailable = 5,
    vehicleDocksAvailable = mapOf("velo" to 3),
    formFactors = listOf(RentalFormFactor.BICYCLE),
    retrievedAt = RETRIEVED_AT,
  )

  /** Un véhicule isolé : sans nom et sans borne, comme un `RentalVehicle` de l'OpenAPI. */
  private fun freeFloating(id: String) = RentalAvailability(
    stationId = id,
    name = "",
    coordinates = LatLon(48.86, 2.36),
    numVehiclesAvailable = 1,
    formFactors = listOf(RentalFormFactor.SCOOTER_STANDING),
    retrievedAt = RETRIEVED_AT,
  )

  private fun manyStations(count: Int) = List(count) { index ->
    RentalAvailability(
      stationId = "station-$index",
      name = "Station $index",
      coordinates = LatLon(48.85 + index / 10_000.0, 2.35),
      numVehiclesAvailable = 2,
      vehicleDocksAvailable = mapOf("velo" to 1),
      retrievedAt = RETRIEVED_AT,
    )
  }

  private companion object {
    val RETRIEVED_AT: Instant = Instant.parse("2026-09-01T10:00:00Z")
  }
}
