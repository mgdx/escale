package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon
import kotlin.math.cos

/**
 * Une distance **de classement**, pas une distance géodésique : le carré de l'écart en degrés,
 * les longitudes ramenées à l'échelle des latitudes par la projection équirectangulaire.
 *
 * Ordonner ou rapprocher des points d'un même quartier ne demande pas de haversine : la projection
 * conserve l'ordre à cette échelle, et le résultat n'est jamais affiché. Le carré évite une racine
 * inutile.
 *
 * Il n'y a **qu'une** formule de distance dans `:core`, et c'est celle-ci : [isWithinMeters] s'en
 * sert aussi, en convertissant son seuil en degrés plutôt qu'en recopiant un second calcul.
 */
internal fun LatLon.rankingDistanceTo(other: LatLon): Double {
  val deltaLat = lat - other.lat
  val deltaLon = (lon - other.lon) * cos(Math.toRadians((lat + other.lat) / 2))
  return deltaLat * deltaLat + deltaLon * deltaLon
}

/**
 * Les deux points sont-ils à moins de [meters] l'un de l'autre ?
 *
 * Le seuil est converti en degrés puis comparé à [rankingDistanceTo], toujours par la même
 * projection équirectangulaire. L'approximation est de l'ordre du pour cent sur quelques centaines
 * de mètres : bien assez pour décider si deux résultats de géocodage désignent le même endroit.
 */
internal fun LatLon.isWithinMeters(other: LatLon, meters: Double): Boolean {
  val degrees = meters / METERS_PER_DEGREE
  return rankingDistanceTo(other) <= degrees * degrees
}

/** Longueur d'un degré de latitude, constante à quelques dixièmes de pour cent près. */
private const val METERS_PER_DEGREE = 111_320.0
