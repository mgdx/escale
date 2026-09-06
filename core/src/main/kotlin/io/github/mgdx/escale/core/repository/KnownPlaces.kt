package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import java.text.Normalizer

/**
 * Les lieux que l'application connaît déjà et qui correspondent à la saisie (SPEC.md § 5.1).
 *
 * Ils s'affichent **dès le premier caractère et sans aucune requête** : attendre trois caractères
 * et un aller-retour réseau pour retrouver sa gare habituelle, alors qu'elle est enregistrée sur
 * l'appareil, n'a pas de sens.
 *
 * L'ordre est celui de la spec : les lieux enregistrés d'abord — domicile, travail, lieux favoris,
 * dans l'ordre où l'appelant les donne —, puis les extrémités des dernières recherches, de la plus
 * récente à la plus ancienne. Les deux extrémités de chaque recherche entrent dans le vivier : on
 * repart aussi souvent d'où l'on est allé.
 *
 * Le filtre est insensible à la casse **et aux accents** : « gare de l'est » doit trouver « Gare de
 * l'Est », et « asnieres » doit trouver « Asnières ». Il porte sur le nom et sur le complément de
 * localisation, comme le dit la spec.
 *
 * La liste est enfin dédoublonnée par [withoutDuplicates] : le domicile est aussi le départ des
 * dix dernières recherches, et il ne doit apparaître qu'une fois.
 *
 * Fonction pure : aucune saisie n'en sort, rien n'y est journalisé (SPEC.md § 11).
 *
 * @param query la saisie en cours ; vide ou blanche, rien n'est proposé.
 * @param saved les lieux enregistrés, dans l'ordre où ils doivent apparaître.
 * @param recent l'historique des recherches. **Vide quand la bascule de SPEC.md § 5.5 est
 *   désactivée** : le dépôt ne conserve alors rien, et il n'y a donc rien de plus à vérifier ici.
 * @param limit le nombre de lignes que le bloc peut occuper.
 */
fun matchingKnownPlaces(
  query: String,
  saved: List<Location>,
  recent: List<SearchHistoryEntry>,
  limit: Int = MAX_KNOWN_PLACES,
): List<Location> {
  val needle = query.foldedForSearch()
  if (needle.isEmpty() || limit <= 0) return emptyList()
  val endpoints = recent.sortedByDescending { it.searchedAt }.flatMap { listOf(it.from, it.to) }
  return (saved + endpoints).filter { it.matches(needle) }.withoutDuplicates().take(limit)
}

/**
 * Les suggestions du serveur, moins celles que le bloc « déjà utilisés » montre déjà
 * (SPEC.md § 5.1).
 *
 * C'est exactement la règle de [withoutDuplicates], appliquée entre deux listes plutôt qu'au sein
 * d'une seule : un arrêt enregistré et le même arrêt rendu par le géocodage sont la même ligne, et
 * l'afficher deux fois coûte une place à un autre résultat.
 */
fun List<Location>.withoutKnownPlaces(known: List<Location>): List<Location> {
  if (known.isEmpty() || isEmpty()) return this
  // Un bloc lui-même dédoublonné est ce qui rend le décompte exact : les `known.size` premières
  // entrées du résultat sont alors le bloc, et rien d'autre.
  val block = known.withoutDuplicates()
  return (block + this).withoutDuplicates().drop(block.size)
}

/** Au-delà, le bloc repousse les résultats du serveur hors de l'écran (SPEC.md § 5.1). */
const val MAX_KNOWN_PLACES = 5

private fun Location.matches(needle: String): Boolean =
  name.foldedForSearch().contains(needle) || description?.foldedForSearch()?.contains(needle) == true

/**
 * Le texte ramené à sa forme comparable : sans accent, en minuscules, sans espaces de bordure.
 *
 * La décomposition canonique sépare la lettre de son accent, et [COMBINING_MARKS] retire le second.
 * `lowercase()` sans argument est indépendant de la langue de l'appareil : une locale turque ne doit
 * pas changer ce que trouve une recherche.
 */
private fun String.foldedForSearch(): String =
  Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

/** Les accents, une fois détachés de leur lettre par la décomposition canonique. */
private val COMBINING_MARKS = Regex("\\p{Mn}+")
