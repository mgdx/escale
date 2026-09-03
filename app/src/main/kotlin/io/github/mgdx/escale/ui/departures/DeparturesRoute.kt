package io.github.mgdx.escale.ui.departures

import kotlinx.serialization.Serializable

/**
 * L'écran des prochains départs à un arrêt (SPEC.md § 5.4), route typée du graphe de navigation
 * (docs/architecture.md § 3, règle 4).
 *
 * **Elle porte ses arguments, contrairement à `DetailRoute`, et la différence est délibérée.** Un
 * identifiant d'itinéraire encode la requête qui l'a produit — donc l'origine et la destination de
 * l'usager —, ce qui interdit de l'écrire dans l'état sauvegardé du système. Un identifiant
 * d'arrêt, lui, désigne un point public du réseau : SPEC.md § 5.5 prévoit explicitement de mettre
 * un arrêt en favori, c'est-à-dire de le ranger dans une base locale. Le passer par la navigation
 * ne dit donc rien de plus que ce que l'application est déjà autorisée à conserver.
 *
 * [stopName] évite un écran sans titre le temps de la première réponse : le nom est déjà connu de
 * l'infobulle ou de l'autocomplétion qui a mené ici, et le redemander au serveur pour l'afficher
 * serait une requête pour rien (SPEC.md § 7).
 */
@Serializable
data class DeparturesRoute(val stopId: String, val stopName: String)
