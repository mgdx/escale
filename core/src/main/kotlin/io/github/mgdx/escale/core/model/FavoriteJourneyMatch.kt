package io.github.mgdx.escale.core.model

import kotlin.math.abs

/**
 * Reconnaître, parmi les trajets favoris, celui que l'usager est en train de regarder
 * (SPEC.md § 5.5 et § 5.5.1).
 *
 * La surveillance ne se propose que « depuis un trajet favori » : l'écran de détail doit donc
 * savoir si le trajet affiché en est un. La règle est ici, en Kotlin pur, parce qu'elle est
 * calculatoire et qu'une comparaison de lieux faite à la main dans un composable finirait par
 * différer d'un écran à l'autre (docs/architecture.md § 1).
 */
object FavoriteJourneyMatch {

  /**
   * Le favori correspondant au couple [from] / [to] pour l'onglet [category], ou `null`.
   *
   * Un brouillon incomplet ne correspond à rien : c'est le cas au retour après la mort du
   * processus, où la recherche en cours est vide.
   */
  fun find(
    favorites: List<FavoriteJourney>,
    from: Location?,
    to: Location?,
    category: JourneyCategory,
  ): FavoriteJourney? {
    if (from == null || to == null) return null
    return favorites.firstOrNull { it.category == category && same(it.from, from) && same(it.to, to) }
  }

  /**
   * Deux points désignent-ils le même lieu ?
   *
   * **L'identifiant d'arrêt fait foi quand il existe des deux côtés** : c'est lui que la requête
   * `plan` transporte (docs/architecture.md § 11.3), et deux quais d'une même gare ont des
   * coordonnées voisines sans être le même point. Un arrêt et une adresse ne se confondent jamais,
   * même à la même position : l'un a un identifiant, l'autre non.
   *
   * Pour deux adresses, la comparaison porte sur les coordonnées à [TOLERANCE] près — le géocodage
   * ne rend pas toujours la même décimale d'une session à l'autre, et un mètre d'écart ne fait pas
   * deux lieux différents.
   */
  fun same(first: Location, second: Location): Boolean = when {
    first.id != null && second.id != null -> first.id == second.id
    first.id != null || second.id != null -> false
    else -> close(first.coordinates, second.coordinates)
  }

  private fun close(first: LatLon, second: LatLon): Boolean =
    abs(first.lat - second.lat) < TOLERANCE && abs(first.lon - second.lon) < TOLERANCE

  /** Environ un mètre : la cinquième décimale d'un degré. */
  private const val TOLERANCE = 1e-5
}
