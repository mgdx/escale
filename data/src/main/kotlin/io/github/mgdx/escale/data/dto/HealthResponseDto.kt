package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `GET /api/v1/health`, décalque du schéma `HealthResponse`.
 *
 * Les deux champs sont facultatifs côté API : un serveur sans flux temps réel ni GBFS les omet.
 */
@Serializable
internal data class HealthResponseDto(val rt: Boolean = false, val gbfs: Boolean = false)
