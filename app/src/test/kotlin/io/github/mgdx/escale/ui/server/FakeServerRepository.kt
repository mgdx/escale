package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.model.ServerCheck
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dépôt de serveurs en mémoire.
 *
 * Aucun test ne touche au réseau : le test de connexion rend le résultat que le cas d'essai a
 * programmé, et l'on vérifie qu'il n'est même pas appelé quand le consentement manque.
 */
class FakeServerRepository(
  initial: ServerConfig = ServerConfig(baseUrl = ServerUrl.DEFAULT_BASE_URL, label = "api.transitous.org"),
) : ServerRepository {

  private val currentServer = MutableStateFlow(initial)
  private val servers = MutableStateFlow(listOf(initial))

  /** Le résultat que rendra le prochain appel à [test]. */
  var testOutcome: Outcome<ServerCheck> = Outcome.Failure(EscaleError.ServerUnreachable(statusCode = null))

  /** Les URL effectivement testées, dans l'ordre. */
  val testedUrls: MutableList<String> = mutableListOf()

  /** Les serveurs effectivement enregistrés, dans l'ordre. */
  val savedConfigs: MutableList<ServerConfig> = mutableListOf()

  val forgottenUrls: MutableList<String> = mutableListOf()

  override val current: Flow<ServerConfig> = currentServer

  override val knownServers: Flow<List<ServerConfig>> = servers

  override suspend fun save(config: ServerConfig): Outcome<Unit> {
    savedConfigs += config
    servers.value = listOf(config) + servers.value.filterNot { it.baseUrl == config.baseUrl }
    currentServer.value = config
    return Outcome.Success(Unit)
  }

  override suspend fun forget(baseUrl: String): Outcome<Unit> {
    forgottenUrls += baseUrl
    servers.value = servers.value.filterNot { it.baseUrl == baseUrl }
    return Outcome.Success(Unit)
  }

  override suspend fun resetToDefault(): Outcome<Unit> =
    save(ServerConfig(baseUrl = ServerUrl.DEFAULT_BASE_URL, label = "api.transitous.org"))

  override suspend fun test(baseUrl: String): Outcome<ServerCheck> {
    testedUrls += baseUrl
    return testOutcome
  }

  /** Ajoute un serveur à la liste des serveurs connus sans en faire le serveur en service. */
  fun addKnown(config: ServerConfig) {
    servers.value = servers.value + config
  }
}

/** Consentement au trafic en clair en mémoire (docs/architecture.md § 11.1). */
class FakeCleartextConsentStore(initial: Set<String> = emptySet()) : CleartextConsentStore {

  private val hosts = MutableStateFlow(initial)

  val acceptedHosts: MutableList<String> = mutableListOf()

  override val consentedHosts: Flow<Set<String>> = hosts

  override suspend fun accept(host: String) {
    acceptedHosts += host
    hosts.value = hosts.value + host.lowercase()
  }
}
