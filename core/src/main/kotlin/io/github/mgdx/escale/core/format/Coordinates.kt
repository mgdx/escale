package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.LatLon
import java.util.Locale

/** Cinq décimales, soit environ un mètre : au-delà, le chiffre n'apporte plus rien à l'usager. */
private const val COORDINATE_DECIMALS = 5

/**
 * Un point écrit en clair, pour nommer un lieu que le géocodage inverse n'a pas su nommer
 * (SPEC.md § 5.1, appui long sur la carte).
 *
 * Le séparateur décimal est celui de [Locale.ROOT], jamais celui de la langue de l'interface : une
 * latitude s'écrit « 48.85661 » dans toutes les langues, et « 48,85661 ; 2,35222 » serait illisible.
 *
 * Cette chaîne s'affiche, elle ne se journalise **jamais** (SPEC.md § 8 et § 11).
 */
fun formatCoordinates(point: LatLon): String {
  val pattern = "%.${COORDINATE_DECIMALS}f"
  return String.format(Locale.ROOT, "$pattern, $pattern", point.lat, point.lon)
}
