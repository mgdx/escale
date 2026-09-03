package io.github.mgdx.escale.ui.trip

import kotlinx.serialization.Serializable

/**
 * L'écran « détail de la course » (SPEC.md § 5.3), route typée du graphe de navigation
 * (docs/architecture.md § 3, règle 4).
 *
 * **Elle porte son identifiant, contrairement à `DetailRoute`, et la différence est délibérée.** Un
 * identifiant d'itinéraire encode la requête qui l'a produit — donc l'origine et la destination de
 * l'usager —, ce qui interdit de l'écrire dans l'état sauvegardé du système. Un `tripId` désigne
 * une course du réseau, la même pour tout le monde : il ne dit rien de qui la consulte, et c'est
 * le seul moyen de rouvrir l'écran après la mort du processus plutôt que de le refermer.
 *
 * [lineName] évite un écran sans titre le temps de la première réponse, quand l'appelant le connaît
 * déjà — c'est le cas depuis un départ. Il est vide quand il vient de l'écran de détail d'un
 * trajet, où seul le `tripId` est disponible : le titre arrive alors avec la réponse.
 */
@Serializable
data class TripRoute(val tripId: String, val lineName: String = "", val headsign: String = "")
