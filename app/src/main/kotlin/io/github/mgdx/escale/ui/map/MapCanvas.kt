package io.github.mgdx.escale.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

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
  }

  BindMapViewLifecycle(mapInstance)

  LaunchedEffect(map, state.styleJson) {
    val target = map ?: return@LaunchedEffect
    val styleJson = state.styleJson ?: return@LaunchedEffect
    loadedStyle = null
    target.setStyle(Style.Builder().fromJson(styleJson)) { style ->
      // Le tracé d'abord : la position de l'usager et le point choisi restent au-dessus de lui.
      style.installJourneyTraceLayers(traceColors, markerIcons)
      style.installOverlayLayers(colors)
      loadedStyle = style
    }
  }

  // Deux sources GeoJSON, jamais des vues Android superposées (règle 6). Le GeoJSON arrive déjà
  // sérialisé par le ViewModel, hors du fil principal (règle 7).
  LaunchedEffect(loadedStyle, state.userLocationGeoJson) {
    loadedStyle?.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE)?.setGeoJson(state.userLocationGeoJson)
  }
  LaunchedEffect(loadedStyle, state.pickedPointGeoJson) {
    loadedStyle?.getSourceAs<GeoJsonSource>(PICKED_POINT_SOURCE)?.setGeoJson(state.pickedPointGeoJson)
  }
  // Le tracé du trajet : une opération par source, jamais un ajout ni un retrait de couche
  // (SPEC.md § 5.7, règles 7 et 8). Un trajet désélectionné y pose une collection vide.
  LaunchedEffect(loadedStyle, state.journeyLinesGeoJson) {
    loadedStyle?.getSourceAs<GeoJsonSource>(JOURNEY_LINES_SOURCE)?.setGeoJson(state.journeyLinesGeoJson)
  }
  LaunchedEffect(loadedStyle, state.journeyMarkersGeoJson) {
    loadedStyle?.getSourceAs<GeoJsonSource>(JOURNEY_MARKERS_SOURCE)?.setGeoJson(state.journeyMarkersGeoJson)
  }

  ApplyCameraTarget(map, state.cameraTarget, contentPadding, actions.onCameraTargetApplied)
  ApplyCameraPadding(map, contentPadding)
  BindMapListeners(mapInstance, map, actions)
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
private fun BindMapListeners(mapInstance: MapInstance, map: MapLibreMap?, actions: MapCanvasActions) {
  DisposableEffect(map, actions) {
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
    val failed = MapView.OnDidFailLoadingMapListener { actions.onLoadFailed() }

    instance.addOnCameraIdleListener(idle)
    instance.addOnCameraMoveStartedListener(moveStarted)
    instance.addOnMapLongClickListener(longClick)
    view.addOnDidFailLoadingMapListener(failed)

    onDispose {
      instance.removeOnCameraIdleListener(idle)
      instance.removeOnCameraMoveStartedListener(moveStarted)
      instance.removeOnMapLongClickListener(longClick)
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
