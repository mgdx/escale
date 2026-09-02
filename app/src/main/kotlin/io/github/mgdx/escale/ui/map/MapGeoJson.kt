package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.StopMarker
import io.github.mgdx.escale.core.geo.TraceMarker
import io.github.mgdx.escale.core.geo.TraceSegment
import io.github.mgdx.escale.core.model.LatLon
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * La conversion des points de la carte en GeoJSON (SPEC.md § 5.7, règles 6 et 7).
 *
 * Règle 6 : les marqueurs sont des **couches MapLibre alimentées par une source GeoJSON**, jamais
 * des vues Android superposées, qui écroulent la fluidité dès la centaine de marqueurs. Règle 7 :
 * la conversion se fait **hors du fil principal** et la source est mise à jour en une seule
 * opération — d'où des fonctions pures, que l'appelant exécute sur un répartiteur de calcul.
 *
 * Rien n'est journalisé ici, et rien ne doit l'être : une coordonnée est une donnée personnelle
 * (SPEC.md § 8 et § 11).
 */
object MapGeoJson {

  /** Une collection vide, l'état d'une source tant qu'elle n'a rien à montrer. */
  val EMPTY: String = featureCollection(emptyList())

  /** Une collection d'un seul point, ou vide si [point] est nul. */
  fun singlePoint(point: LatLon?): String = featureCollection(listOfNotNull(point))

  /**
   * Les portions d'un trajet, une `LineString` par portion (SPEC.md § 5.3).
   *
   * Chaque portion porte sa couleur — celle de la ligne quand le réseau la publie, celle de son
   * mode sinon —, la forme de son trait et son libellé : les couches n'ont plus qu'à lire ces
   * propriétés, sans jamais avoir à être reconstruites quand le trajet change.
   */
  fun journeyLines(segments: List<TraceSegment>): String = collection {
    for (segment in segments) {
      feature(
        properties = {
          put(PROPERTY_COLOR, segment.color ?: TracePalette.colorOf(segment.kind))
          segment.textColor?.let { put(PROPERTY_TEXT_COLOR, it) }
          put(PROPERTY_STROKE, segment.stroke.name)
          put(PROPERTY_LABEL, segment.label)
        },
        geometry = {
          put("type", "LineString")
          putJsonArray("coordinates") {
            for (point in segment.points) {
              addJsonArray {
                add(point.lon)
                add(point.lat)
              }
            }
          }
        },
      )
    }
  }

  /**
   * Les arrêts de la carte (SPEC.md § 5.7).
   *
   * Chaque entité porte tout ce dont les couches ont besoin pour se dessiner sans jamais être
   * reconstruites : son dessin ([PROPERTY_ICON]), le palier à partir duquel elle doit se voir
   * ([PROPERTY_TIER]) et son nom. [PROPERTY_STOP_ID] est ce que l'appui rend, pour aller chercher
   * les lignes desservies puis les prochains départs.
   */
  fun stops(markers: List<StopMarker>): String = collection {
    for (marker in markers) {
      feature(
        properties = {
          put(PROPERTY_STOP_ID, marker.id)
          put(PROPERTY_LABEL, marker.name)
          put(PROPERTY_TIER, marker.tier.name)
          put(PROPERTY_MODE, marker.mode.name)
          put(PROPERTY_ICON, StopIcon.of(marker.mode).imageId)
        },
        geometry = { point(marker.point) },
      )
    }
  }

  /** Les marqueurs de départ, d'arrivée et de correspondance d'un trajet (SPEC.md § 5.3). */
  fun journeyMarkers(markers: List<TraceMarker>): String = collection {
    for (marker in markers) {
      feature(
        properties = {
          put(PROPERTY_KIND, marker.kind.name)
          put(PROPERTY_LABEL, marker.label)
        },
        geometry = { point(marker.point) },
      )
    }
  }

  private fun featureCollection(points: List<LatLon>): String = collection {
    for (coordinates in points) {
      feature(properties = { }, geometry = { point(coordinates) })
    }
  }

  private fun collection(features: JsonArrayBuilder.() -> Unit): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") { features() }
  }.toString()

  /** Une entité GeoJSON : ses propriétés, que les couches liront, et sa géométrie. */
  private fun JsonArrayBuilder.feature(
    properties: JsonObjectBuilder.() -> Unit,
    geometry: JsonObjectBuilder.() -> Unit,
  ) {
    addJsonObject {
      put("type", "Feature")
      putJsonObject("properties") { properties() }
      putJsonObject("geometry") { geometry() }
    }
  }

  private fun JsonObjectBuilder.point(point: LatLon) {
    put("type", "Point")
    // GeoJSON ordonne les coordonnées en longitude puis latitude (RFC 7946 § 3.1.1).
    putJsonArray("coordinates") {
      add(point.lon)
      add(point.lat)
    }
  }

  /** Couleur du trait, au format `#RRGGBB`. */
  const val PROPERTY_COLOR = "color"

  /** Couleur du libellé de ligne. Absente quand le réseau ne publie pas de couleur. */
  const val PROPERTY_TEXT_COLOR = "textColor"

  /** Forme du trait : le nom d'une valeur de `TraceStroke`. */
  const val PROPERTY_STROKE = "stroke"

  /** Nom de la ligne, ou du lieu pour un marqueur. Vide quand il n'y en a pas. */
  const val PROPERTY_LABEL = "label"

  /** Nature du marqueur : le nom d'une valeur de `TraceMarkerKind`. */
  const val PROPERTY_KIND = "kind"

  /** Identifiant de l'arrêt, celui que `/api/v6/stop` et `/api/v6/stoptimes` attendent. */
  const val PROPERTY_STOP_ID = "stopId"

  /** Palier d'apparition de l'arrêt : le nom d'une valeur de `ZoomTier`. */
  const val PROPERTY_TIER = "tier"

  /** Mode principal desservi : le nom d'une valeur de `TransitMode`. */
  const val PROPERTY_MODE = "mode"

  /** Identifiant du dessin posé sur la feuille de style pour cet arrêt. */
  const val PROPERTY_ICON = "icon"
}
