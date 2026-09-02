package io.github.mgdx.escale.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
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
    // L'infobulle se pose au bas de la carte, au-dessus de ce que le remplissage réserve à la
    // feuille de résultats et aux encarts système (SPEC.md § 5.7 et § 9).
    state.selectedStop?.let { stop ->
      MapStopCard(
        stop = stop,
        onDepartures = { state.stopActions.onDepartures(stop) },
        onDismiss = state.stopActions.onDismissStop,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = contentPadding.calculateBottomPadding())
          .padding(StopCardMargin),
      )
    }
  }

  BindMapViewLifecycle(mapInstance)

  LaunchedEffect(map, state.styleJson) {
    val target = map ?: return@LaunchedEffect
    val styleJson = state.styleJson ?: return@LaunchedEffect
    loadedStyle = null
    target.setStyle(Style.Builder().fromJson(styleJson)) { style ->
      // Le tracé d'abord, les arrêts par-dessus, puis la position de l'usager et le point choisi,
      // qui restent au-dessus de tout.
      style.installJourneyTraceLayers(traceColors, markerIcons)
      style.installStopLayers(stopColors, stopIcons)
      style.installOverlayLayers(colors)
      loadedStyle = style
    }
  }

  ApplyMapSources(loadedStyle, state)

  ApplyCameraTarget(map, state.cameraTarget, contentPadding, actions.onCameraTargetApplied)
  ApplyCameraPadding(map, contentPadding)
  BindMapListeners(mapInstance, map, actions, state.stopActions)
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
  // Les points d'intérêt ne font l'objet d'aucune requête : ils sont déjà dans les tuiles, et la
  // feuille embarquée porte leur couche avec le bon `minzoom` (SPEC.md § 5.7). Le réglage de
  // SPEC.md § 5.6 ne fait que l'allumer ou l'éteindre, indépendamment du zoom.
  LaunchedEffect(style, state.pointsOfInterestVisible) {
    style?.getLayer(POINTS_OF_INTEREST_LAYER)?.setProperties(
      PropertyFactory.visibility(if (state.pointsOfInterestVisible) Property.VISIBLE else Property.NONE),
    )
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
) {
  DisposableEffect(map, actions, stopActions) {
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
    // Appui sur un arrêt ou sur un groupe (SPEC.md § 5.7). L'interrogation porte sur les seules
    // couches d'arrêts : toucher une rue ou un bâtiment ne doit rien ouvrir.
    val click = MapLibreMap.OnMapClickListener { point ->
      when (val tap = instance.tapAt(instance.projection.toScreenLocation(point))) {
        is MapTap.OnStop -> stopActions.onStopClick(tap.stop)
        is MapTap.OnCluster -> stopActions.onClusterClick(tap.point)
        null -> stopActions.onDismissStop()
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

  data class OnCluster(val point: LatLon) : MapTap
}

/**
 * Ce que l'appui a atteint, ou `null` s'il n'a touché aucun marqueur d'arrêt.
 *
 * L'appui est élargi à un carré de [TAP_SLOP_PX] pixels de côté : un marqueur de 22 dp n'est pas
 * une cible de 48 dp, et SPEC.md § 9 impose que la cible tactile en soit une. Un arrêt gagne
 * toujours sur une pastille de regroupement dessinée sous lui.
 */
// La signature de `queryRenderedFeatures` est variadique : cinq identifiants recopiés une fois par
// appui du doigt, le coût est nul et il n'y a pas d'autre appel possible.
@Suppress("SpreadOperator")
private fun MapLibreMap.tapAt(screen: PointF): MapTap? {
  val features = queryRenderedFeatures(screen.tapArea(), *STOP_TAPPABLE_LAYERS)
  features.firstOrNull { !it.hasProperty(CLUSTER_COUNT_PROPERTY) }
    ?.toSelectedStop()
    ?.let { return MapTap.OnStop(it) }
  return features.firstOrNull { it.hasProperty(CLUSTER_COUNT_PROPERTY) }
    ?.let { feature -> feature.geometry() as? Point }
    ?.let { point -> MapTap.OnCluster(LatLon(lat = point.latitude(), lon = point.longitude())) }
}

/** L'entité GeoJSON touchée, relue dans les termes de l'interface. */
private fun Feature.toSelectedStop(): SelectedStop? {
  val id = getStringProperty(MapGeoJson.PROPERTY_STOP_ID) ?: return null
  return SelectedStop(
    id = id,
    name = getStringProperty(MapGeoJson.PROPERTY_LABEL).orEmpty(),
    mode = modeOf(getStringProperty(MapGeoJson.PROPERTY_MODE)),
  )
}

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

/** La couche de points d'intérêt des feuilles de `res/raw`, que le réglage allume ou éteint. */
private const val POINTS_OF_INTEREST_LAYER = "poi-landmarks"

/**
 * Demi-côté du carré d'appui, en pixels.
 *
 * Volontairement exprimé en pixels et non en dp : `queryRenderedFeatures` raisonne en pixels
 * d'écran, et 24 px valent une cible de 48 px de côté, soit 48 dp sur un écran de densité 1 et
 * davantage ailleurs — jamais moins que le minimum de SPEC.md § 9.
 */
private const val TAP_SLOP_PX = 24f

/** De l'air entre l'infobulle et les bords de l'écran. */
private val StopCardMargin = 12.dp
