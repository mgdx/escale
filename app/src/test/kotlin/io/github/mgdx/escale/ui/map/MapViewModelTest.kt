package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.FakeServerRepository
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * L'écran de carte, éprouvé sans Android : le `ViewModel` ne dépend que d'interfaces, et la seule
 * chose qu'il ne fait pas est de dessiner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val paris = LatLon(48.8566, 2.3522)
  private val lyon = LatLon(45.7640, 4.8357)

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

  // --- Cadrage initial, SPEC.md § 5.1 ------------------------------------------------------

  @Test
  fun `la caméra mémorisée passe avant tout le reste`() = runTest {
    val remembered = MapCamera(paris, zoom = 14.0)
    val model = MapViewModel(
      serverRepository = servers,
      mapRepository = maps,
      geocodeRepository = geocode,
      stopsRepository = stops,
      rentalsRepository = rentals,
      preferencesRepository = preferences,
      styles = styles,
      cameraStore = FakeCameraMemory(remembered),
      locationSource = FakeLocationSource(coarseGranted = true, lastKnown = lyon),
      selection = selection,
      searchSession = searchSession,
      selectedJourneys = journeys,
      departureRequests = departures,
      computeDispatcher = UnconfinedTestDispatcher(),
    )
    assertEquals(remembered, model.uiState.value.cameraTarget?.camera)
    // Le cadrage initial ne s'anime pas : il doit être en place avant la première image.
    assertFalse(checkNotNull(model.uiState.value.cameraTarget).animated)
    assertEquals(0, maps.calls)
  }

  @Test
  fun `à défaut, la position déjà connue sert, sans demander la moindre permission`() = runTest {
    locations.coarseGranted = true
    locations.lastKnown = lyon
    val model = viewModel()
    assertEquals(lyon, model.uiState.value.cameraTarget?.camera?.center)
    // Aucune demande de permission n'est émise au démarrage (SPEC.md § 5.1 et § 11).
    assertNull(model.uiState.value.permissionRequest)
    assertEquals(0, maps.calls)
  }

  @Test
  fun `à défaut encore, le serveur donne son cadrage`() = runTest {
    val fromServer = MapCamera(LatLon(45.18, 12.90), zoom = 4.0)
    maps.outcome = Outcome.Success(fromServer)
    val model = viewModel()
    assertEquals(fromServer, model.uiState.value.cameraTarget?.camera)
    assertEquals(1, maps.calls)
  }

  @Test
  fun `un serveur muet ne prive pas la carte d'un cadrage`() = runTest {
    val model = viewModel()
    assertEquals(MapViewModel.WORLD_CAMERA, model.uiState.value.cameraTarget?.camera)
  }

  @Test
  fun `un cadrage acquitté ne se rejoue pas`() = runTest {
    val model = viewModel()
    assertNotNull(model.uiState.value.cameraTarget)
    model.onCameraTargetApplied()
    assertNull(model.uiState.value.cameraTarget)
  }

  // --- Feuille de style, SPEC.md § 5.7 ------------------------------------------------------

  @Test
  fun `la feuille suit le serveur configuré et le thème`() = runTest {
    val model = viewModel()
    model.onThemeChanged(dark = true)
    assertEquals(ServerUrl.DEFAULT_BASE_URL to true, styles.tiledRequests.last())
    assertTrue(checkNotNull(model.uiState.value.styleJson).endsWith("sombre"))

    servers.save(ServerConfig(baseUrl = "https://motis.example", label = "essai"))
    assertEquals("https://motis.example" to true, styles.tiledRequests.last())
  }

  @Test
  fun `un serveur testé et sans tuiles reçoit un fond neutre et un message`() = runTest {
    servers.save(
      ServerConfig(
        baseUrl = "https://sans-tuiles.example",
        label = "sans tuiles",
        hasTiles = false,
        lastCheckedAt = Instant.EPOCH,
      ),
    )
    val model = viewModel()
    model.onThemeChanged(dark = false)
    assertTrue(styles.blankRequested)
    assertTrue(model.uiState.value.tilesUnavailable)
    // Aucun repli sur un fournisseur tiers : aucune feuille de tuiles n'est même demandée.
    assertTrue(styles.tiledRequests.isEmpty())
  }

  @Test
  fun `un serveur jamais testé garde le bénéfice du doute`() = runTest {
    val model = viewModel()
    model.onThemeChanged(dark = false)
    assertFalse(styles.blankRequested)
    assertFalse(model.uiState.value.tilesUnavailable)
  }

  // --- Bouton de position, SPEC.md § 5.1 ----------------------------------------------------

  @Test
  fun `le premier appui demande la permission approchée, jamais la précise`() = runTest {
    val model = viewModel()
    model.onLocateClick()
    assertEquals(LocationPermission.COARSE, model.uiState.value.permissionRequest?.permission)
    assertEquals(LocateState.UNKNOWN, model.uiState.value.locateState)
    assertEquals(0, locations.collected)
  }

  @Test
  fun `permission refusée, le bouton reste et une explication s'affiche`() = runTest {
    val model = viewModel()
    model.onLocateClick()
    model.onPermissionRequestLaunched()
    model.onPermissionResult(granted = false)

    assertTrue(model.uiState.value.permissionExplanationVisible)
    // Le bouton n'est pas retiré : il reste dans son état « position inconnue ».
    assertEquals(LocateState.UNKNOWN, model.uiState.value.locateState)
    assertEquals(0, locations.collected)

    model.onDismissPermissionExplanation()
    assertFalse(model.uiState.value.permissionExplanationVisible)
  }

  @Test
  fun `les trois états du bouton s'enchaînent centrage puis suivi puis arrêt`() = runTest {
    locations.coarseGranted = true
    locations.fineGranted = true
    val model = viewModel()

    model.onLocateClick()
    assertEquals(LocateState.CENTERED, model.uiState.value.locateState)

    model.onLocateClick()
    assertEquals(LocateState.FOLLOWING, model.uiState.value.locateState)

    model.onLocateClick()
    assertEquals(LocateState.CENTERED, model.uiState.value.locateState)
  }

  @Test
  fun `la permission précise n'est demandée qu'au passage en suivi, et une seule fois`() = runTest {
    locations.coarseGranted = true
    val model = viewModel()

    model.onLocateClick()
    assertNull(model.uiState.value.permissionRequest)

    model.onLocateClick()
    assertEquals(LocationPermission.FINE, model.uiState.value.permissionRequest?.permission)

    model.onPermissionRequestLaunched()
    model.onLocateClick()
    model.onLocateClick()
    assertNull(model.uiState.value.permissionRequest)
  }

  @Test
  fun `déplacer la carte du doigt arrête le suivi sans le désactiver`() = runTest {
    locations.coarseGranted = true
    locations.fineGranted = true
    val model = viewModel()
    model.onLocateClick()
    model.onLocateClick()
    assertEquals(LocateState.FOLLOWING, model.uiState.value.locateState)

    model.onUserMovedCamera()
    assertEquals(LocateState.CENTERED, model.uiState.value.locateState)
  }

  @Test
  fun `une position reçue alimente la source GeoJSON, jamais une vue superposée`() = runTest {
    locations.coarseGranted = true
    locations.fineGranted = true
    val model = viewModel()
    model.onLocateClick()
    locations.emitted.emit(paris)

    val geoJson = model.uiState.value.userLocationGeoJson
    assertTrue(geoJson.contains("\"Point\""))
    // GeoJSON ordonne longitude puis latitude.
    assertTrue(geoJson.contains("[${paris.lon},${paris.lat}]"))
  }

  // --- Appui long, SPEC.md § 5.1 ------------------------------------------------------------

  @Test
  fun `un appui long ouvre le menu et pose un point sur la carte`() = runTest {
    val model = viewModel()
    model.onMapLongClick(paris)

    assertEquals(paris, model.uiState.value.longPressPoint)
    assertTrue(model.uiState.value.pickedPointGeoJson.contains("\"Point\""))

    model.onDismissLongPress()
    assertNull(model.uiState.value.longPressPoint)
    assertEquals(MapGeoJson.EMPTY, model.uiState.value.pickedPointGeoJson)
  }

  @Test
  fun `choisir Partir d'ici dépose le point pour le lot recherche, libellé compris`() = runTest {
    val model = viewModel()
    model.onMapLongClick(paris)
    model.onPick(MapPickPurpose.DEPARTURE)

    val pick = checkNotNull(selection.pick.value)
    assertEquals(MapPickPurpose.DEPARTURE, pick.purpose)
    assertEquals(paris, pick.point)
    // Le libellé lisible vient de /api/v1/reverse-geocode, réutilisé et non réécrit.
    assertEquals(listOf(paris), geocode.reversed)
    // Le menu se referme et le point choisi reste affiché tant qu'il n'est pas consommé.
    assertNull(model.uiState.value.longPressPoint)

    selection.consume()
    assertNull(selection.pick.value)
  }

  @Test
  fun `sans appui long, aucun choix ne peut être déposé`() = runTest {
    val model = viewModel()
    model.onPick(MapPickPurpose.DESTINATION)
    assertNull(selection.pick.value)
    assertTrue(geocode.reversed.isEmpty())
  }

  // --- Mémorisation de la caméra, SPEC.md § 5.1 ---------------------------------------------

  @Test
  fun `chaque arrêt de caméra mémorise le cadrage`() = runTest {
    val model = viewModel()
    val camera = MapCamera(lyon, zoom = 13.0)
    model.onCameraIdle(
      viewport = io.github.mgdx.escale.core.geo.MapViewport(
        visibleArea = BoundingBox(min = LatLon(45.7, 4.8), max = LatLon(45.8, 4.9)),
        zoom = 13.0,
      ),
      camera = camera,
    )
    assertEquals(listOf(camera), cameras.saved)
  }

  // --- Tracé du trajet sélectionné, SPEC.md § 5.3 et § 5.7 -----------------------------------

  @Test
  fun `un trajet sélectionné alimente les deux sources et cadre la carte`() = runTest {
    val model = viewModel()
    journeys.select(journeyBetween(paris, lyon))

    val state = model.uiState.value
    assertTrue(state.journeyTraced)
    assertTrue(state.journeyLinesGeoJson.contains("\"LineString\""))
    assertTrue(state.journeyMarkersGeoJson.contains("ORIGIN"))
    assertTrue(state.journeyMarkersGeoJson.contains("DESTINATION"))

    // Le cadrage est une emprise, jamais un centre et un zoom devinés : c'est le composable qui y
    // appliquera le remplissage de la feuille ouverte (règle 9).
    val goal = checkNotNull(state.cameraTarget).goal
    assertTrue(goal is CameraGoal.Fit)
    val bounds = (goal as CameraGoal.Fit).bounds
    assertTrue(bounds.contains(paris))
    assertTrue(bounds.contains(lyon))
    // L'emprise est élargie pour que le tracé ne colle pas aux bords.
    assertTrue(bounds.min.lat < lyon.lat)
    assertTrue(bounds.max.lat > paris.lat)
    // Une animation de cadrage, donc bornée à 500 ms par le composable.
    assertTrue(checkNotNull(state.cameraTarget).animated)
  }

  @Test
  fun `désélectionner un trajet vide les deux sources sans rien démonter`() = runTest {
    val model = viewModel()
    journeys.select(journeyBetween(paris, lyon))
    assertTrue(model.uiState.value.journeyTraced)

    // C'est aussi ce que fait une nouvelle recherche : elle vide le magasin.
    journeys.select(null)

    val state = model.uiState.value
    assertFalse(state.journeyTraced)
    assertEquals(MapGeoJson.EMPTY, state.journeyLinesGeoJson)
    assertEquals(MapGeoJson.EMPTY, state.journeyMarkersGeoJson)
  }

  @Test
  fun `un trajet remplace le précédent, il ne s'y ajoute pas`() = runTest {
    val model = viewModel()
    journeys.select(journeyBetween(paris, lyon))
    journeys.select(journeyBetween(paris, paris.copy(lat = paris.lat + 0.02)))

    // Deux portions tracées d'affilée ne laissent qu'une seule entité dans la source : la carte
    // reçoit un remplacement, pas un empilement (règle 8).
    assertEquals(1, model.uiState.value.journeyLinesGeoJson.split("LineString").size - 1)
    assertFalse(model.uiState.value.journeyLinesGeoJson.contains("${lyon.lon}"))
  }

  @Test
  fun `un trajet de quelques mètres se cadre par son centre`() = runTest {
    val model = viewModel()
    journeys.select(journeyBetween(paris, paris.copy(lat = paris.lat + 0.00002)))

    val goal = checkNotNull(model.uiState.value.cameraTarget).goal
    assertTrue(goal is CameraGoal.Center)
  }

  private fun journeyBetween(from: LatLon, to: LatLon): Journey {
    val instant = Instant.parse("2026-09-01T08:00:00Z")
    val leg = JourneyLeg.Walk(
      startTime = instant,
      endTime = instant,
      scheduledStartTime = instant,
      scheduledEndTime = instant,
      duration = Duration.ofMinutes(10),
      from = Place("Départ", from, null, null, instant, instant),
      to = Place("Arrivée", to, null, null, instant, instant),
      geometry = listOf(from, to),
    )
    return Journey(
      id = null,
      startTime = instant,
      endTime = instant,
      scheduledStartTime = instant,
      scheduledEndTime = instant,
      duration = Duration.ofMinutes(10),
      transfers = 0,
      legs = listOf(leg),
    )
  }
}
