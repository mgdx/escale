package io.github.mgdx.escale.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapLoadRules
import io.github.mgdx.escale.core.geo.MapViewport
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.poiCategory
import io.github.mgdx.escale.core.model.poiComplement
import io.github.mgdx.escale.core.model.poiTypeKey
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/** Ce que la carte peut signaler à l'écran qui l'héberge. */
@Immutable
data class MapCanvasActions(
  val onCameraIdle: (MapViewport, MapCamera) -> Unit,
  val onUserMovedCamera: () -> Unit,
  val onCameraTargetApplied: () -> Unit,
  val onLongClick: (LatLon) -> Unit,
  val onLoadFailed: () -> Unit,
  val onPick: (MapPickPurpose) -> Unit,
  val onDismissPick: () -> Unit,
)

/** Les couleurs des couches que l'application pose elle-même, prises au thème Material. */
@Immutable
data class MapOverlayColors(val position: Color, val picked: Color, val onOverlay: Color)

/**
 * La carte plein écran (SPEC.md § 5.1 et § 5.7).
 *
 * Le `MapView` n'est **pas** créé ici : il vient de [MapInstance] et survit à tous les écrans
 * (règle 8). Ce composable ne fait que l'attacher, lui poser des couches, écouter sa caméra, et
 * le détacher — jamais le détruire.
 *
 * @param contentPadding encarts système et hauteur de la feuille de résultats ouverte. Ils
 *   deviennent le `padding` de la caméra, ce qui fait qu'un cadrage de trajet tient compte de la
 *   feuille (règle 9).
 */
@Composable
fun MapCanvas(
  state: MapUiState,
  mapInstance: MapInstance,
  actions: MapCanvasActions,
  colors: MapOverlayColors,
  contentPadding: PaddingValues,
  modifier: Modifier = Modifier,
) {
  val map by mapInstance.map.collectAsStateWithLifecycle()
  var loadedStyle by remember { mutableStateOf<Style?>(null) }
  // Le trajet tracé est annoncé aux lecteurs d'écran : une carte muette ne dirait pas qu'elle vient
  // de changer de contenu (SPEC.md § 9).
  val label = stringResource(
    if (state.journeyTraced) R.string.map_content_description_journey else R.string.map_content_description,
  )
  val traceColors = mapTraceColors()
  val markerIcons = rememberTraceMarkerIcons(traceColors)
  val stopColors = mapStopColors()
  val stopIcons = rememberStopIcons(stopColors)
  val rentalColors = mapRentalColors()
  val rentalIcons = rememberRentalIcons(rentalColors)

  Box(modifier = modifier) {
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .semantics { contentDescription = label },
      factory = { mapInstance.view().detachedFromParent() },
      // Sortir de la composition détache la vue, il ne la détruit pas : c'est toute la règle 8.
      onRelease = { it.detachedFromParent() },
    )
    MapPickMenu(
      point = state.longPressPoint,
      offset = map.screenOffsetOf(state.longPressPoint),
      onPick = actions.onPick,
      onDismiss = actions.onDismissPick,
    )
    MapDetailCard(state = state, contentPadding = contentPadding)
  }

  BindMapViewLifecycle(mapInstance)

  LaunchedEffect(map, state.styleJson) {
    val target = map ?: return@LaunchedEffect
    val styleJson = state.styleJson ?: return@LaunchedEffect
    loadedStyle = null
    target.setStyle(Style.Builder().fromJson(styleJson)) { style ->
      // Le tracé d'abord, les arrêts par-dessus ses traits mais sous ses marqueurs, puis la
      // position de l'usager et le point choisi, qui restent au-dessus de tout.
      style.installJourneyTraceLayers(traceColors, markerIcons)
      style.installStopLayers(stopColors, stopIcons)
      style.installRentalLayers(rentalColors, rentalIcons)
      style.installOverlayLayers(colors)
      loadedStyle = style
    }
  }

  ApplyMapSources(loadedStyle, state)

  ApplyCameraTarget(map, state.cameraTarget, contentPadding, actions.onCameraTargetApplied)
  ApplyCameraPadding(map, contentPadding)
  BindMapListeners(mapInstance, map, actions, state.stopActions, state.rentalActions, state.placeActions)
}

/**
 * La fiche ouverte, s'il y en a une : celle d'un arrêt, d'un point de libre-service, ou d'un point
 * d'intérêt du fond de carte.
 *
 * Elles ne s'affichent **jamais ensemble** — le `ViewModel` ferme les autres en ouvrant l'une — et
 * se posent exactement au même endroit : au bas de la carte, au-dessus de ce que le remplissage
 * réserve à la feuille de résultats et aux encarts système. Une bulle ancrée sur le marqueur
 * sortirait de l'écran dès qu'on touche un point d'un bord, et davantage encore à 200 %
 * d'agrandissement (SPEC.md § 5.7 et § 9).
 */
@Composable
private fun BoxScope.MapDetailCard(state: MapUiState, contentPadding: PaddingValues) {
  val placement = Modifier
    .align(Alignment.BottomCenter)
    .padding(
      // Le haut : ce que la carte de recherche recouvre. En paysage, une fiche haute passait
      // sinon **derrière** elle, et son nom comme son adresse devenaient illisibles.
      top = contentPadding.calculateTopPadding() + StopCardMargin,
      // Le bas : la feuille de résultats, puis la place du cartouche d'attribution.
      bottom = contentPadding.calculateBottomPadding() + AttributionRoom,
    )
    .padding(horizontal = StopCardMargin)
    // Ce qui ne tient toujours pas se fait défilant plutôt que rogné : à 200 % d'agrandissement et
    // en paysage, aucune hauteur d'écran ne suffit, et un bouton hors de l'écran est un bouton
    // perdu (SPEC.md § 9).
    .verticalScroll(rememberScrollState())
  state.selectedStop?.let { stop ->
    MapStopCard(
      stop = stop,
      onDepartures = { state.stopActions.onDepartures(stop) },
      onDismiss = state.stopActions.onDismissStop,
      modifier = placement,
    )
  }
  state.selectedRental?.let { rental ->
    MapRentalCard(rental = rental, onDismiss = state.rentalActions.onDismissRental, modifier = placement)
  }
  state.selectedPlace?.let { place ->
    MapPlaceCard(
      place = place,
      onPick = state.placeActions.onPick,
      onDismiss = state.placeActions.onDismissPlace,
      modifier = placement,
    )
  }
}

/**
 * Pose le contenu de chaque source, et rien d'autre (SPEC.md § 5.7, règles 6, 7 et 8).
 *
 * **Une opération par source, jamais un ajout ni un retrait de couche.** Le GeoJSON arrive déjà
 * sérialisé par le `ViewModel`, hors du fil principal, et une collection vide efface un contenu
 * sans rien démonter. Des deux sources d'arrêts, une seule porte des entités à la fois : c'est
 * ainsi que le regroupement s'allume et s'éteint sans toucher aux couches.
 */
@Composable
private fun ApplyMapSources(style: Style?, state: MapUiState) {
  LaunchedEffect(style, state.userLocationGeoJson) {
    style?.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE)?.setGeoJson(state.userLocationGeoJson)
  }
  LaunchedEffect(style, state.pickedPointGeoJson) {
    style?.getSourceAs<GeoJsonSource>(PICKED_POINT_SOURCE)?.setGeoJson(state.pickedPointGeoJson)
  }
  LaunchedEffect(style, state.journeyLinesGeoJson) {
    style?.getSourceAs<GeoJsonSource>(JOURNEY_LINES_SOURCE)?.setGeoJson(state.journeyLinesGeoJson)
  }
  LaunchedEffect(style, state.journeyMarkersGeoJson) {
    style?.getSourceAs<GeoJsonSource>(JOURNEY_MARKERS_SOURCE)?.setGeoJson(state.journeyMarkersGeoJson)
  }
  LaunchedEffect(style, state.stopsGeoJson) {
    style?.getSourceAs<GeoJsonSource>(STOPS_SOURCE)?.setGeoJson(state.stopsGeoJson)
  }
  LaunchedEffect(style, state.clusteredStopsGeoJson) {
    style?.getSourceAs<GeoJsonSource>(STOPS_CLUSTERED_SOURCE)?.setGeoJson(state.clusteredStopsGeoJson)
  }
  // Le libre-service a deux familles, donc quatre sources : chacune se remplit et se vide seule,
  // sans jamais qu'une couche soit ajoutée ni retirée (SPEC.md § 5.7, règles 6 et 8).
  LaunchedEffect(style, state.rentalStationsGeoJson) {
    style?.getSourceAs<GeoJsonSource>(rentalSource(RentalMarkerKind.STATION))
      ?.setGeoJson(state.rentalStationsGeoJson)
  }
  LaunchedEffect(style, state.clusteredRentalStationsGeoJson) {
    style?.getSourceAs<GeoJsonSource>(rentalClusteredSource(RentalMarkerKind.STATION))
      ?.setGeoJson(state.clusteredRentalStationsGeoJson)
  }
  LaunchedEffect(style, state.rentalVehiclesGeoJson) {
    style?.getSourceAs<GeoJsonSource>(rentalSource(RentalMarkerKind.VEHICLE))
      ?.setGeoJson(state.rentalVehiclesGeoJson)
  }
  LaunchedEffect(style, state.clusteredRentalVehiclesGeoJson) {
    style?.getSourceAs<GeoJsonSource>(rentalClusteredSource(RentalMarkerKind.VEHICLE))
      ?.setGeoJson(state.clusteredRentalVehiclesGeoJson)
  }
  // Les points d'intérêt ne font l'objet d'aucune requête : ils sont déjà dans les tuiles, et la
  // feuille embarquée porte leurs douze couches avec le bon `minzoom` (SPEC.md § 5.7). Les douze
  // bascules de SPEC.md § 5.6 ne font que les allumer ou les éteindre, indépendamment du zoom, et
  // sans qu'une seule couche soit ajoutée ni retirée (règle 8).
  LaunchedEffect(style, state.visiblePoiCategories) {
    val loaded = style ?: return@LaunchedEffect
    POI_LAYERS.forEach { (category, layerId) ->
      val visible = category in state.visiblePoiCategories
      loaded.getLayer(layerId)?.setProperties(
        PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
      )
    }
  }
}

/**
 * Suit le cycle de vie de l'écran sans jamais détruire la carte.
 *
 * `onStart` / `onStop` mettent le rendu en pause et le reprennent ; `onDestroy` n'est appelé nulle
 * part, c'est précisément ce que la règle 8 exige.
 */
@Composable
private fun BindMapViewLifecycle(mapInstance: MapInstance) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, mapInstance) {
    val view = mapInstance.view()
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> view.onStart()
        Lifecycle.Event.ON_RESUME -> view.onResume()
        Lifecycle.Event.ON_PAUSE -> view.onPause()
        Lifecycle.Event.ON_STOP -> view.onStop()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // Quitter la carte pour un autre écran suspend le rendu ; l'instance, elle, reste vivante.
      val current = lifecycleOwner.lifecycle.currentState
      if (current.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
      if (current.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
    }
  }
}

/**
 * Applique un cadrage, sans jamais dépasser 500 ms d'animation (règle 9).
 *
 * Un cadrage par emprise — celui d'un trajet — reçoit exactement le même remplissage que celui que
 * [ApplyCameraPadding] pose en permanence : les encarts système et la hauteur de la feuille de
 * résultats ouverte. C'est ce qui fait qu'un trajet cadré tient dans la partie visible de la carte
 * et non sous la feuille (règle 9). L'air autour du tracé vient d'un élargissement de l'emprise,
 * calculé dans `:core`, et non d'un remplissage supplémentaire qui décalerait la caméra à chaque
 * changement de hauteur de feuille.
 */
@Composable
private fun ApplyCameraTarget(
  map: MapLibreMap?,
  target: CameraTarget?,
  contentPadding: PaddingValues,
  onApplied: () -> Unit,
) {
  val padding = rememberCameraPadding(contentPadding)
  LaunchedEffect(map, target?.token, padding) {
    val instance = map ?: return@LaunchedEffect
    val camera = target ?: return@LaunchedEffect
    val update = when (val goal = camera.goal) {
      is CameraGoal.Center -> CameraUpdateFactory.newLatLngZoom(
        LatLng(goal.camera.center.lat, goal.camera.center.lon),
        goal.camera.zoom,
      )

      is CameraGoal.Fit -> {
        // Réduit au besoin, pour qu'il reste toujours de quoi cadrer, même carte de recherche
        // visible et feuille dépliée (règle 9).
        val room = padding.fittedInto(instance.width, instance.height)
        CameraUpdateFactory.newLatLngBounds(
          goal.bounds.asLatLngBounds(),
          room.left,
          room.top,
          room.right,
          room.bottom,
        )
      }
    }
    if (camera.animated) {
      instance.easeCamera(update, MapLoadRules.MAX_CAMERA_ANIMATION_MILLIS)
    } else {
      instance.moveCamera(update)
    }
    onApplied()
  }
}

/** Le remplissage de la caméra en pixels : encarts système et éléments flottants de l'écran. */
@Composable
private fun rememberCameraPadding(contentPadding: PaddingValues): CameraPadding {
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  return remember(contentPadding, density, direction) {
    with(density) {
      CameraPadding(
        left = contentPadding.calculateStartPadding(direction).roundToPx(),
        top = contentPadding.calculateTopPadding().roundToPx(),
        right = contentPadding.calculateEndPadding(direction).roundToPx(),
        bottom = contentPadding.calculateBottomPadding().roundToPx(),
      )
    }
  }
}

/** Le `padding` de caméra : les encarts système et la feuille de résultats ouverte (règle 9). */
@Composable
private fun ApplyCameraPadding(map: MapLibreMap?, contentPadding: PaddingValues) {
  val padding = rememberCameraPadding(contentPadding)
  LaunchedEffect(map, padding) {
    val instance = map ?: return@LaunchedEffect
    // `MapLibreMap.setPadding` est déprécié : le remplissage fait désormais partie de la position
    // de caméra, et se pose donc par une mise à jour de caméra comme le reste.
    instance.moveCamera(
      CameraUpdateFactory.paddingTo(
        padding.left.toDouble(),
        padding.top.toDouble(),
        padding.right.toDouble(),
        padding.bottom.toDouble(),
      ),
    )
  }
}

/**
 * Branche les écouteurs de la carte, et les débranche en sortant.
 *
 * Sans ce débranchement, l'instance unique accumulerait les écouteurs de chaque passage sur
 * l'écran : c'est le revers de sa longévité.
 */
@Composable
private fun BindMapListeners(
  mapInstance: MapInstance,
  map: MapLibreMap?,
  actions: MapCanvasActions,
  stopActions: MapStopActions,
  rentalActions: MapRentalActions,
  placeActions: MapPlaceActions,
) {
  DisposableEffect(map, actions, stopActions, rentalActions, placeActions) {
    val instance = map
    val view = mapInstance.view()
    if (instance == null) return@DisposableEffect onDispose { }

    // Règle 1 : le chargement ne peut naître qu'à l'arrêt de la caméra, jamais pendant le geste.
    val idle = MapLibreMap.OnCameraIdleListener {
      val position = instance.cameraPosition
      val center = position.target ?: return@OnCameraIdleListener
      val bounds = instance.projection.visibleRegion.latLngBounds
      actions.onCameraIdle(
        MapViewport(visibleArea = bounds.asBoundingBox(), zoom = position.zoom),
        MapCamera(center = LatLon(center.latitude, center.longitude), zoom = position.zoom),
      )
    }
    val moveStarted = MapLibreMap.OnCameraMoveStartedListener { reason ->
      if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) actions.onUserMovedCamera()
    }
    val longClick = MapLibreMap.OnMapLongClickListener { point ->
      actions.onLongClick(LatLon(point.latitude, point.longitude))
      true
    }
    // Appui sur un arrêt, un point de libre-service, un groupe ou un point d'intérêt
    // (SPEC.md § 5.7). L'interrogation porte sur les seules couches qui portent un marqueur :
    // toucher une rue ou un bâtiment ne doit rien ouvrir.
    val click = MapLibreMap.OnMapClickListener { point ->
      when (val tap = instance.tapAt(instance.projection.toScreenLocation(point))) {
        is MapTap.OnStop -> stopActions.onStopClick(tap.stop)

        is MapTap.OnRental -> rentalActions.onRentalClick(tap.rental)

        is MapTap.OnCluster -> stopActions.onClusterClick(tap.point)

        is MapTap.OnPlace -> placeActions.onPlaceClick(tap.place)

        null -> {
          stopActions.onDismissStop()
          rentalActions.onDismissRental()
          placeActions.onDismissPlace()
        }
      }
      // Faux : l'appui reste disponible pour le reste de la carte, qui n'en fait rien aujourd'hui.
      false
    }
    val failed = MapView.OnDidFailLoadingMapListener { actions.onLoadFailed() }

    instance.addOnCameraIdleListener(idle)
    instance.addOnCameraMoveStartedListener(moveStarted)
    instance.addOnMapLongClickListener(longClick)
    instance.addOnMapClickListener(click)
    view.addOnDidFailLoadingMapListener(failed)

    onDispose {
      instance.removeOnCameraIdleListener(idle)
      instance.removeOnCameraMoveStartedListener(moveStarted)
      instance.removeOnMapLongClickListener(longClick)
      instance.removeOnMapClickListener(click)
      view.removeOnDidFailLoadingMapListener(failed)
    }
  }
}

/**
 * Pose les deux couches que l'application dessine elle-même : la position de l'usager et le point
 * choisi par appui long. Elles sont réinstallées à chaque chargement de feuille, puisqu'une
 * nouvelle feuille remplace toutes les couches.
 */
private fun Style.installOverlayLayers(colors: MapOverlayColors) {
  addSource(GeoJsonSource(USER_LOCATION_SOURCE))
  addLayer(
    CircleLayer(USER_LOCATION_HALO_LAYER, USER_LOCATION_SOURCE).withProperties(
      PropertyFactory.circleRadius(HALO_RADIUS),
      PropertyFactory.circleColor(colors.position.toArgb()),
      PropertyFactory.circleOpacity(HALO_OPACITY),
    ),
  )
  addLayer(
    CircleLayer(USER_LOCATION_DOT_LAYER, USER_LOCATION_SOURCE).withProperties(
      PropertyFactory.circleRadius(DOT_RADIUS),
      PropertyFactory.circleColor(colors.position.toArgb()),
      PropertyFactory.circleStrokeWidth(STROKE_WIDTH),
      PropertyFactory.circleStrokeColor(colors.onOverlay.toArgb()),
    ),
  )

  addSource(GeoJsonSource(PICKED_POINT_SOURCE))
  addLayer(
    CircleLayer(PICKED_POINT_LAYER, PICKED_POINT_SOURCE).withProperties(
      PropertyFactory.circleRadius(PICKED_RADIUS),
      PropertyFactory.circleColor(colors.picked.toArgb()),
      PropertyFactory.circleStrokeWidth(STROKE_WIDTH),
      PropertyFactory.circleStrokeColor(colors.onOverlay.toArgb()),
    ),
  )
}

/**
 * Les couleurs du tracé, prises au thème Material.
 *
 * Elles sont lues ici et non passées par l'écran : le tracé appartient au lot « carte », et
 * `HomeScreen` n'a pas à connaître la palette de couches qu'il ne dessine pas.
 */
@Composable
private fun mapTraceColors(): MapTraceColors = MapTraceColors(
  casing = MaterialTheme.colorScheme.surface,
  label = MaterialTheme.colorScheme.onSurface,
  labelHalo = MaterialTheme.colorScheme.surface,
  origin = MaterialTheme.colorScheme.primary,
  transfer = MaterialTheme.colorScheme.onSurface,
  destination = MaterialTheme.colorScheme.tertiary,
)

/** Ce qu'un appui sur la carte a atteint, quand il a atteint quelque chose. */
private sealed interface MapTap {
  data class OnStop(val stop: SelectedStop) : MapTap

  data class OnRental(val rental: SelectedRental) : MapTap

  data class OnCluster(val point: LatLon) : MapTap

  data class OnPlace(val place: SelectedPlace) : MapTap
}

/**
 * Ce que l'appui a atteint, ou `null` s'il n'a touché aucun marqueur.
 *
 * L'appui est élargi à un carré de [TAP_SLOP_PX] pixels de côté : un marqueur de 20 à 24 dp n'est
 * pas une cible de 48 dp, et SPEC.md § 9 impose que la cible tactile en soit une. Un marqueur
 * détaillé gagne toujours sur une pastille de regroupement dessinée sous lui.
 *
 * **L'ordre des trois recherches est la priorité de SPEC.md § 5.7** : un arrêt ou un point de
 * libre-service l'emporte sur un point d'intérêt posé au même endroit, et le groupe qui les
 * rassemble l'emporte aussi. Le point d'intérêt ne répond que si le doigt n'a touché rien d'autre.
 */
// La signature de `queryRenderedFeatures` est variadique : une poignée d'identifiants recopiés une
// fois par appui du doigt, le coût est nul et il n'y a pas d'autre appel possible.
@Suppress("SpreadOperator")
private fun MapLibreMap.tapAt(screen: PointF): MapTap? {
  val features = queryRenderedFeatures(screen.tapArea(), *TAPPABLE_LAYERS)
  val markers = features.filterNot { it.hasProperty(CLUSTER_COUNT_PROPERTY) }
  return markers.firstNotNullOfOrNull { it.toMarkerTap() }
    ?: features.firstOrNull { it.hasProperty(CLUSTER_COUNT_PROPERTY) }?.toClusterTap()
    ?: markers.firstNotNullOfOrNull { it.toPlaceTap() }
}

/** L'entité GeoJSON touchée, relue dans les termes de l'interface : un arrêt ou du libre-service. */
private fun Feature.toMarkerTap(): MapTap? =
  toSelectedStop()?.let(MapTap::OnStop) ?: toSelectedRental()?.let(MapTap::OnRental)

private fun Feature.toClusterTap(): MapTap? = (geometry() as? Point)
  ?.let { point -> MapTap.OnCluster(LatLon(lat = point.latitude(), lon = point.longitude())) }

/**
 * Le point d'intérêt touché, ou `null` si l'entité n'en est pas un.
 *
 * Tout est lu sur la tuile, y compris le type et son complément, dont la table vit dans `:core` :
 * la fiche s'ouvre donc immédiatement, et la seule chose qu'elle attend est son adresse.
 */
private fun Feature.toPlaceTap(): MapTap? {
  val point = geometry() as? Point ?: return null
  val shop = getStringProperty(OSM_SHOP)
  val amenity = getStringProperty(OSM_AMENITY)
  val tourism = getStringProperty(OSM_TOURISM)
  val historic = getStringProperty(OSM_HISTORIC)
  val manMade = getStringProperty(OSM_MAN_MADE)
  val category = poiCategory(shop, amenity, tourism, historic, manMade)
  val typeKey = poiTypeKey(shop, amenity, tourism, historic, manMade)
  // Une entité de la couche `pois` que la table ne sait ni classer ni nommer n'a rien à montrer.
  if (category == null && typeKey == null) return null
  return MapTap.OnPlace(
    SelectedPlace(
      point = LatLon(lat = point.latitude(), lon = point.longitude()),
      name = getStringProperty(OSM_NAME).orEmpty(),
      category = category,
      typeKey = typeKey,
      complement = poiComplement(
        typeKey = typeKey,
        cuisine = getStringProperty(OSM_CUISINE),
        atm = isYes(OSM_ATM),
        religion = getStringProperty(OSM_RELIGION),
        denomination = getStringProperty(OSM_DENOMINATION),
      ),
      houseNumber = getStringProperty(OSM_HOUSE_NUMBER)?.takeIf { it.isNotBlank() },
    ),
  )
}

/**
 * Une étiquette OpenStreetMap qui vaut « oui ».
 *
 * Les tuiles écrivent `atm=yes` en chaîne, mais rien n'interdit à un producteur de tuiles d'en faire
 * un booléen : les deux se lisent ici, et tout le reste vaut non.
 */
private fun Feature.isYes(property: String): Boolean =
  getBooleanProperty(property) ?: getStringProperty(property).equals("yes", ignoreCase = true)

/** L'arrêt touché, ou `null` si l'entité n'en est pas un. */
private fun Feature.toSelectedStop(): SelectedStop? {
  val id = getStringProperty(MapGeoJson.PROPERTY_STOP_ID) ?: return null
  return SelectedStop(
    id = id,
    name = getStringProperty(MapGeoJson.PROPERTY_LABEL).orEmpty(),
    mode = modeOf(getStringProperty(MapGeoJson.PROPERTY_MODE)),
  )
}

/**
 * La station ou le véhicule touché, ou `null` si l'entité n'en est pas un.
 *
 * Tout vient de l'entité : l'infobulle du § 5.7 — « nom, véhicules disponibles, places libres, lien
 * vers l'exploitant » — s'ouvre donc sans le moindre appel réseau.
 */
private fun Feature.toSelectedRental(): SelectedRental? {
  val id = getStringProperty(MapGeoJson.PROPERTY_RENTAL_ID) ?: return null
  return SelectedRental(
    id = id,
    name = getStringProperty(MapGeoJson.PROPERTY_LABEL).orEmpty(),
    kind = rentalKindOf(getStringProperty(MapGeoJson.PROPERTY_RENTAL_KIND)),
    icon = rentalIconOf(getStringProperty(MapGeoJson.PROPERTY_RENTAL_FORM)),
    vehiclesAvailable = countOf(MapGeoJson.PROPERTY_RENTAL_VEHICLES),
    docksAvailable = countOf(MapGeoJson.PROPERTY_RENTAL_DOCKS),
    isRenting = getBooleanProperty(MapGeoJson.PROPERTY_RENTAL_RENTING) ?: true,
    isReturning = getBooleanProperty(MapGeoJson.PROPERTY_RENTAL_RETURNING) ?: true,
    rentalUriAndroid = getStringProperty(MapGeoJson.PROPERTY_RENTAL_URI),
  )
}

/** Un compte porté par l'entité. Absent ou illisible, il vaut zéro plutôt que d'inventer. */
private fun Feature.countOf(property: String): Int = getNumberProperty(property)?.toInt() ?: 0

/** La famille écrite dans l'entité. Une valeur qu'on ne sait pas relire est un véhicule isolé. */
private fun rentalKindOf(name: String?): RentalMarkerKind =
  RentalMarkerKind.entries.firstOrNull { it.name == name } ?: RentalMarkerKind.VEHICLE

/** Le dessin écrit dans l'entité. Une valeur qu'on ne sait pas relire devient [RentalIcon.OTHER]. */
private fun rentalIconOf(name: String?): RentalIcon =
  RentalIcon.entries.firstOrNull { it.name == name } ?: RentalIcon.OTHER

/** Le mode écrit dans l'entité. Une valeur qu'on ne sait pas relire devient [TransitMode.OTHER]. */
private fun modeOf(name: String?): TransitMode =
  TransitMode.entries.firstOrNull { it.name == name } ?: TransitMode.OTHER

/** Le carré d'appui autour du doigt : une cible de 48 dp, pas un marqueur de 22 dp. */
private fun PointF.tapArea(): RectF = RectF(x - TAP_SLOP_PX, y - TAP_SLOP_PX, x + TAP_SLOP_PX, y + TAP_SLOP_PX)

/**
 * Les couleurs des arrêts, prises au thème Material.
 *
 * Elles sont lues ici et non passées par l'écran : les arrêts appartiennent au lot « carte », et
 * `HomeScreen` n'a pas à connaître la palette de couches qu'il ne dessine pas.
 */
@Composable
private fun mapStopColors(): MapStopColors = MapStopColors(
  plate = MaterialTheme.colorScheme.surface,
  onPlate = MaterialTheme.colorScheme.onSurfaceVariant,
  plateStroke = MaterialTheme.colorScheme.outlineVariant,
  label = MaterialTheme.colorScheme.onSurface,
  labelHalo = MaterialTheme.colorScheme.surface,
  cluster = MaterialTheme.colorScheme.primaryContainer,
  onCluster = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Les couleurs du libre-service, prises au thème Material.
 *
 * Elles diffèrent de celles des arrêts pour que l'œil sépare les familles d'un coup, mais **rien
 * n'y est porté par la seule couleur** (SPEC.md § 9) : c'est la forme de la pastille — disque,
 * carré arrondi, losange — et le pictogramme qui distinguent les trois familles, et la fiche qui
 * les nomme.
 */
@Composable
private fun mapRentalColors(): MapRentalColors = MapRentalColors(
  plate = MaterialTheme.colorScheme.surface,
  onPlate = MaterialTheme.colorScheme.onSurfaceVariant,
  plateStroke = MaterialTheme.colorScheme.outline,
  label = MaterialTheme.colorScheme.onSurface,
  labelHalo = MaterialTheme.colorScheme.surface,
  cluster = MaterialTheme.colorScheme.tertiaryContainer,
  onCluster = MaterialTheme.colorScheme.onTertiaryContainer,
)

/**
 * Toutes les couches auxquelles un appui peut répondre : arrêts, libre-service, points d'intérêt.
 *
 * Les pastilles de regroupement des deux premiers lots sont à la fin de leur propre liste : un
 * marqueur détaillé, où qu'il vienne, gagne donc l'appui sur un groupe dessiné sous lui. La
 * priorité entre familles, elle, ne tient pas à cet ordre mais à [tapAt].
 */
private val TAPPABLE_LAYERS: Array<String> =
  STOP_TAPPABLE_LAYERS + RENTAL_TAPPABLE_LAYERS + POI_TAPPABLE_LAYERS

/** L'emprise, dans le type de MapLibre. */
private fun BoundingBox.asLatLngBounds(): LatLngBounds = LatLngBounds.from(
  latNorth = max.lat,
  lonEast = max.lon,
  latSouth = min.lat,
  lonWest = min.lon,
)

/** L'emprise visible, dans le type de `:core`. */
private fun LatLngBounds.asBoundingBox(): BoundingBox = BoundingBox(
  min = LatLon(latitudeSouth, longitudeWest),
  max = LatLon(latitudeNorth, longitudeEast),
)

/** La position à l'écran d'un point de la carte, pour y ancrer le menu d'appui long. */
private fun MapLibreMap?.screenOffsetOf(point: LatLon?): IntOffset {
  if (this == null || point == null) return IntOffset.Zero
  val screen = projection.toScreenLocation(LatLng(point.lat, point.lon))
  return IntOffset(screen.x.toInt(), screen.y.toInt())
}

/**
 * Détache la vue de son parent avant de la rattacher ailleurs.
 *
 * Une vue retenue au-delà de la composition garde son ancien parent : la rattacher sans cela lève
 * une `IllegalStateException`.
 */
private fun MapView.detachedFromParent(): MapView = apply { (parent as? ViewGroup)?.removeView(this) }

/** Le menu contextuel d'appui long, ancré sur le point touché. */
@Composable
private fun MapPickMenu(point: LatLon?, offset: IntOffset, onPick: (MapPickPurpose) -> Unit, onDismiss: () -> Unit) {
  Box(modifier = Modifier.offset { offset }) {
    MapPickDropdown(expanded = point != null, onPick = onPick, onDismiss = onDismiss)
  }
}

private const val USER_LOCATION_SOURCE = "escale-user-location"
private const val USER_LOCATION_HALO_LAYER = "escale-user-location-halo"
private const val USER_LOCATION_DOT_LAYER = "escale-user-location-dot"
private const val PICKED_POINT_SOURCE = "escale-picked-point"
private const val PICKED_POINT_LAYER = "escale-picked-point-dot"

private const val HALO_RADIUS = 16f
private const val HALO_OPACITY = 0.2f
private const val DOT_RADIUS = 7f
private const val PICKED_RADIUS = 9f
private const val STROKE_WIDTH = 2.5f

/**
 * Les étiquettes OpenStreetMap que la couche `pois` des tuiles porte, et que la fiche relit.
 *
 * Elles ne sont pas des chaînes d'interface : ce sont les noms de champs du schéma Shortbread, que
 * ni la traduction ni le thème ne changent (docs/architecture.md § 3, règle 2).
 */
private const val OSM_NAME = "name"
private const val OSM_SHOP = "shop"
private const val OSM_AMENITY = "amenity"
private const val OSM_TOURISM = "tourism"
private const val OSM_HISTORIC = "historic"
private const val OSM_MAN_MADE = "man_made"
private const val OSM_CUISINE = "cuisine"
private const val OSM_ATM = "atm"
private const val OSM_RELIGION = "religion"
private const val OSM_DENOMINATION = "denomination"
private const val OSM_HOUSE_NUMBER = "housenumber"

/**
 * Demi-côté du carré d'appui, en pixels.
 *
 * Volontairement exprimé en pixels et non en dp : `queryRenderedFeatures` raisonne en pixels
 * d'écran, et 24 px valent une cible de 48 px de côté, soit 48 dp sur un écran de densité 1 et
 * davantage ailleurs — jamais moins que le minimum de SPEC.md § 9.
 */
private const val TAP_SLOP_PX = 24f

/** De l'air entre la fiche et les bords de l'écran. */
private val StopCardMargin = 12.dp

/**
 * La place que la fiche laisse au cartouche d'attribution, en bas à gauche de la carte.
 *
 * « Attribution OpenStreetMap visible en permanence » (SPEC.md § 4.2 et § 5.7) : ce n'est pas un
 * ornement qu'on peut recouvrir, c'est une obligation. Le cartouche se pose à 16 dp du bas et sa
 * ligne interactive fait 48 dp — `minimumInteractiveComponentSize` la lui garantit —, si bien que
 * la fiche doit s'arrêter 64 dp plus haut pour ne masquer ni la mention ni son point de contact.
 */
private val AttributionRoom = 64.dp
