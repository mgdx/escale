package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import java.math.BigDecimal

/**
 * Les paramètres de `GET /api/v1/rentals` (SPEC.md § 5.3 et § 5.7).
 *
 * Comme [StopsQueryBuilder], l'assemblage est en Kotlin pur : `:data` ne fait que poser ces couples
 * sur la requête. Les noms viennent un à un de `docs/motis-openapi.yaml`, opération `rentals`.
 *
 * L'API refuse de rendre la moindre station tant qu'aucun filtre n'est posé : sans `point`+`radius`
 * ni `min`+`max`, elle ne renvoie que la liste des exploitants. Les deux fonctions ci-dessous sont
 * donc les deux seules manières d'interroger utilement ce point d'entrée.
 */
object RentalsQueryBuilder {

  /**
   * Autour d'un point, pour la portion en libre-service d'un trajet.
   *
   * Le rayon est **volontairement faible** (SPEC.md § 5.3) : on cherche la station que le trajet
   * désigne, pas l'inventaire du quartier. Cinquante mètres autour du parvis de la gare de Berlin
   * rendent déjà trois exploitants ; deux cents en rendraient trente, pour une seule qui compte.
   *
   * `withVehicles=false` : un véhicule en free-floating n'a ni disponibilité ni place libre à
   * annoncer, et il représente l'essentiel du poids de la réponse — trente-trois véhicules contre
   * trois stations sur cette même place. Ne pas les demander divise la réponse par quatre (§ 7).
   */
  fun around(point: LatLon, radiusMeters: Int): Map<String, String> = buildMap {
    put(POINT, point(point))
    put(RADIUS, radiusMeters.toString())
    put(WITH_VEHICLES, "false")
    put(WITH_ZONES, "false")
  }

  /**
   * Dans une emprise, pour les marqueurs de la carte.
   *
   * `min` est le coin sud-ouest et `max` le coin nord-est, comme pour `/api/v6/map/stops` : la
   * description de l'OpenAPI (« lower right », « upper left ») est la même sur les deux points
   * d'entrée et ne correspond pas au comportement observé sur `api.transitous.org`, où c'est bien
   * le couple sud-ouest / nord-est qui rend l'emprise attendue.
   *
   * Les véhicules isolés sont demandés ici, eux : ils sont le contenu du palier de zoom ≥ 15.
   */
  fun within(area: BoundingBox): Map<String, String> = buildMap {
    put(MIN, point(area.min))
    put(MAX, point(area.max))
    put(WITH_ZONES, "false")
  }

  private fun point(point: LatLon): String = "${number(point.lat)},${number(point.lon)}"

  /** Sans notation scientifique ni séparateur décimal de langue : le serveur n'en veut pas. */
  private fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

  private const val POINT = "point"
  private const val RADIUS = "radius"
  private const val MIN = "min"
  private const val MAX = "max"
  private const val WITH_VEHICLES = "withVehicles"

  /**
   * Les zones de géorepérage sont des multipolygones encodés, et Escale n'en dessine aucune en v1.
   * Les demander alourdirait chaque réponse sans rien afficher de plus (SPEC.md § 7).
   */
  private const val WITH_ZONES = "withZones"
}
