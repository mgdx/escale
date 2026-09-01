package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapDataRequest
import io.github.mgdx.escale.core.model.LatLon

/** Les trois états du bouton de position (SPEC.md § 5.1). */
enum class LocateState {
  /** Aucune position connue : permission jamais accordée, refusée, ou aucun point encore reçu. */
  UNKNOWN,

  /** La carte a été centrée sur la position, mais ne la suit pas. */
  CENTERED,

  /** La caméra suit la position à chaque nouveau point. */
  FOLLOWING,
}

/** Les deux permissions de localisation, demandées dans cet ordre et jamais au démarrage. */
enum class LocationPermission {
  /** `ACCESS_COARSE_LOCATION`, demandée au premier appui sur le bouton de position. */
  COARSE,

  /** `ACCESS_FINE_LOCATION`, demandée seulement si l'usager insiste pour un centrage précis. */
  FINE,
}

/**
 * Une demande de permission à lancer.
 *
 * [token] change à chaque demande : sans lui, deux demandes successives de la même permission
 * seraient égales et la seconde ne déclencherait rien.
 */
data class PermissionRequest(val permission: LocationPermission, val token: Long)

/**
 * Un cadrage que la caméra doit prendre.
 *
 * [animated] est faux pour le cadrage initial, qui doit être en place avant la première image, et
 * vrai pour un recentrage demandé par l'usager. L'animation ne dépasse jamais 500 ms
 * (SPEC.md § 5.7, règle 9).
 */
data class CameraTarget(val camera: MapCamera, val animated: Boolean, val token: Long)

/**
 * Tout ce que l'écran de carte a à afficher, en une seule `data class` exposée en `StateFlow`
 * (docs/architecture.md § 8).
 */
data class MapUiState(
  /** La feuille de style à charger, nulle tant qu'elle n'est pas prête. */
  val styleJson: String? = null,

  /** Le serveur ne sert pas de fond de carte : fond neutre et message discret (SPEC.md § 5.7). */
  val tilesUnavailable: Boolean = false,

  /** Le cadrage à appliquer, acquitté par [MapViewModel.onCameraTargetApplied]. */
  val cameraTarget: CameraTarget? = null,

  val locateState: LocateState = LocateState.UNKNOWN,

  /** La position de l'usager, en GeoJSON prêt à poser sur la source (SPEC.md § 5.7, règles 6 et 7). */
  val userLocationGeoJson: String = MapGeoJson.EMPTY,

  /** Le point d'appui long, en GeoJSON. */
  val pickedPointGeoJson: String = MapGeoJson.EMPTY,

  /** Le point sur lequel le menu « Partir d'ici » / « Aller ici » est ouvert. */
  val longPressPoint: LatLon? = null,

  /** La permission à demander, acquittée par [MapViewModel.onPermissionRequestLaunched]. */
  val permissionRequest: PermissionRequest? = null,

  /** Permission refusée : une phrase d'explication et le chemin vers les réglages système. */
  val permissionExplanationVisible: Boolean = false,

  /** La fiche d'attribution, ouverte depuis le bouton d'attribution (SPEC.md § 4.2). */
  val attributionVisible: Boolean = false,

  /**
   * La requête d'arrêts que le palier et l'emprise courants justifient (SPEC.md § 5.7).
   *
   * Calculée dès maintenant par `:core` — anti-rebond de 300 ms, emprise élargie de 30 %, rien
   * sous le zoom 11 — mais pas encore envoyée : `/api/v6/map/stops` est du ressort du jalon 6, qui
   * n'aura qu'à brancher `StopsRepository` sur ce champ.
   */
  val plannedRequest: MapDataRequest? = null,
)
