package io.github.mgdx.escale.ui.about

import kotlinx.serialization.Serializable

/**
 * Route du texte intégral de la licence, atteinte depuis « À propos » (SPEC.md § 5.6).
 *
 * **Une destination, et non un dialogue plein écran.** Un `Dialog` occupant tout l'écran ne
 * consomme pas les encarts système : les barres de statut et de navigation restaient grises au
 * lieu de suivre le fond, alors que tout le reste de l'application est bord à bord. Il ne se
 * comporte pas non plus comme un écran vis-à-vis du retour arrière matériel.
 */
@Serializable
data object LicenseRoute
