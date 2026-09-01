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
