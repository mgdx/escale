package io.github.mgdx.escale.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapViewport
import io.github.mgdx.escale.core.geo.mapDataRequests
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'écran de carte : cadrage, position de l'usager, feuille de style, points choisis.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et ne détient pas la carte :
 * l'instance MapLibre vit dans l'`AppContainer` et survit à tous les écrans (SPEC.md § 5.7,
 * règle 8). Ici, on ne décide que de ce qu'elle doit montrer.
 *
 * Aucune coordonnée n'est journalisée (SPEC.md § 8 et § 11).
 */
class MapViewModel(
  private val serverRepository: ServerRepository,
  private val mapRepository: MapRepository,
  private val geocodeRepository: GeocodeRepository,
  private val styles: MapStyleSource,
  private val cameraStore: MapCameraMemory,
  private val locationSource: LocationSource,
  private val selection: MapSelection,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  private val state = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = state.asStateFlow()

  /** Le thème du système, poussé par le composable : il décide de la palette de la feuille. */
  private val darkTheme = MutableStateFlow<Boolean?>(null)

  /** Un arrêt de caméra, et un seul par geste (SPEC.md § 5.7, règle 1). */
  private val cameraIdles = MutableSharedFlow<MapViewport>(extraBufferCapacity = EXTRA_BUFFER)

  private var tokens = 0L
  private var locationJob: Job? = null
  private var fineRequested = false
  private var centerOnNextFix = false

  init {
    observeStyle()
    observePlannedRequests()
    applyInitialCamera()
  }

  /** Le thème a changé : la feuille de style se recharge, la carte, elle, ne bouge pas. */
  fun onThemeChanged(dark: Boolean) {
    darkTheme.value = dark
  }

  /**
   * La caméra s'est arrêtée : c'est le seul moment où une requête de carte peut naître
   * (SPEC.md § 5.7, règle 1), et c'est aussi là qu'on mémorise le cadrage (SPEC.md § 5.1).
   */
  fun onCameraIdle(viewport: MapViewport, camera: MapCamera) {
    cameraIdles.tryEmit(viewport)
    viewModelScope.launch { cameraStore.save(camera) }
  }

  /**
   * L'usager a déplacé la carte du doigt : le suivi s'arrête.
   *
   * Continuer à recentrer sous ses doigts serait une lutte, pas un suivi.
   */
  fun onUserMovedCamera() {
    state.update { current ->
      if (current.locateState == LocateState.FOLLOWING) current.copy(locateState = LocateState.CENTERED) else current
    }
  }

  /** Acquitte un cadrage appliqué, pour qu'il ne se rejoue pas à la recomposition suivante. */
  fun onCameraTargetApplied() {
    state.update { it.copy(cameraTarget = null) }
  }

  /**
   * Appui sur le bouton de position (SPEC.md § 5.1).
   *
   * Sans permission, elle est demandée — `ACCESS_COARSE_LOCATION` d'abord, jamais au démarrage.
   * Avec la permission, le premier appui centre, le suivant passe en suivi ; c'est à ce
   * second appui, et seulement là, que la permission précise est demandée à son tour.
   */
  fun onLocateClick() {
    if (!locationSource.hasCoarsePermission()) {
      requestPermission(LocationPermission.COARSE)
      return
    }
    when (state.value.locateState) {
      LocateState.UNKNOWN -> startTracking(LocateState.CENTERED)

      LocateState.CENTERED -> {
        startTracking(LocateState.FOLLOWING)
        // « seulement si l'utilisateur insiste pour un centrage précis » : une seule fois par
        // session, sans quoi ce serait du harcèlement.
        if (!locationSource.hasFinePermission() && !fineRequested) {
          fineRequested = true
          requestPermission(LocationPermission.FINE)
        }
      }

      LocateState.FOLLOWING -> stopTracking()
    }
  }

  /** Acquitte une demande de permission lancée. */
  fun onPermissionRequestLaunched() {
    state.update { it.copy(permissionRequest = null) }
  }

  /**
   * Réponse du système à une demande de permission.
   *
   * Refus : **le bouton reste**, et un appui explique en une phrase et propose d'ouvrir les
   * réglages système. L'application reste pleinement utilisable sans localisation (SPEC.md § 11).
   */
  fun onPermissionResult(granted: Boolean) {
    if (granted) {
      startTracking(LocateState.CENTERED)
    } else {
      state.update { it.copy(permissionExplanationVisible = true) }
    }
  }

  fun onDismissPermissionExplanation() {
    state.update { it.copy(permissionExplanationVisible = false) }
  }

  /** Appui long sur la carte : le menu « Partir d'ici » / « Aller ici » s'ouvre sur ce point. */
  fun onMapLongClick(point: LatLon) {
    viewModelScope.launch {
      val geoJson = withContext(computeDispatcher) { MapGeoJson.singlePoint(point) }
      state.update { it.copy(longPressPoint = point, pickedPointGeoJson = geoJson) }
    }
  }

  fun onDismissLongPress() {
    state.update { it.copy(longPressPoint = null, pickedPointGeoJson = MapGeoJson.EMPTY) }
  }

  /**
   * L'usager a choisi quoi faire du point : il est déposé dans [MapSelection], que le lot
   * « recherche » consommera. Le libellé lisible suit dès que le géocodage inverse l'a rendu.
   */
  fun onPick(purpose: MapPickPurpose) {
    val point = state.value.longPressPoint ?: return
    selection.select(purpose, point)
    onDismissLongPress()
    viewModelScope.launch {
      selection.attachLabel(point, geocodeRepository.reverseGeocode(point).getOrNull())
    }
  }

  fun onShowAttribution() {
    state.update { it.copy(attributionVisible = true) }
  }

  fun onDismissAttribution() {
    state.update { it.copy(attributionVisible = false) }
  }

  /** MapLibre n'a pas pu charger la carte : le message discret de SPEC.md § 5.7 s'affiche. */
  fun onMapLoadFailed() {
    state.update { it.copy(tilesUnavailable = true) }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeStyle() {
    viewModelScope.launch {
      combine(serverRepository.current, darkTheme.filterNotNull()) { server, dark -> server to dark }
        .distinctUntilChanged()
        .mapLatest { (server, dark) -> loadStyle(server, dark) }
        .collect { loaded ->
          state.update { it.copy(styleJson = loaded.first, tilesUnavailable = loaded.second) }
        }
    }
  }

  private suspend fun loadStyle(server: ServerConfig, dark: Boolean): Pair<String, Boolean> {
    // Un serveur qu'on a testé et qui n'a pas de tuiles reçoit un fond neutre d'emblée. Un serveur
    // jamais testé garde le bénéfice du doute : c'est MapLibre qui dira s'il sert des tuiles.
    val knownWithoutTiles = server.lastCheckedAt != null && !server.hasTiles
    val json = if (knownWithoutTiles) styles.blankStyle(dark) else styles.tiledStyle(server.baseUrl, dark)
    return json to knownWithoutTiles
  }

  /**
   * Branche les arrêts de caméra sur les règles de `:core`.
   *
   * `collectLatest` porte la règle 2 : une nouvelle requête planifiée annule la coroutine de la
   * précédente, donc l'appel réseau qu'elle attendait. Le jalon 6 remplacera la mise à jour de
   * l'état par l'appel à `StopsRepository`.
   */
  private fun observePlannedRequests() {
    viewModelScope.launch {
      mapDataRequests(cameraIdles).collect { request ->
        state.update { it.copy(plannedRequest = request) }
      }
    }
  }

  /** Le cadrage initial de SPEC.md § 5.1, dans l'ordre exact que la spec impose. */
  private fun applyInitialCamera() {
    viewModelScope.launch {
      val camera = cameraStore.lastCamera()
        ?: locationSource.lastKnownLocation()?.let { MapCamera(it, NEARBY_ZOOM) }
        ?: mapRepository.initialCamera().getOrNull()
        ?: WORLD_CAMERA
      state.update { it.copy(cameraTarget = CameraTarget(camera, animated = false, token = nextToken())) }
    }
  }

  private fun requestPermission(permission: LocationPermission) {
    state.update { it.copy(permissionRequest = PermissionRequest(permission, nextToken())) }
  }

  private fun startTracking(mode: LocateState) {
    // Le prochain point recentre la carte, que ce soit celui déjà connu du système ou le premier
    // que le fournisseur enverra.
    centerOnNextFix = true
    state.update { it.copy(locateState = mode) }
    locationSource.lastKnownLocation()?.let(::centerOn)
    if (locationJob?.isActive == true) return
    locationJob = viewModelScope.launch {
      locationSource.locations().collect { point -> onNewLocation(point) }
    }
  }

  private fun centerOn(point: LatLon) {
    centerOnNextFix = false
    state.update {
      it.copy(cameraTarget = CameraTarget(MapCamera(point, NEARBY_ZOOM), animated = true, token = nextToken()))
    }
  }

  private suspend fun onNewLocation(point: LatLon) {
    // Règle 7 : la conversion en GeoJSON se fait hors du fil principal, et la source est mise à
    // jour en une seule opération.
    val geoJson = withContext(computeDispatcher) { MapGeoJson.singlePoint(point) }
    state.update { it.copy(userLocationGeoJson = geoJson) }
    if (centerOnNextFix || state.value.locateState == LocateState.FOLLOWING) centerOn(point)
  }

  private fun stopTracking() {
    locationJob?.cancel()
    locationJob = null
    state.update { it.copy(locateState = LocateState.CENTERED) }
  }

  private fun nextToken(): Long {
    tokens += 1
    return tokens
  }

  companion object {
    /** Le zoom d'un centrage sur la position : le quartier, pas la rue ni la région. */
    const val NEARBY_ZOOM = 15.0

    /** Ni caméra mémorisée, ni position connue, ni serveur qui réponde : on montre la planète. */
    val WORLD_CAMERA = MapCamera(center = LatLon(lat = 20.0, lon = 0.0), zoom = 1.5)

    private const val EXTRA_BUFFER = 4

    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        MapViewModel(
          serverRepository = container.serverRepository,
          mapRepository = container.mapRepository,
          geocodeRepository = container.geocodeRepository,
          styles = container.mapStyles,
          cameraStore = container.mapCameraStore,
          locationSource = container.deviceLocationSource,
          selection = container.mapSelection,
        )
      }
    }
  }
}
