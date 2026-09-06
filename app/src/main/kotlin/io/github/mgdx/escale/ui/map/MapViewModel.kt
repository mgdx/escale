package io.github.mgdx.escale.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.geo.MAX_BROWSABLE_STOPS
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapDataRequest
import io.github.mgdx.escale.core.geo.MapViewport
import io.github.mgdx.escale.core.geo.RentalMarker
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.geo.StopMarker
import io.github.mgdx.escale.core.geo.browsableStops
import io.github.mgdx.escale.core.geo.center
import io.github.mgdx.escale.core.geo.isPointLike
import io.github.mgdx.escale.core.geo.journeyTrace
import io.github.mgdx.escale.core.geo.mapDataRequests
import io.github.mgdx.escale.core.geo.rentalMarkers
import io.github.mgdx.escale.core.geo.requestsRentals
import io.github.mgdx.escale.core.geo.shouldClusterRentals
import io.github.mgdx.escale.core.geo.shouldClusterStops
import io.github.mgdx.escale.core.geo.stopMarkers
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.core.result.getOrNull
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
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
// Treize collaborateurs, et pas un de trop : la carte est le seul écran qui réunit le serveur
// courant, les arrêts, les réglages, le géocodage inverse, la position de l'appareil, la feuille
// de style, la caméra mémorisée et les quatre points de rendez-vous avec les autres lots. Les
// regrouper en objets de commodité masquerait ce que cet écran dépend réellement, sans en retirer
// une seule dépendance (docs/architecture.md § 9 : injection par constructeur, sans conteneur).
@Suppress("LongParameterList")
class MapViewModel(
  private val serverRepository: ServerRepository,
  private val mapRepository: MapRepository,
  private val geocodeRepository: GeocodeRepository,
  private val stopsRepository: StopsRepository,
  private val rentalsRepository: RentalsRepository,
  private val preferencesRepository: PreferencesRepository,
  private val styles: MapStyleSource,
  private val cameraStore: MapCameraMemory,
  private val locationSource: LocationSource,
  private val selection: MapSelection,
  private val searchSession: SearchSession,
  private val selectedJourneys: SelectedJourneyStore,
  private val departureRequests: StopDepartureRequests,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  /**
   * Les rappels d'appui sur un arrêt, construits une seule fois et posés sur l'état initial : ils
   * ne changent jamais, et n'entrent donc jamais dans une recomposition (voir [MapStopActions]).
   */
  private val stopActions = MapStopActions(
    onStopClick = ::onStopClick,
    onClusterClick = ::onClusterClick,
    onDismissStop = ::onDismissStop,
    onDepartures = ::onStopDepartures,
  )

  /** Les mêmes rappels pour le libre-service, construits une fois et jamais recomposés. */
  private val rentalActions = MapRentalActions(
    onRentalClick = ::onRentalClick,
    onDismissRental = ::onDismissRental,
  )

  /** Les mêmes rappels pour les points d'intérêt du fond de carte. */
  private val placeActions = MapPlaceActions(
    onPlaceClick = ::onPlaceClick,
    onDismissPlace = ::onDismissPlace,
    onPick = ::onPlacePick,
  )

  private val state = MutableStateFlow(
    MapUiState(stopActions = stopActions, rentalActions = rentalActions, placeActions = placeActions),
  )
  val uiState: StateFlow<MapUiState> = state.asStateFlow()

  /** Le thème du système, poussé par le composable : il décide de la palette de la feuille. */
  private val darkTheme = MutableStateFlow<Boolean?>(null)

  /**
   * Le dernier arrêt de caméra (SPEC.md § 5.7, règle 1).
   *
   * Un `StateFlow` et non un flux d'événements : il rejoue sa valeur courante à chaque nouvelle
   * collecte, ce qui fait que rallumer le réglage « arrêts » recharge l'écran courant sans attendre
   * que l'usager touche la carte. Deux arrêts de caméra trop rapprochés se fondent en un seul,
   * ce que l'anti-rebond de 300 ms aurait fait de toute façon.
   */
  private val cameraIdles = MutableStateFlow<MapViewport?>(null)

  /**
   * Les marqueurs de la dernière réponse d'arrêts, tels quels.
   *
   * Ils servent au parcours en liste de SPEC.md § 9, qui doit dire exactement ce que la carte
   * montre : c'est la même donnée, filtrée par l'emprise visible et le palier courant.
   */
  private val loadedStops = MutableStateFlow<List<StopMarker>>(emptyList())

  private var tokens = 0L
  private var locationJob: Job? = null
  private var stopDetailJob: Job? = null

  /**
   * La requête d'adresse de la fiche ouverte (SPEC.md § 7, règle 11).
   *
   * Une seule à la fois, et elle meurt avec la fiche : c'est ce champ, et lui seul, qui garantit
   * qu'une fiche refermée avant la réponse n'en attend plus aucune.
   */
  private var placeAddressJob: Job? = null
  private var fineRequested = false
  private var centerOnNextFix = false

  init {
    observeStyle()
    observeStops()
    observeBrowsableStops()
    observeRentals()
    observePoiCategories()
    observeSelectedJourney()
    observeSearches()
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
    cameraIdles.value = viewport
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
   * Branche les arrêts de caméra sur `StopsRepository` (SPEC.md § 5.7).
   *
   * Sept des neuf règles de fluidité passent par ces quelques lignes :
   *
   * - **règles 1, 3, 5 et § 7.9** : `mapDataRequests` ne laisse passer qu'une requête par geste,
   *   après 300 ms, sur l'emprise élargie de 30 %, jamais sous le zoom 11 et jamais en redescendant
   *   d'un palier. Rien de tout cela n'est réécrit ici ;
   * - **règle 2** : `collectLatest` annule la coroutine de la requête précédente — donc l'appel
   *   HTTP — dès qu'une nouvelle naît, c'est-à-dire dès que la caméra a rebougé ;
   * - **règle 4** : le cache par emprise et par palier est dans le dépôt ; une emprise déjà
   *   couverte rend sa réponse sans toucher au réseau ;
   * - **règles 6 et 7** : la mise en GeoJSON se fait sur [computeDispatcher], et les deux sources
   *   sont posées en **une seule** modification d'état.
   *
   * Le réglage « arrêts » de SPEC.md § 5.6 coupe le flux entier : décoché, il n'émet plus la
   * moindre requête, ce qui est plus honnête que de charger pour ne rien montrer. Recoché, la
   * collecte reprend sur la position courante de la caméra, que [cameraIdles] a retenue.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeStops() {
    viewModelScope.launch {
      preferencesRepository.displayPreferences
        .map { it.showStops }
        .distinctUntilChanged()
        .onEach { visible -> if (!visible) clearStops() }
        .flatMapLatest { visible ->
          if (visible) mapDataRequests(cameraIdles.filterNotNull()) else emptyFlow()
        }
        .collectLatest { request -> loadStops(request) }
    }
  }

  private suspend fun loadStops(request: MapDataRequest) {
    val stops = stopsRepository
      .stopsIn(area = request.area, modes = request.tier.stopModes, grouped = true)
      .getOrNull()
      // Une emprise sans réponse laisse les marqueurs précédents en place : une carte qui se vide
      // parce que le réseau a hoqueté est pire qu'une carte un peu en retard (SPEC.md § 8).
      ?: return
    val markers = withContext(computeDispatcher) { stopMarkers(stops) }
    val drawing = withContext(computeDispatcher) { stopsDrawing(markers) }
    // Les mêmes marqueurs alimentent le parcours en liste : la liste dit ce que la carte montre
    // parce qu'elle part de la même donnée, jamais d'une seconde requête (SPEC.md § 9).
    loadedStops.value = markers
    state.update {
      it.copy(
        plannedRequest = request,
        stopsGeoJson = drawing.plain,
        clusteredStopsGeoJson = drawing.clustered,
      )
    }
  }

  /**
   * Les arrêts mis en GeoJSON, sur l'une des deux sources et jamais sur les deux.
   *
   * Règle 6 : au-delà de 200 points, c'est la source regroupante qui les porte ; en deçà, chacun
   * garde son dessin et son nom. L'autre source reçoit une collection vide, ce qui l'efface sans
   * démonter la moindre couche (règle 8).
   */
  private fun stopsDrawing(markers: List<StopMarker>): StopsDrawing {
    val geoJson = MapGeoJson.stops(markers)
    return if (shouldClusterStops(markers.size)) {
      StopsDrawing(plain = MapGeoJson.EMPTY, clustered = geoJson)
    } else {
      StopsDrawing(plain = geoJson, clustered = MapGeoJson.EMPTY)
    }
  }

  /**
   * Tient à jour la liste des arrêts affichés (SPEC.md § 9).
   *
   * Elle se recalcule à chaque arrêt de caméra, et à chaque réponse d'arrêts : ce sont les deux
   * seuls moments où « ce que la carte montre » change. Aucune requête n'en naît — la donnée est
   * déjà là — et le calcul part sur [computeDispatcher], comme la mise en GeoJSON, pour que le fil
   * principal reste sous les 16 ms qu'exige le § 5.7.
   *
   * Un déplacement qui ne franchit aucun seuil de cache ne provoque donc aucun octet de réseau,
   * mais met bien la liste à jour : c'est l'emprise **visible** qui a changé, pas la donnée.
   */
  private fun observeBrowsableStops() {
    viewModelScope.launch {
      combine(loadedStops, cameraIdles.filterNotNull()) { markers, viewport -> markers to viewport }
        .collectLatest { (markers, viewport) ->
          val visible = withContext(computeDispatcher) {
            browsableStops(markers = markers, visibleArea = viewport.visibleArea, zoom = viewport.zoom)
          }
          state.update {
            it.copy(
              browsableStops = visible.map(StopMarker::toSelectedStop),
              browsableStopsTruncated = visible.size >= MAX_BROWSABLE_STOPS,
            )
          }
        }
    }
  }

  private fun clearStops() {
    loadedStops.value = emptyList()
    state.update {
      it.copy(
        stopsGeoJson = MapGeoJson.EMPTY,
        clusteredStopsGeoJson = MapGeoJson.EMPTY,
        selectedStop = null,
        browsableStops = emptyList(),
        browsableStopsTruncated = false,
      )
    }
  }

  /**
   * Branche les arrêts de caméra sur `RentalsRepository` (SPEC.md § 5.7).
   *
   * Le flux est celui des arrêts, à un filtre près, et c'est tout l'intérêt : `mapDataRequests`
   * porte déjà l'anti-rebond de 300 ms (règle 1), l'emprise élargie de 30 % (règle 3) et le refus
   * de réémettre en redescendant d'un palier (règle 5). Le `collectLatest` porte l'annulation de la
   * requête en vol (règle 2), et la conversion en GeoJSON se fait sur [computeDispatcher] pour être
   * posée en une seule modification d'état (règles 6 et 7).
   *
   * **Le filtre est la seule chose propre au libre-service** : `requestsRentals` est faux sous le
   * zoom 13, là où `requestsStops` est déjà vrai à partir du zoom 11. Un arrêt de caméra au zoom 12
   * charge donc des arrêts et **aucune** station, exactement comme le veut le tableau de § 5.7.
   * Le filtre est posé **après** `mapDataRequests`, et non avant : le flux garde ainsi la mémoire du
   * palier déjà chargé, ce qui fait que remonter au zoom 14 puis redescendre n'émet rien de plus.
   *
   * Le réglage « stations en libre-service » de SPEC.md § 5.6 coupe le flux entier, indépendamment
   * du zoom : décoché, il n'émet plus la moindre requête. Recoché, la collecte reprend sur la
   * position courante de la caméra, que [cameraIdles] a retenue.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeRentals() {
    viewModelScope.launch {
      preferencesRepository.displayPreferences
        .map { it.showRentals }
        .distinctUntilChanged()
        .onEach { visible -> if (!visible) clearRentals() }
        .flatMapLatest { visible ->
          if (visible) {
            mapDataRequests(cameraIdles.filterNotNull()).filter { it.tier.requestsRentals }
          } else {
            emptyFlow()
          }
        }
        .collectLatest { request -> loadRentals(request) }
    }
  }

  private suspend fun loadRentals(request: MapDataRequest) {
    val availabilities = rentalsRepository
      .stationsIn(request.area)
      .getOrNull()
      // Comme pour les arrêts : une emprise sans réponse laisse les marqueurs précédents en place
      // plutôt que de vider la carte parce que le réseau a hoqueté (SPEC.md § 8).
      ?: return
    val drawing = withContext(computeDispatcher) { rentalsDrawing(rentalMarkers(availabilities)) }
    state.update {
      it.copy(
        rentalStationsGeoJson = drawing.stations.plain,
        clusteredRentalStationsGeoJson = drawing.stations.clustered,
        rentalVehiclesGeoJson = drawing.vehicles.plain,
        clusteredRentalVehiclesGeoJson = drawing.vehicles.clustered,
      )
    }
  }

  private fun clearRentals() {
    state.update {
      it.copy(
        rentalStationsGeoJson = MapGeoJson.EMPTY,
        clusteredRentalStationsGeoJson = MapGeoJson.EMPTY,
        rentalVehiclesGeoJson = MapGeoJson.EMPTY,
        clusteredRentalVehiclesGeoJson = MapGeoJson.EMPTY,
        selectedRental = null,
      )
    }
  }

  /**
   * Appui sur une station ou un véhicule en libre-service (SPEC.md § 5.7).
   *
   * « Nom, véhicules disponibles, places libres, lien vers l'exploitant » : tout est déjà dans
   * l'entité touchée, l'infobulle s'ouvre donc sans le moindre appel réseau. Elle referme celle d'un
   * arrêt : deux fiches superposées au bas de l'écran seraient illisibles à 200 % d'agrandissement.
   *
   * Privée, comme sa jumelle [onDismissRental] : rien ne l'appelle hors de cette classe. Le canevas
   * passe par [MapRentalActions], que l'état transporte, et les cas d'essai empruntent le même
   * chemin — celui que le doigt de l'usager prend réellement.
   */
  private fun onRentalClick(rental: SelectedRental) {
    stopDetailJob?.cancel()
    placeAddressJob?.cancel()
    state.update { it.copy(selectedRental = rental, selectedStop = null, selectedPlace = null) }
  }

  private fun onDismissRental() {
    state.update { it.copy(selectedRental = null) }
  }

  /**
   * Une recherche part : l'infobulle ouverte se referme (SPEC.md § 5.1).
   *
   * « Dès que Départ et Arrivée sont renseignés, la recherche se lance [...] les résultats montent
   * en feuille inférieure au-dessus de la carte, qui reste visible en haut et cadre le trajet
   * sélectionné. » La feuille et l'infobulle se disputent alors le bas de l'écran, et c'est
   * exactement ce que [onRentalClick] refuse déjà entre deux infobulles : deux fiches superposées
   * seraient illisibles à 200 % d'agrandissement, et l'infobulle mangerait la part de carte que le
   * trajet doit occuper (SPEC.md § 9).
   *
   * Le signal est le **brouillon complet**, et non le premier trajet mis en évidence : une
   * recherche qui met deux secondes à répondre, ou qui échoue, laisserait sinon la fiche ouverte
   * par-dessus la feuille. Chaque brouillon complet est une recherche qui part — c'est le même
   * signal que `ResultsViewModel` ouvre la feuille avec —, y compris quand seule la destination
   * change d'une recherche à la suivante.
   */
  private fun observeSearches() {
    viewModelScope.launch {
      searchSession.draft.filter { it.isComplete }.collect { closeDetailCard() }
    }
  }

  /** Referme la fiche ouverte, quelle que soit sa famille, et abandonne l'appel qu'elle attend. */
  private fun closeDetailCard() {
    stopDetailJob?.cancel()
    stopDetailJob = null
    placeAddressJob?.cancel()
    placeAddressJob = null
    state.update { it.copy(selectedStop = null, selectedRental = null, selectedPlace = null) }
  }

  /**
   * Les douze bascules de couches de SPEC.md § 5.6, indépendantes du zoom.
   *
   * Aucune requête n'est en jeu : les points d'intérêt sont déjà dans les tuiles vectorielles, et
   * la feuille de style embarquée porte leurs douze couches avec le bon `minzoom`. Il n'y a qu'à
   * les allumer ou à les éteindre — jamais à en ajouter ni à en retirer (règle 8).
   *
   * Éteindre la catégorie d'une fiche ouverte la referme : laisser une fiche décrire un pictogramme
   * que la carte vient d'effacer serait un mensonge de plus qu'une commodité.
   */
  private fun observePoiCategories() {
    viewModelScope.launch {
      preferencesRepository.displayPreferences
        .map { it.visiblePoiCategories }
        .distinctUntilChanged()
        .collect { visible ->
          val orphaned = state.value.selectedPlace?.category?.let { it !in visible } == true
          if (orphaned) onDismissPlace()
          state.update { it.copy(visiblePoiCategories = visible) }
        }
    }
  }

  /**
   * Appui sur un point d'intérêt du fond de carte (SPEC.md § 5.7).
   *
   * La fiche s'ouvre **aussitôt** sur ce que la tuile porte — le nom, le type, le numéro de voie —
   * et n'attend rien pour s'afficher. La rue, elle, demande un géocodage inverse : c'est la seule
   * requête que les points d'intérêt provoquent, **une par fiche**, servie par le cache de 24 h du
   * géocodage (SPEC.md § 7, règles 5 et 11).
   *
   * Elle referme l'infobulle d'un arrêt ou d'un point de libre-service : deux fiches superposées au
   * bas de l'écran seraient illisibles à 200 % d'agrandissement (SPEC.md § 9).
   */
  private fun onPlaceClick(place: SelectedPlace) {
    stopDetailJob?.cancel()
    placeAddressJob?.cancel()
    state.update { it.copy(selectedPlace = place, selectedStop = null, selectedRental = null) }
    placeAddressJob = viewModelScope.launch {
      val address = geocodeRepository.reverseGeocode(place.point).getOrNull()
      state.update { current ->
        // L'usager a pu refermer la fiche, ou en ouvrir une autre, pendant l'appel.
        if (current.selectedPlace?.point != place.point) {
          current
        } else {
          current.copy(selectedPlace = current.selectedPlace.copy(address = address, addressLoading = false))
        }
      }
    }
  }

  /** Referme la fiche, et abandonne la requête d'adresse qu'elle attendait encore. */
  private fun onDismissPlace() {
    placeAddressJob?.cancel()
    placeAddressJob = null
    state.update { it.copy(selectedPlace = null) }
  }

  /**
   * « Partir d'ici » / « Aller ici » depuis la fiche : exactement le chemin de l'appui long.
   *
   * L'adresse déjà rendue sert de libellé ; si elle n'est pas encore arrivée, le point part sans
   * elle. **Aucune seconde requête n'est émise** — une fiche vaut une requête, pas deux
   * (SPEC.md § 7, règle 11) —, et le lot « recherche » sait afficher un point sans nom.
   */
  private fun onPlacePick(purpose: MapPickPurpose) {
    val place = state.value.selectedPlace ?: return
    selection.select(purpose, place.point)
    place.address?.let { selection.attachLabel(place.point, it) }
    onDismissPlace()
  }

  /**
   * Appui sur un arrêt : l'infobulle s'ouvre aussitôt sur ce qu'on sait déjà — son nom et son mode,
   * lus sur l'entité touchée — puis les lignes desservies arrivent (SPEC.md § 5.7).
   *
   * L'appel n'attend pas de réponse pour ouvrir l'infobulle : `/api/v6/stop` prend le temps qu'il
   * prend, et l'usager doit voir immédiatement qu'il a touché le bon arrêt.
   */
  fun onStopClick(stop: SelectedStop) {
    placeAddressJob?.cancel()
    state.update {
      it.copy(
        selectedStop = stop.copy(linesLoading = true, linesFailed = false),
        selectedRental = null,
        selectedPlace = null,
      )
    }
    stopDetailJob?.cancel()
    stopDetailJob = viewModelScope.launch {
      val outcome = stopsRepository.stop(stop.id)
      state.update { current ->
        // L'usager a pu refermer l'infobulle ou en ouvrir une autre pendant l'appel.
        if (current.selectedStop?.id != stop.id) {
          current
        } else {
          current.copy(selectedStop = current.selectedStop.withLines(outcome))
        }
      }
    }
  }

  /**
   * « Un bouton menant aux prochains départs (§ 5.4) » — **le point d'accroche du jalon 9**.
   *
   * L'écran des prochains départs n'existe pas encore ; l'appui dépose la demande dans
   * [StopDepartureRequests], que la navigation consommera. Voir la documentation de cette classe
   * pour les trois lignes qui restent à écrire.
   */
  fun onStopDepartures(stop: SelectedStop) {
    departureRequests.request(stopId = stop.id, stopName = stop.name)
  }

  fun onDismissStop() {
    stopDetailJob?.cancel()
    stopDetailJob = null
    state.update { it.copy(selectedStop = null) }
  }

  /**
   * Appui sur une pastille de regroupement : on se rapproche, on n'ouvre rien.
   *
   * Le zoom monte de [CLUSTER_ZOOM_STEP] paliers, ce qui suffit à faire éclater le groupe.
   * L'animation est bornée à 500 ms par le composable (règle 9).
   */
  fun onClusterClick(point: LatLon) {
    val zoom = (cameraIdles.value?.zoom ?: NEARBY_ZOOM) + CLUSTER_ZOOM_STEP
    state.update {
      val goal = CameraGoal.Center(MapCamera(point, zoom))
      it.copy(
        cameraTarget = CameraTarget(goal, animated = true, token = nextToken()),
        selectedStop = null,
        selectedRental = null,
        selectedPlace = null,
      )
    }
  }

  /**
   * Trace le trajet choisi dans la feuille de résultats, et le cadre (SPEC.md § 5.1 et § 5.3).
   *
   * Trois règles de fluidité se jouent ici (SPEC.md § 5.7) :
   *
   * - **règle 7** : le calcul du tracé et sa mise en GeoJSON se font sur [computeDispatcher], hors
   *   du fil principal, et les deux sources sont mises à jour en une seule modification d'état ;
   * - **règle 8** : rien n'est ajouté ni retiré des couches. Une désélection, ou une nouvelle
   *   recherche qui vide le magasin, pose une collection vide sur les deux sources : le tracé
   *   disparaît, les couches restent, et rien ne s'empile d'un trajet au suivant ;
   * - **règle 9** : le cadrage est une emprise, à laquelle le composable appliquera le
   *   remplissage de la feuille ouverte.
   *
   * `collectLatest` abandonne le calcul du trajet précédent dès qu'un autre est choisi : sur une
   * frise de plusieurs centaines de points, c'est ce qui évite d'afficher un tracé périmé.
   */
  private fun observeSelectedJourney() {
    viewModelScope.launch {
      selectedJourneys.selected.collectLatest { journey ->
        val drawing = withContext(computeDispatcher) { drawingOf(journey) }
        state.update { current ->
          current.copy(
            journeyLinesGeoJson = drawing.lines,
            journeyMarkersGeoJson = drawing.markers,
            journeyTraced = drawing.traced,
            cameraTarget = drawing.goal
              ?.let { CameraTarget(it, animated = true, token = nextToken()) }
              ?: current.cameraTarget,
          )
        }
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
      state.update {
        it.copy(cameraTarget = CameraTarget(CameraGoal.Center(camera), animated = false, token = nextToken()))
      }
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
      val goal = CameraGoal.Center(MapCamera(point, NEARBY_ZOOM))
      it.copy(cameraTarget = CameraTarget(goal, animated = true, token = nextToken()))
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

  /** Les arrêts prêts à poser : l'une des deux collections porte tout, l'autre est vide. */
  private data class StopsDrawing(val plain: String, val clustered: String)

  /** Le tracé d'un trajet, prêt à poser : deux sources GeoJSON et un cadrage. */
  private data class JourneyDrawing(val lines: String, val markers: String, val traced: Boolean, val goal: CameraGoal?)

  private fun drawingOf(journey: Journey?): JourneyDrawing {
    val trace = journeyTrace(journey)
    return JourneyDrawing(
      lines = MapGeoJson.journeyLines(trace.segments),
      markers = MapGeoJson.journeyMarkers(trace.markers),
      traced = !trace.isEmpty,
      goal = frameOf(trace.bounds),
    )
  }

  /**
   * Le cadrage d'une emprise de trajet (SPEC.md § 5.3).
   *
   * L'emprise est élargie de [FRAME_MARGIN_RATIO] plutôt que de recevoir un remplissage
   * supplémentaire : le remplissage de la caméra est déjà celui des encarts système et de la
   * feuille de résultats, et lui ajouter une marge le ferait diverger de celui que le composable
   * applique en permanence. Une emprise réduite à un point se cadre par son centre.
   */
  private fun frameOf(bounds: BoundingBox?): CameraGoal? = when {
    bounds == null -> null
    bounds.isPointLike() -> CameraGoal.Center(MapCamera(bounds.center, NEARBY_ZOOM))
    else -> CameraGoal.Fit(bounds.expandBy(FRAME_MARGIN_RATIO))
  }

  companion object {
    /** Le zoom d'un centrage sur la position : le quartier, pas la rue ni la région. */
    const val NEARBY_ZOOM = 15.0

    /** Ni caméra mémorisée, ni position connue, ni serveur qui réponde : on montre la planète. */
    val WORLD_CAMERA = MapCamera(center = LatLon(lat = 20.0, lon = 0.0), zoom = 1.5)

    /** De combien on se rapproche à l'appui sur un groupe : assez pour qu'il éclate. */
    private const val CLUSTER_ZOOM_STEP = 2.0

    /** De l'air autour d'un trajet cadré : le tracé ne colle pas aux bords de la zone visible. */
    private const val FRAME_MARGIN_RATIO = 0.12

    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        MapViewModel(
          serverRepository = container.serverRepository,
          mapRepository = container.mapRepository,
          geocodeRepository = container.geocodeRepository,
          stopsRepository = container.stopsRepository,
          rentalsRepository = container.rentalsRepository,
          preferencesRepository = container.preferencesRepository,
          styles = container.mapStyles,
          cameraStore = container.mapCameraStore,
          locationSource = container.deviceLocationSource,
          selection = container.mapSelection,
          searchSession = container.searchSession,
          selectedJourneys = container.selectedJourneyStore,
          departureRequests = container.stopDepartureRequests,
        )
      }
    }
  }
}

/**
 * L'infobulle complétée par ce que `/api/v6/stop` a rendu.
 *
 * Un échec n'efface pas l'infobulle : le nom de l'arrêt reste juste, et [SelectedStop.linesFailed]
 * dit que les lignes manquent plutôt que de laisser croire que l'arrêt n'en dessert aucune
 * (SPEC.md § 8).
 */
private fun SelectedStop.withLines(outcome: Outcome<Stop>): SelectedStop = when (outcome) {
  is Outcome.Success -> copy(lines = outcome.value.lines, linesLoading = false, linesFailed = false)
  is Outcome.Failure -> copy(linesLoading = false, linesFailed = true)
}

/** Une famille de libre-service prête à poser : la source ordinaire et la source regroupante. */
private data class SourcePair(val plain: String, val clustered: String)

/** Les deux familles de libre-service, chacune sur son couple de sources. */
private data class RentalsDrawing(val stations: SourcePair, val vehicles: SourcePair)

/**
 * Les points de libre-service mis en GeoJSON, une paire de sources par famille.
 *
 * Fonction pure, hors de la classe comme `withLines` : elle ne lit rien de l'état et s'exécute sur
 * le répartiteur de calcul (SPEC.md § 5.7, règle 7).
 *
 * Le seuil de regroupement de la règle 6 s'applique **par famille** : stations et véhicules ne se
 * voient pas au même palier, et les mélanger dans une même source regroupante ferait apparaître au
 * zoom 13 des pastilles comptant des véhicules encore invisibles.
 *
 * Une famille absente de la réponse reçoit deux collections vides, ce qui l'efface sans démonter la
 * moindre couche (règle 8).
 */
private fun rentalsDrawing(markers: List<RentalMarker>): RentalsDrawing {
  val byKind = markers.groupBy { it.kind }
  return RentalsDrawing(
    stations = familyDrawing(byKind[RentalMarkerKind.STATION].orEmpty()),
    vehicles = familyDrawing(byKind[RentalMarkerKind.VEHICLE].orEmpty()),
  )
}

/** Une famille sur l'une de ses deux sources, jamais sur les deux : l'autre reçoit du vide. */
private fun familyDrawing(markers: List<RentalMarker>): SourcePair {
  val geoJson = MapGeoJson.rentals(markers)
  return if (shouldClusterRentals(markers.size)) {
    SourcePair(plain = MapGeoJson.EMPTY, clustered = geoJson)
  } else {
    SourcePair(plain = geoJson, clustered = MapGeoJson.EMPTY)
  }
}
