package io.github.mgdx.escale.ui.detail

import kotlinx.serialization.Serializable

/**
 * L'écran de détail d'un trajet (SPEC.md § 5.3), route typée du graphe de navigation
 * (docs/architecture.md § 3, règle 4).
 *
 * **La route ne porte aucun argument, et c'est délibéré.** Un identifiant d'itinéraire MOTIS
 * encode la requête qui l'a produit — donc l'origine et la destination de l'usager — et un
 * argument de route se lit dans l'adresse de la destination, journalisée par l'outillage de
 * navigation. Il n'a rien à y faire (SPEC.md § 11).
 *
 * Le trajet à afficher est donc lu en mémoire, dans `SelectedJourneyStore`. Après la mort du
 * processus ce magasin est vide : `DetailViewModel` redemande alors l'itinéraire à partir de
 * l'identifiant qu'il a rangé dans l'état sauvegardé de cette entrée de navigation — en mémoire du
 * système, jamais sur le disque, et effacé avec la tâche. L'usager qui revient retrouve la fiche
 * qu'il lisait, et non la liste de résultats.
 */
@Serializable
data object DetailRoute
