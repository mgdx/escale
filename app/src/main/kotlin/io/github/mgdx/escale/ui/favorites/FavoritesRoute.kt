package io.github.mgdx.escale.ui.favorites

import kotlinx.serialization.Serializable

/**
 * L'écran des favoris et de l'historique (SPEC.md § 5.5), route typée du graphe de navigation
 * (docs/architecture.md § 3, règle 4).
 *
 * **Elle ne porte aucun argument, et c'est délibéré.** Tout ce qu'elle affiche vient des dépôts ;
 * rien n'a à transiter par l'état sauvegardé du système, où un nom de lieu favori — le domicile de
 * l'usager, typiquement — n'a pas sa place (SPEC.md § 11).
 */
@Serializable
data object FavoritesRoute
