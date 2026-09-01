package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.LatLon
import kotlinx.serialization.json.add
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
 * opération — d'où une fonction pure, que l'appelant exécute sur un répartiteur de calcul.
 *
 * Rien n'est journalisé ici, et rien ne doit l'être : une coordonnée est une donnée personnelle
 * (SPEC.md § 8 et § 11).
 */
object MapGeoJson {

  /** Une collection vide, l'état d'une source tant qu'elle n'a rien à montrer. */
  val EMPTY: String = featureCollection(emptyList())

  /** Une collection d'un seul point, ou vide si [point] est nul. */
  fun singlePoint(point: LatLon?): String = featureCollection(listOfNotNull(point))

  private fun featureCollection(points: List<LatLon>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
      for (point in points) {
        addJsonObject {
          put("type", "Feature")
          putJsonObject("properties") { }
          putJsonObject("geometry") {
            put("type", "Point")
            // GeoJSON ordonne les coordonnées en longitude puis latitude (RFC 7946 § 3.1.1).
            putJsonArray("coordinates") {
              add(point.lon)
              add(point.lat)
            }
          }
        }
      }
    }
  }.toString()
}
