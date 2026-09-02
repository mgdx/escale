package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapLoadRules
import io.github.mgdx.escale.core.geo.MapViewport
import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.FakeServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Les arrêts sur la carte : les neuf règles de fluidité de SPEC.md § 5.7, mesurées en **requêtes
 * réellement parties** et non en pixels.
 *
 * Aucune coordonnée n'est journalisée nulle part dans ce parcours, et ces cas ne le vérifient pas
 * autrement qu'en n'ayant aucun journal à lire : le code du lot n'appelle pas `Log`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapStopsViewModelTest {

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
    preferencesRepository = preferences,
    styles = styles,
    cameraStore = cameras,
    locationSource = locations,
    selection = selection,
    selectedJourneys = journeys,
    departureRequests = departures,
    computeDispatcher = UnconfinedTestDispatcher(),
  )

  private fun MapViewModel.idleAt(area: BoundingBox, zoom: Double) {
    onCameraIdle(MapViewport(visibleArea = area, zoom = zoom), MapCamera(center = LatLon(48.85, 2.35), zoom = zoom))
  }

  /** Laisse passer l'anti-rebond de 300 ms de la règle 1, et rien de plus. */
  private fun TestScope.settle() {
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS + 1)
    advanceUntilIdle()
  }

  // --- Règle 1 et § 7.9 : quand une requête part, et quand elle ne part pas -------------------

  @Test
  fun `sous le zoom 11, aucune requete n'est emise`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 10.9)
    settle()

    assertTrue("SPEC.md § 5.7 : « < 11 : aucune requête n'est émise »", stops.requests.isEmpty())
    assertNull(model.uiState.value.plannedRequest)
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.stopsGeoJson)
  }

  @Test
  fun `au zoom 11, seuls les modes ferres lourds sont demandes`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 11.0)
    settle()

    assertEquals(1, stops.requests.size)
    assertEquals(TransitMode.HEAVY_RAIL_MODES, stops.requests.single().second)
    assertEquals(ZoomTier.MAJOR_STATIONS, model.uiState.value.plannedRequest?.tier)
  }

  @Test
  fun `au zoom 13, les modes de surface s'ajoutent sans rien retirer`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()

    val modes = stops.requests.single().second
    assertTrue(modes.containsAll(TransitMode.HEAVY_RAIL_MODES))
    assertTrue(modes.contains(TransitMode.BUS))
    assertTrue(modes.contains(TransitMode.TRAM))
  }

  @Test
  fun `l'emprise demandee est celle de l'ecran elargie de trente pour cent`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 12.0)
    settle()

    val asked = stops.requests.single().first
    assertTrue(asked.min.lat < wide.min.lat)
    assertTrue(asked.max.lat > wide.max.lat)
    assertEquals(wide.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), asked)
  }

  @Test
  fun `un deplacement suivi d'un autre n'emet qu'une requete, la derniere`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 12.0)
    // Trop tôt pour que la première parte : la caméra a rebougé avant les 300 ms.
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS / 2)
    model.idleAt(elsewhere, zoom = 12.0)
    settle()

    assertEquals(1, stops.requests.size)
    assertEquals(elsewhere.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), stops.requests.single().first)
  }

  // --- Règle 3 et règle 5 : ce qui ne redéclenche rien ----------------------------------------

  @Test
  fun `un petit deplacement retombe dans la zone deja chargee et n'emet rien`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 12.0)
    settle()
    model.idleAt(nudged, zoom = 12.0)
    settle()

    assertEquals("l'emprise élargie de 30 % couvre déjà l'écran déplacé", 1, stops.requests.size)
  }

  @Test
  fun `franchir un seuil de zoom vers le bas ne declenche aucune requete`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 13.5)
    settle()
    assertEquals(1, stops.requests.size)

    // On redescend sous le seuil de 13 : les gares sont déjà chargées, il n'y a qu'à masquer la
    // couche des arrêts de surface (SPEC.md § 5.7, règle 5).
    model.idleAt(nudged, zoom = 11.5)
    settle()
    assertEquals(1, stops.requests.size)

    // Et sous le zoom 11, toujours rien.
    model.idleAt(nudged, zoom = 10.0)
    settle()
    assertEquals(1, stops.requests.size)
  }

  @Test
  fun `monter d'un palier demande les modes qui manquent`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 11.5)
    settle()
    model.idleAt(nudged, zoom = 13.5)
    settle()

    assertEquals(2, stops.requests.size)
    assertEquals(TransitMode.HEAVY_RAIL_MODES, stops.requests.first().second)
    assertTrue(stops.requests.last().second.contains(TransitMode.BUS))
  }

  // --- Règle 2 : annulation de la requête en vol ----------------------------------------------

  @Test
  fun `une requete en vol est abandonnee des que la camera rebouge`() = runTest {
    stops.delayMillis = 5_000
    stops.stops = listOf(stop("gare", TransitMode.RAIL))
    val model = viewModel()

    model.idleAt(wide, zoom = 12.0)
    // Juste assez pour que l'anti-rebond laisse partir la requête, pas assez pour qu'elle réponde.
    advanceTimeBy(MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS + 1)
    assertEquals(1, stops.requests.size)
    // La réponse n'est pas encore arrivée : rien n'est posé.
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.stopsGeoJson)

    // La caméra rebouge : `collectLatest` annule la coroutine de la première, donc son appel.
    stops.delayMillis = 0
    model.idleAt(elsewhere, zoom = 12.0)
    settle()

    assertEquals(2, stops.requests.size)
    // Seule la seconde réponse a été posée ; la première a été abandonnée en vol.
    assertEquals(elsewhere.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), model.uiState.value.plannedRequest?.area)
  }

  // --- Règles 6 et 7 : ce qui est posé, et sur quelle source ----------------------------------

  @Test
  fun `en deca de deux cents points, chaque arret garde son dessin`() = runTest {
    stops.stops = manyStops(MapLoadRules.CLUSTER_THRESHOLD)
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()

    val state = model.uiState.value
    assertTrue(state.stopsGeoJson.contains("\"Point\""))
    assertEquals(MapGeoJson.EMPTY, state.clusteredStopsGeoJson)
  }

  @Test
  fun `au-dela de deux cents points, la source regroupante prend le relais`() = runTest {
    stops.stops = manyStops(MapLoadRules.CLUSTER_THRESHOLD + 1)
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()

    val state = model.uiState.value
    assertEquals(MapGeoJson.EMPTY, state.stopsGeoJson)
    assertTrue(state.clusteredStopsGeoJson.contains("\"Point\""))
  }

  @Test
  fun `chaque arret porte son palier, son dessin et son identifiant`() = runTest {
    stops.stops = listOf(stop("gare", TransitMode.RAIL), stop("arret", TransitMode.BUS))
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()

    val geoJson = model.uiState.value.stopsGeoJson
    assertTrue(geoJson.contains(ZoomTier.MAJOR_STATIONS.name))
    assertTrue(geoJson.contains(ZoomTier.ALL_STOPS.name))
    assertTrue(geoJson.contains(StopIcon.RAIL.imageId))
    assertTrue(geoJson.contains(StopIcon.BUS.imageId))
    assertTrue(geoJson.contains("gare"))
  }

  @Test
  fun `une reponse en erreur laisse les marqueurs precedents en place`() = runTest {
    stops.stops = listOf(stop("gare", TransitMode.RAIL))
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()
    val posed = model.uiState.value.stopsGeoJson
    assertTrue(posed.contains("gare"))

    // Réseau coupé sur l'emprise suivante : une carte qui se vide parce que le réseau a hoqueté
    // est pire qu'une carte un peu en retard (SPEC.md § 8).
    stops.failure = EscaleError.NoNetwork
    model.idleAt(elsewhere, zoom = 13.0)
    settle()

    assertEquals(2, stops.requests.size)
    assertEquals(posed, model.uiState.value.stopsGeoJson)
  }

  // --- Le réglage de visibilité, indépendant du zoom (SPEC.md § 5.7) --------------------------

  @Test
  fun `arrets masques par reglage, aucune requete n'est emise`() = runTest {
    preferences.updateDisplayPreferences(DisplayPreferences(showStops = false))
    val model = viewModel()
    model.idleAt(wide, zoom = 15.0)
    settle()

    assertTrue(stops.requests.isEmpty())
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.stopsGeoJson)
  }

  @Test
  fun `rallumer les arrets recharge l'ecran courant sans attendre un geste`() = runTest {
    preferences.updateDisplayPreferences(DisplayPreferences(showStops = false))
    val model = viewModel()
    model.idleAt(wide, zoom = 15.0)
    settle()
    assertTrue(stops.requests.isEmpty())

    preferences.updateDisplayPreferences(DisplayPreferences(showStops = true))
    settle()
    assertEquals(1, stops.requests.size)
  }

  @Test
  fun `decocher les arrets efface les deux sources et referme l'infobulle`() = runTest {
    stops.stops = listOf(stop("gare", TransitMode.RAIL))
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()
    model.onStopClick(SelectedStop(id = "gare", name = "Gare", mode = TransitMode.RAIL))

    preferences.updateDisplayPreferences(DisplayPreferences(showStops = false))
    settle()

    val state = model.uiState.value
    assertEquals(MapGeoJson.EMPTY, state.stopsGeoJson)
    assertEquals(MapGeoJson.EMPTY, state.clusteredStopsGeoJson)
    assertNull(state.selectedStop)
  }

  @Test
  fun `le reglage des points d'interet n'emet aucune requete`() = runTest {
    val model = viewModel()
    assertTrue(model.uiState.value.pointsOfInterestVisible)

    preferences.updateDisplayPreferences(DisplayPreferences(showPointsOfInterest = false))
    settle()

    assertFalse(model.uiState.value.pointsOfInterestVisible)
    // Les points d'intérêt sont déjà dans les tuiles : les masquer ne coûte rien au réseau.
    assertTrue(stops.requests.isEmpty())
  }

  // --- L'infobulle (SPEC.md § 5.7) -------------------------------------------------------------

  @Test
  fun `l'infobulle s'ouvre aussitot, puis les lignes arrivent`() = runTest {
    stops.detail = Outcome.Success(
      Stop(
        id = "gare",
        name = "Châtelet",
        coordinates = LatLon(48.85, 2.35),
        lines = listOf(StopLine("c1", "14", "", TransitMode.SUBWAY, "RATP")),
      ),
    )
    val model = viewModel()
    model.onStopClick(SelectedStop(id = "gare", name = "Châtelet", mode = TransitMode.SUBWAY))
    advanceUntilIdle()

    val selected = checkNotNull(model.uiState.value.selectedStop)
    assertEquals("Châtelet", selected.name)
    assertFalse(selected.linesLoading)
    assertFalse(selected.linesFailed)
    assertEquals(listOf("14"), selected.lines.map { it.label })
    assertEquals(listOf("gare"), stops.detailRequests)
  }

  @Test
  fun `des lignes illisibles ne font pas disparaitre l'arret`() = runTest {
    stops.detail = Outcome.Failure(EscaleError.NoNetwork)
    val model = viewModel()
    model.onStopClick(SelectedStop(id = "gare", name = "Châtelet", mode = TransitMode.SUBWAY))
    advanceUntilIdle()

    val selected = checkNotNull(model.uiState.value.selectedStop)
    assertEquals("Châtelet", selected.name)
    assertTrue(selected.linesFailed)
    assertFalse(selected.linesLoading)
  }

  @Test
  fun `le bouton des prochains departs depose une demande pour le jalon 9`() = runTest {
    val model = viewModel()
    model.onStopDepartures(SelectedStop(id = "gare", name = "Châtelet", mode = TransitMode.SUBWAY))

    val request = checkNotNull(departures.request.value)
    assertEquals("gare", request.stopId)
    assertEquals("Châtelet", request.stopName)

    departures.consume()
    assertNull(departures.request.value)
  }

  @Test
  fun `l'appui sur un groupe rapproche la camera sans rien ouvrir`() = runTest {
    val model = viewModel()
    model.idleAt(wide, zoom = 12.0)
    settle()
    model.onClusterClick(LatLon(48.85, 2.35))

    val target = checkNotNull(model.uiState.value.cameraTarget)
    assertTrue(target.animated)
    val goal = target.goal
    assertTrue(goal is CameraGoal.Center)
    assertTrue("le groupe doit éclater", (goal as CameraGoal.Center).camera.zoom > 12.0)
    assertNull(model.uiState.value.selectedStop)
  }

  @Test
  fun `refermer l'infobulle n'efface pas les marqueurs`() = runTest {
    stops.stops = listOf(stop("gare", TransitMode.RAIL))
    val model = viewModel()
    model.idleAt(wide, zoom = 13.0)
    settle()
    model.onStopClick(SelectedStop(id = "gare", name = "Gare", mode = TransitMode.RAIL))
    advanceUntilIdle()
    assertNotNull(model.uiState.value.selectedStop)

    model.onDismissStop()
    assertNull(model.uiState.value.selectedStop)
    assertTrue(model.uiState.value.stopsGeoJson.contains("gare"))
  }

  private fun stop(id: String, mode: TransitMode) =
    Stop(id = id, name = id, coordinates = LatLon(48.85, 2.35), modes = listOf(mode))

  private fun manyStops(count: Int) = List(count) { index ->
    Stop(
      id = "arret-$index",
      name = "Arrêt $index",
      coordinates = LatLon(48.85 + index / 10_000.0, 2.35),
      modes = listOf(TransitMode.BUS),
    )
  }
}
