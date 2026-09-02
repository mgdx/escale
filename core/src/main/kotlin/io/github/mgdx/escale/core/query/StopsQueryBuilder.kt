package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
import java.math.BigDecimal

/**
 * Les paramètres des deux points d'entrée d'arrêts de la carte (SPEC.md § 5.7 et § 5.4).
 *
 * Comme [PlanQueryBuilder], l'assemblage est en Kotlin pur : `:data` ne fait que poser ces couples
 * sur la requête. Les noms viennent un à un de `docs/motis-openapi.yaml`, opérations `stops` et
 * `stopInfo`.
 */
object StopsQueryBuilder {

  /**
   * `GET /api/v6/map/stops` : les arrêts d'une emprise.
   *
   * - `min` / `max` sont des couples `latitude,longitude`, coin sud-ouest puis coin nord-est ;
   * - `modes` est **restreint au palier de zoom courant** (SPEC.md § 5.7). Un ensemble vide
   *   signifie « tous les modes » côté serveur : le paramètre est alors omis, plutôt qu'envoyé
   *   vide, ce qui n'aurait pas le même sens ;
   * - `grouped=true` laisse le serveur regrouper lui-même les quais d'une même gare, ce qui divise
   *   d'autant le nombre de points à dessiner.
   *
   * Les modes partent en une seule valeur séparée par des virgules, forme vérifiée contre
   * `api.transitous.org` : la même emprise rend onze arrêts sans filtre et cinq avec les cinq modes
   * ferrés lourds.
   */
  fun mapStops(area: BoundingBox, modes: Set<TransitMode>, grouped: Boolean): Map<String, String> {
    val parameters = LinkedHashMap<String, String>()
    parameters[MIN] = point(area.min)
    parameters[MAX] = point(area.max)
    if (grouped) parameters[GROUPED] = "true"
    if (modes.isNotEmpty()) parameters[MODES] = modes.joinToString(",") { it.name }
    return parameters
  }

  /** `GET /api/v6/stop` : les lignes desservant un arrêt, pour l'infobulle de SPEC.md § 5.7. */
  fun stopInfo(stopId: String): Map<String, String> = mapOf(STOP_ID to stopId)

  private fun point(point: LatLon): String = "${number(point.lat)},${number(point.lon)}"

  /** Sans notation scientifique ni séparateur décimal de langue : le serveur n'en veut pas. */
  private fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

  private const val MIN = "min"
  private const val MAX = "max"
  private const val GROUPED = "grouped"
  private const val MODES = "modes"
  private const val STOP_ID = "stopId"
}
