package io.github.mgdx.escale.ui.map

import androidx.compose.runtime.Stable
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.geo.MapDataRequest
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.geo.StopMarker
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiComplement
import io.github.mgdx.escale.core.model.PoiTypeKey
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.TransitMode

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
 * Ce que la caméra doit montrer : un point à une échelle, ou une emprise à faire tenir à l'écran.
 */
sealed interface CameraGoal {
  /** Se poser sur un point, à une échelle donnée : cadrage initial, recentrage sur la position. */
  data class Center(val camera: MapCamera) : CameraGoal

  /**
   * Faire tenir une emprise à l'écran : c'est le cadrage d'un trajet (SPEC.md § 5.3).
   *
   * Le remplissage n'est pas ici : il est appliqué au moment du cadrage, à partir des encarts
   * système et de la hauteur de la feuille de résultats ouverte (SPEC.md § 5.7, règle 9).
   */
  data class Fit(val bounds: BoundingBox) : CameraGoal
}

/**
 * Un cadrage que la caméra doit prendre.
 *
 * [animated] est faux pour le cadrage initial, qui doit être en place avant la première image, et
 * vrai pour un recentrage demandé par l'usager ou un cadrage de trajet. L'animation ne dépasse
 * jamais 500 ms (SPEC.md § 5.7, règle 9).
 */
data class CameraTarget(val goal: CameraGoal, val animated: Boolean, val token: Long) {
  /** Le point visé, quand le cadrage en vise un. Nul pour un cadrage par emprise. */
  val camera: MapCamera? get() = (goal as? CameraGoal.Center)?.camera
}

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

  /**
   * Les portions du trajet sélectionné, en GeoJSON prêt à poser sur la source (SPEC.md § 5.3).
   *
   * Vaut [MapGeoJson.EMPTY] quand aucun trajet n'est sélectionné : poser une collection vide efface
   * le tracé sans démonter la moindre couche (SPEC.md § 5.7, règle 8).
   */
  val journeyLinesGeoJson: String = MapGeoJson.EMPTY,

  /** Les marqueurs de départ, de correspondance et d'arrivée du trajet, en GeoJSON. */
  val journeyMarkersGeoJson: String = MapGeoJson.EMPTY,

  /** Vrai quand un trajet est tracé : la carte l'annonce alors aux lecteurs d'écran (SPEC.md § 9). */
  val journeyTraced: Boolean = false,

  /** Le point sur lequel le menu « Partir d'ici » / « Aller ici » est ouvert. */
  val longPressPoint: LatLon? = null,

  /** La permission à demander, acquittée par [MapViewModel.onPermissionRequestLaunched]. */
  val permissionRequest: PermissionRequest? = null,

  /** Permission refusée : une phrase d'explication et le chemin vers les réglages système. */
  val permissionExplanationVisible: Boolean = false,

  /** La fiche d'attribution, ouverte depuis le bouton d'attribution (SPEC.md § 4.2). */
  val attributionVisible: Boolean = false,

  /**
   * La dernière requête d'arrêts émise (SPEC.md § 5.7).
   *
   * Calculée par `:core` — anti-rebond de 300 ms, emprise élargie de 30 %, rien sous le zoom 11 —
   * puis envoyée à `StopsRepository`. Le champ reste exposé parce qu'il dit, sans journaliser quoi
   * que ce soit, quel palier est en service : c'est ce qui se vérifie en test.
   */
  val plannedRequest: MapDataRequest? = null,

  /**
   * Les arrêts posés sur la source ordinaire, en GeoJSON prêt à l'emploi (SPEC.md § 5.7).
   *
   * Vaut [MapGeoJson.EMPTY] quand le regroupement est en service, ou quand le réglage « arrêts »
   * est décoché : poser une collection vide efface les marqueurs sans démonter la moindre couche
   * (règle 8).
   */
  val stopsGeoJson: String = MapGeoJson.EMPTY,

  /** Les mêmes arrêts, mais sur la source regroupante, au-delà de 200 points (règle 6). */
  val clusteredStopsGeoJson: String = MapGeoJson.EMPTY,

  /**
   * Les catégories de points d'intérêt visibles sur la carte (SPEC.md § 5.6 et § 5.7).
   *
   * « Un réglage permet de masquer complètement les arrêts, les stations en libre-service ou les
   * points d'intérêt, **indépendamment du zoom** » : chacune des douze catégories se règle
   * séparément. Aucune requête n'est en jeu — les points d'intérêt sont déjà dans les tuiles —, et
   * cet ensemble ne fait qu'allumer ou éteindre les douze couches de la feuille de style.
   */
  val visiblePoiCategories: Set<PoiCategory> = PoiCategory.DEFAULT_VISIBLE,

  /**
   * Les stations de libre-service posées sur leur source ordinaire (SPEC.md § 5.7).
   *
   * Vaut [MapGeoJson.EMPTY] quand le regroupement est en service, ou quand le réglage « stations en
   * libre-service » est décoché : poser une collection vide efface les marqueurs sans démonter la
   * moindre couche (règle 8).
   */
  val rentalStationsGeoJson: String = MapGeoJson.EMPTY,

  /** Les mêmes stations, sur leur source regroupante, au-delà de 200 points (règle 6). */
  val clusteredRentalStationsGeoJson: String = MapGeoJson.EMPTY,

  /**
   * Les véhicules en libre-service isolés, sur leur source ordinaire (SPEC.md § 5.7).
   *
   * Ils ne se voient qu'à partir du zoom 15, ce que porte le `minzoom` de leur couche et non cet
   * état : la source reste garnie en dézoomant, la couche s'éteint, et **aucune requête ne part**
   * en remontant (règle 5).
   */
  val rentalVehiclesGeoJson: String = MapGeoJson.EMPTY,

  /** Les mêmes véhicules, sur leur source regroupante (règle 6). */
  val clusteredRentalVehiclesGeoJson: String = MapGeoJson.EMPTY,

  /**
   * Les arrêts que la carte montre, à parcourir en liste (SPEC.md § 9).
   *
   * MapLibre dessine ses marqueurs dans une vue unique : ils ne sont pas atteignables un à un au
   * lecteur d'écran, et la règle 6 du § 5.7 interdit d'en faire des vues Android. Cette liste est
   * la voie d'accès de remplacement, et elle mène à la **même** infobulle qu'un appui sur la carte.
   *
   * Elle est calculée par `:core` — palier, emprise visible, proximité au centre, plafond — et
   * remise à jour au seul arrêt de la caméra, comme les marqueurs eux-mêmes (règle 1).
   */
  val browsableStops: List<SelectedStop> = emptyList(),

  /** Vrai quand la carte montre plus d'arrêts que la liste n'en énonce (`MAX_BROWSABLE_STOPS`). */
  val browsableStopsTruncated: Boolean = false,

  /** L'infobulle ouverte sur un arrêt, ou `null` si aucune ne l'est (SPEC.md § 5.7). */
  val selectedStop: SelectedStop? = null,

  /** L'infobulle ouverte sur une station ou un véhicule en libre-service (SPEC.md § 5.7). */
  val selectedRental: SelectedRental? = null,

  /** La fiche ouverte sur un point d'intérêt du fond de carte (SPEC.md § 5.7). */
  val selectedPlace: SelectedPlace? = null,

  /** Ce que la carte doit faire d'un appui sur un arrêt. Voir [MapStopActions]. */
  val stopActions: MapStopActions = MapStopActions.Inert,

  /** Ce que la carte doit faire d'un appui sur un point de libre-service. */
  val rentalActions: MapRentalActions = MapRentalActions.Inert,

  /** Ce que la carte doit faire d'un appui sur un point d'intérêt. Voir [MapPlaceActions]. */
  val placeActions: MapPlaceActions = MapPlaceActions.Inert,
) {
  /**
   * Une fiche est-elle ouverte au bas de la carte, quelle que soit sa famille ?
   *
   * Les trois se posent au même endroit et ne s'affichent jamais ensemble. Les commandes flottantes,
   * elles, occupent le même bas d'écran : c'est ce booléen qui les efface le temps d'une fiche, sans
   * quoi l'engrenage et le bouton de position recouvriraient ses boutons — une cible tactile qui
   * n'en est plus une (SPEC.md § 9).
   */
  val detailCardOpen: Boolean get() = selectedStop != null || selectedRental != null || selectedPlace != null
}

/**
 * Les rappels d'interaction sur les arrêts de la carte (SPEC.md § 5.7).
 *
 * Ils voyagent avec l'état plutôt que dans `MapCanvasActions`, et c'est un choix explicite :
 * `MapCanvasActions` est assemblé par l'écran qui **héberge** la carte, lequel n'a pas à connaître
 * une fonction de carte de plus à chaque jalon. Le lot « carte » possède à la fois le `ViewModel`
 * et le canevas ; leur couture n'a pas à passer par leur hôte.
 *
 * L'instance est créée **une seule fois** par le `ViewModel` et ne change jamais : l'égalité de
 * [MapUiState] reste donc celle de ses données, et aucune recomposition n'est déclenchée par ce
 * champ. [Inert] est la valeur d'un état construit hors du `ViewModel`, dans un aperçu ou un test
 * de rendu : la carte s'affiche, elle ne répond simplement pas.
 */
@Stable
class MapStopActions(
  val onStopClick: (SelectedStop) -> Unit,
  val onClusterClick: (LatLon) -> Unit,
  val onDismissStop: () -> Unit,
  val onDepartures: (SelectedStop) -> Unit,
) {
  companion object {
    val Inert = MapStopActions(onStopClick = {}, onClusterClick = {}, onDismissStop = {}, onDepartures = {})
  }
}

/**
 * L'arrêt sur lequel l'infobulle est ouverte (SPEC.md § 5.7).
 *
 * « Appui sur un arrêt : infobulle avec le nom et les lignes desservies, et un bouton menant aux
 * prochains départs. » Le nom et le mode viennent de l'entité touchée, donc immédiatement ; les
 * lignes demandent un appel à `/api/v6/stop`, d'où les trois états [linesLoading], [lines] et
 * [linesFailed].
 */
data class SelectedStop(
  val id: String,
  val name: String,
  /** Mode principal, celui qui a donné son dessin au marqueur. */
  val mode: TransitMode,
  val lines: List<StopLine> = emptyList(),
  val linesLoading: Boolean = true,
  /** Les lignes n'ont pas pu être lues : l'infobulle le dit, elle ne fait pas semblant. */
  val linesFailed: Boolean = false,
)

/**
 * L'arrêt à ouvrir depuis le parcours en liste (SPEC.md § 9).
 *
 * Le marqueur porte déjà tout ce qu'une infobulle affiche d'emblée — l'identifiant, le nom, le mode
 * qui lui a donné son dessin. Passer par [SelectedStop] fait que la liste et l'appui sur la carte
 * mènent **exactement** au même endroit, avec le même appel de lignes derrière.
 */
internal fun StopMarker.toSelectedStop(): SelectedStop = SelectedStop(id = id, name = name, mode = mode)

/**
 * Les rappels d'interaction sur les points de libre-service (SPEC.md § 5.7).
 *
 * Même dispositif que [MapStopActions], et pour la même raison : le lot « carte » possède à la fois
 * le `ViewModel` et le canevas, leur couture n'a pas à passer par l'écran qui les héberge.
 * L'instance est créée une seule fois et ne change jamais, si bien qu'elle n'entre dans aucune
 * comparaison d'état.
 *
 * L'appui sur une pastille de regroupement n'est pas ici : rapprocher la caméra est un comportement
 * de la carte et non du libre-service, et [MapStopActions.onClusterClick] le porte déjà pour les
 * deux familles de sources regroupantes.
 */
@Stable
class MapRentalActions(val onRentalClick: (SelectedRental) -> Unit, val onDismissRental: () -> Unit) {
  companion object {
    val Inert = MapRentalActions(onRentalClick = {}, onDismissRental = {})
  }
}

/**
 * La station ou le véhicule sur lequel l'infobulle est ouverte (SPEC.md § 5.7).
 *
 * « Appui sur une station de libre-service : nom, véhicules disponibles, places libres, lien vers
 * l'exploitant. » Tout est déjà porté par l'entité GeoJSON touchée : l'infobulle s'affiche sans le
 * moindre appel réseau, à la différence de celle d'un arrêt qui doit aller chercher ses lignes.
 *
 * [retrievedAt] n'y figure pas : la donnée vient de la réponse qui a peuplé la source, dont la
 * fraîcheur est garantie par le cache de 60 secondes de `RentalsCache` (règle 4).
 */
data class SelectedRental(
  val id: String,
  val name: String,
  val kind: RentalMarkerKind,
  /** Le type de véhicule dessiné, qui donne aussi son libellé lu à voix haute (SPEC.md § 9). */
  val icon: RentalIcon,
  val vehiclesAvailable: Int,
  val docksAvailable: Int,
  val isRenting: Boolean,
  val isReturning: Boolean,
  /** Lien profond vers l'exploitant, ouvert en intent externe et jamais en WebView (SPEC.md § 2). */
  val rentalUriAndroid: String? = null,
)

/**
 * Les rappels d'interaction sur les points d'intérêt du fond de carte (SPEC.md § 5.7).
 *
 * Même dispositif que [MapStopActions] et [MapRentalActions], et pour la même raison : le lot
 * « carte » possède à la fois le `ViewModel` et le canevas, leur couture n'a pas à passer par
 * l'écran qui les héberge. L'instance est créée une seule fois et n'entre donc dans aucune
 * comparaison d'état.
 *
 * [onPick] mène exactement où mène l'appui long sur la carte : le point est déposé dans
 * `MapSelection`, que le lot « recherche » consomme.
 */
@Stable
class MapPlaceActions(
  val onPlaceClick: (SelectedPlace) -> Unit,
  val onDismissPlace: () -> Unit,
  val onPick: (MapPickPurpose) -> Unit,
) {
  companion object {
    val Inert = MapPlaceActions(onPlaceClick = {}, onDismissPlace = {}, onPick = {})
  }
}

/**
 * Le point d'intérêt sur lequel la fiche est ouverte (SPEC.md § 5.7).
 *
 * Tout vient de l'entité touchée, sauf l'adresse : la tuile ne porte que le numéro de voie, et la
 * rue demande un géocodage inverse. C'est la **seule** requête que les points d'intérêt provoquent,
 * une par fiche, annulée à la fermeture (SPEC.md § 7, règle 11).
 *
 * Aucun de ces champs n'est journalisé, pas plus que la position : SPEC.md § 8 et § 11 l'interdisent
 * y compris en compilation de débogage.
 */
data class SelectedPlace(
  val point: LatLon,
  /** Le nom porté par la tuile, vide quand le lieu n'en a pas — beaucoup n'en ont pas. */
  val name: String = "",
  /** La catégorie, qui nomme le lieu à défaut de mieux. */
  val category: PoiCategory? = null,
  /** Le type précis, quand la table le nomme : « Boulangerie », « Lieu de culte ». */
  val typeKey: PoiTypeKey? = null,
  /** Ce qui suit le type : la cuisine, la confession, ou le distributeur de billets. */
  val complement: PoiComplement? = null,
  /** Le numéro de voie de la tuile, qui tient lieu d'adresse en attendant la vraie. */
  val houseNumber: String? = null,
  /** L'adresse rendue par le géocodage inverse, nulle tant qu'elle n'est pas arrivée ou connue. */
  val address: Location? = null,
  /** Vrai tant que la requête d'adresse est en vol : la fiche s'ouvre sans l'attendre. */
  val addressLoading: Boolean = true,
)
