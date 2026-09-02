package io.github.mgdx.escale.ui.detail

import kotlinx.serialization.Serializable

/**
 * L'écran de détail d'un trajet (SPEC.md § 5.3), route typée du graphe de navigation
 * (docs/architecture.md § 3, règle 4).
 *
 * **La route ne porte aucun argument, et c'est délibéré.** Un identifiant d'itinéraire MOTIS
 * encode la requête qui l'a produit — donc l'origine et la destination de l'usager — et les
 * arguments de route finissent dans l'état sauvegardé du système, sur le disque. SPEC.md § 11
 * interdit d'y écrire une donnée de localisation, et `ResultsViewModel` a tranché de la même façon
 * pour la liste de résultats.
 *
 * Le trajet à afficher est donc lu en mémoire, dans `SelectedJourneyStore`. Après la mort du
 * processus ce magasin est vide, comme l'est la recherche en cours : l'écran se referme alors de
 * lui-même et l'usager retrouve la carte, exactement comme la feuille de résultats.
 */
@Serializable
data object DetailRoute
