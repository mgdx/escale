package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.ServerHealth
import io.github.mgdx.escale.data.dto.HealthResponseDto

/**
 * @param fullyStarted faux quand le serveur a répondu 400, ce qui signifie qu'il n'a pas encore
 *   parcouru un cycle complet de tous ses flux. Ce n'est pas une erreur de requête.
 */
internal fun HealthResponseDto.toDomain(fullyStarted: Boolean): ServerHealth =
  ServerHealth(realtime = rt, gbfs = gbfs, fullyStarted = fullyStarted)
