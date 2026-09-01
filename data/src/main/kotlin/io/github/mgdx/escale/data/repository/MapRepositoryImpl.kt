package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.MapApi
import kotlinx.coroutines.flow.first

/**
 * Le cadrage initial du serveur courant.
 *
 * Le serveur n'est pas retenu à la construction mais relu à chaque appel : changer de serveur doit
 * recadrer la carte sur la zone du nouveau, pas garder celle de l'ancien (SPEC.md § 5.6.1).
 */
class MapRepositoryImpl(private val api: MapApi, private val serverRepository: ServerRepository) : MapRepository {

  override suspend fun initialCamera(): Outcome<MapCamera> = api.initialCamera(currentBaseUrl())

  private suspend fun currentBaseUrl(): String = serverRepository.current.first().baseUrl
}
