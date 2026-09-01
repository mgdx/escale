package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.ServerCheck
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Serveur courant figé, pour éprouver le dépôt de recherche sans DataStore ni disque. */
internal class FakeServerRepository(baseUrl: String = "https://exemple.org") : ServerRepository {

  private val state = MutableStateFlow(ServerConfig(baseUrl = baseUrl, label = "Essai"))

  override val current: Flow<ServerConfig> = state

  override val knownServers: Flow<List<ServerConfig>> = MutableStateFlow(listOf(state.value))

  override suspend fun save(config: ServerConfig): Outcome<Unit> {
    state.value = config
    return Outcome.Success(Unit)
  }

  override suspend fun forget(baseUrl: String): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun resetToDefault(): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun test(baseUrl: String): Outcome<ServerCheck> = Outcome.Failure(EscaleError.NoNetwork)
}
