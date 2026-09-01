package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Corps d'erreur de l'API MOTIS, décalque du schéma `Error`.
 *
 * Le champ `error` est un message technique du serveur ; il ne contient pas la requête de
 * l'usager et peut donc être remonté à l'écran (SPEC.md § 8).
 */
@Serializable
internal data class ErrorDto(val error: String? = null)
