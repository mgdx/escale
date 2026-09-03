package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerTestProgress
import io.github.mgdx.escale.core.model.ServerTestStep
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

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

  /**
   * L'avancement que rendra le prochain appel à [test], émission par émission.
   *
   * Programmé par [programTest] ou [programUnreachable] plutôt qu'écrit à la main : ce qui compte
   * dans un cas d'essai, ce sont les trois verdicts, pas la mécanique du flux.
   */
  var testProgress: List<ServerTestProgress> = unreachable(EscaleError.ServerUnreachable(statusCode = null))

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

  /**
   * Nombre d'émissions après lequel le flux se bloque, jusqu'à [resumeTest].
   *
   * C'est ce qui permet d'observer un test **en cours** : sans point d'arrêt, les six émissions se
   * suivent sans reprendre la main et l'écran ne montre que l'état final, ce qui est précisément le
   * défaut que l'on corrige.
   */
  var pauseAfter: Int? = null

  private val gate = CompletableDeferred<Unit>()

  /** Laisse repartir un flux arrêté par [pauseAfter]. */
  fun resumeTest() {
    gate.complete(Unit)
  }

  override fun test(baseUrl: String): Flow<ServerTestProgress> = flow {
    testedUrls += baseUrl
    testProgress.forEachIndexed { index, progress ->
      if (index == pauseAfter) gate.await()
      emit(progress)
    }
  }

  /** Un serveur qui répond, avec les trois verdicts voulus. */
  fun programTest(reachable: Boolean = true, apiCompatible: Boolean = true, tilesAvailable: Boolean = true) {
    testProgress = buildList {
      add(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      add(ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = reachable))
      add(ServerTestProgress.Started(ServerTestStep.API_VERSION))
      add(ServerTestProgress.Finished(ServerTestStep.API_VERSION, passed = apiCompatible))
      add(ServerTestProgress.Started(ServerTestStep.TILES))
      add(ServerTestProgress.Finished(ServerTestStep.TILES, passed = tilesAvailable))
    }
  }

  /** Un serveur injoignable : les deux étapes suivantes sont sautées, comme le fait le vrai dépôt. */
  fun programUnreachable(error: EscaleError) {
    testProgress = unreachable(error)
  }

  /** Ajoute un serveur à la liste des serveurs connus sans en faire le serveur en service. */
  fun addKnown(config: ServerConfig) {
    servers.value = servers.value + config
  }

  private companion object {
    fun unreachable(error: EscaleError) = listOf(
      ServerTestProgress.Started(ServerTestStep.REACHABLE),
      ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = false, error = error),
      ServerTestProgress.Skipped(ServerTestStep.API_VERSION),
      ServerTestProgress.Skipped(ServerTestStep.TILES),
    )
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

/**
 * Les trois purges de SPEC.md § 5.6.1, comptées plutôt qu'exécutées.
 *
 * Chacune rend ce que le cas d'essai a programmé : c'est ce qui permet de vérifier qu'un échec de
 * purge ne bloque ni les deux autres, ni le changement de serveur.
 */
class RecordingCacheReset(
  resultsOutcome: Outcome<Unit> = Outcome.Success(Unit),
  geocodeOutcome: Outcome<Unit> = Outcome.Success(Unit),
  tilesPurged: Boolean = true,
) {

  var resultsCacheCleared: Int = 0
    private set

  var geocodeCacheCleared: Int = 0
    private set

  var tileCacheCleared: Int = 0
    private set

  /** Le vrai objet, monté sur les compteurs ci-dessus. */
  val reset: ServerCacheReset = ServerCacheReset(
    resultsCache = {
      resultsCacheCleared++
      resultsOutcome
    },
    geocodeCache = {
      geocodeCacheCleared++
      geocodeOutcome
    },
    tileCache = {
      tileCacheCleared++
      tilesPurged
    },
  )

  /** Le triplet des compteurs, pour lire une assertion d'un coup d'œil. */
  fun counts(): Triple<Int, Int, Int> = Triple(resultsCacheCleared, geocodeCacheCleared, tileCacheCleared)
}
