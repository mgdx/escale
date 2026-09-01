package io.github.mgdx.escale.core.model

/**
 * Emprise rectangulaire en degrés décimaux, décrite par son coin sud-ouest et son coin nord-est.
 *
 * L'API MOTIS attend ce couple sous les paramètres `min` / `max` de `/api/v6/map/stops`.
 */
data class BoundingBox(val min: LatLon, val max: LatLon) {

  /** Vrai si le point est à l'intérieur de l'emprise, bords compris. */
  fun contains(point: LatLon): Boolean = point.lat in min.lat..max.lat && point.lon in min.lon..max.lon

  /**
   * Élargit l'emprise de [ratio] de sa taille, de part et d'autre de chaque axe.
   *
   * `expandBy(0.3)` produit l'emprise « écran élargie de 30 % » exigée par SPEC.md § 5.7 : les
   * petits déplacements de caméra retombent alors dans une zone déjà chargée et n'émettent aucune
   * requête. Les latitudes sont bornées aux pôles et les longitudes au méridien de changement de
   * date : le serveur refuse une emprise hors de ces bornes.
   */
  fun expandBy(ratio: Double): BoundingBox {
    val latMargin = (max.lat - min.lat) * ratio
    val lonMargin = (max.lon - min.lon) * ratio
    return BoundingBox(
      min = LatLon(
        lat = (min.lat - latMargin).coerceAtLeast(MIN_LATITUDE),
        lon = (min.lon - lonMargin).coerceAtLeast(MIN_LONGITUDE),
      ),
      max = LatLon(
        lat = (max.lat + latMargin).coerceAtMost(MAX_LATITUDE),
        lon = (max.lon + lonMargin).coerceAtMost(MAX_LONGITUDE),
      ),
    )
  }

  private companion object {
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0
  }
}
