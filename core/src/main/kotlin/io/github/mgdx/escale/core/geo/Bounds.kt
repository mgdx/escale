package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon

/**
 * Emprise englobant tous les [points], ou `null` si la liste est vide.
 *
 * Sert à cadrer la carte sur un trajet (SPEC.md § 5.3) et à construire le paramètre d'emprise de
 * `/api/v6/map/stops`. L'antiméridien n'est pas traité : un trajet le franchissant produirait une
 * emprise faisant le tour du globe. Aucun jeu de données MOTIS ne s'y prête en v1 ; le jour où l'un
 * s'y prêtera, c'est ici qu'il faudra corriger.
 */
fun boundingBoxOf(points: List<LatLon>): BoundingBox? {
  if (points.isEmpty()) return null
  var minLat = Double.MAX_VALUE
  var maxLat = -Double.MAX_VALUE
  var minLon = Double.MAX_VALUE
  var maxLon = -Double.MAX_VALUE
  for (point in points) {
    if (point.lat < minLat) minLat = point.lat
    if (point.lat > maxLat) maxLat = point.lat
    if (point.lon < minLon) minLon = point.lon
    if (point.lon > maxLon) maxLon = point.lon
  }
  return BoundingBox(min = LatLon(minLat, minLon), max = LatLon(maxLat, maxLon))
}

/**
 * Étendue en degrés d'une emprise, en deçà de laquelle la cadrer par ses bords n'a plus de sens.
 *
 * 0,0005° valent une cinquantaine de mètres sous nos latitudes : un trajet plus petit que cela est
 * un point, et le cadrer bord à bord enverrait la caméra au zoom maximal sur un mouchoir de poche.
 */
const val POINT_LIKE_SPAN_DEGREES = 0.0005

/** Le centre de l'emprise. */
val BoundingBox.center: LatLon
  get() = LatLon(lat = (min.lat + max.lat) / 2, lon = (min.lon + max.lon) / 2)

/**
 * Vrai si l'emprise est trop petite pour être cadrée par ses bords (SPEC.md § 5.7, règle 9).
 *
 * Un trajet à pied de trente mètres, ou une portion dont le serveur n'a donné qu'un seul point,
 * produit une emprise dégénérée : elle se cadre par son [center] et un zoom fixe.
 */
fun BoundingBox.isPointLike(spanDegrees: Double = POINT_LIKE_SPAN_DEGREES): Boolean =
  max.lat - min.lat < spanDegrees && max.lon - min.lon < spanDegrees
