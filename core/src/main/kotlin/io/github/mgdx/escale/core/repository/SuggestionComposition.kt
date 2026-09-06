package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind

/**
 * Compose la liste affichée pour qu'**aucun type de lieu n'en masque les autres** (SPEC.md § 5.1).
 *
 * Le serveur rend vingt candidats ([GeocodeRepository.DEFAULT_RESULT_COUNT]) et l'écran n'en montre
 * que dix. Le biais géographique ne suffit pas à les répartir : sur « rue de la paix », la réponse
 * est une file d'arrêts de bus homonymes de communes voisines, et l'adresse cherchée n'apparaît
 * qu'au quatorzième rang — hors de l'écran, donc introuvable.
 *
 * Deux règles, et deux seulement, appliquées **sans jamais rebattre l'ordre du serveur** :
 *
 * 1. au-delà du [MAX_DISTANT_STOPS]ᵉ, les arrêts dont la [Location.description] diffère de celle du
 *    premier arrêt de la liste sont repoussés en fin de liste. La description porte la commune : le
 *    premier arrêt est celui que le serveur juge le plus pertinent, donc celui de la commune visée,
 *    et les suivants qui n'y sont pas sont des homonymes lointains ;
 * 2. la première adresse et le premier lieu gardent une place dans les [limit] retenus dès que le
 *    serveur en a rendu, quitte à ce qu'ils arrivent en bas de la liste.
 *
 * Une liste qui tient déjà dans [limit] est rendue telle quelle : rien n'y est masqué, et la
 * réordonner ne ferait que dégrader le classement du serveur.
 *
 * Fonction pure, et volontairement écrite sur des rangs plutôt que sur des [Location] : deux
 * suggestions distinctes peuvent se comparer égales, et les confondre en perdrait une.
 *
 * @param candidates les suggestions du serveur, dans son ordre, déjà dédoublonnées.
 * @param limit le nombre de lignes affichables.
 */
fun composeSuggestions(
  candidates: List<Location>,
  limit: Int = GeocodeRepository.DISPLAYED_RESULT_COUNT,
): List<Location> {
  if (limit <= 0) return emptyList()
  if (candidates.size <= limit) return candidates
  val ranks = candidates.ranksWithDistantStopsLast()
  val reserved = candidates.reservedRanks().filter { ranks.indexOf(it) >= limit }.take(limit)
  val quota = limit - reserved.size
  val chosen = (ranks.filterNot { it in reserved }.take(quota) + reserved).toSet()
  return ranks.filter { it in chosen }.map(candidates::get)
}

/** Au-delà, les arrêts homonymes d'autres communes prennent toute la place (SPEC.md § 5.1). */
const val MAX_DISTANT_STOPS = 4

/**
 * Les rangs des candidats, ceux des arrêts lointains excédentaires renvoyés en fin de liste.
 *
 * L'ordre relatif ne change nulle part ailleurs : le serveur classe par pertinence, et cette
 * composition ne fait que déplacer ce qui déborde.
 */
private fun List<Location>.ranksWithDistantStopsLast(): List<Int> {
  val reference = firstOrNull { it.kind == PlaceKind.STOP }?.description
  val kept = ArrayList<Int>(size)
  val demoted = ArrayList<Int>()
  var distantStops = 0
  forEachIndexed { rank, location ->
    val distant = location.kind == PlaceKind.STOP && location.description != reference
    if (distant) distantStops++
    if (distant && distantStops > MAX_DISTANT_STOPS) demoted += rank else kept += rank
  }
  return kept + demoted
}

/** Les rangs de la première adresse et du premier lieu, ceux à qui une place est garantie. */
private fun List<Location>.reservedRanks(): List<Int> = listOfNotNull(
  indexOfFirst { it.kind == PlaceKind.ADDRESS }.takeIf { it >= 0 },
  indexOfFirst { it.kind == PlaceKind.PLACE }.takeIf { it >= 0 },
)
