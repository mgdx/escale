package io.github.mgdx.escale.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerTestProgress
import io.github.mgdx.escale.core.model.ServerTestStep
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.MotisClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

/**
 * Serveur courant et serveurs déjà utilisés, persistés en DataStore Preferences.
 *
 * Le serveur par défaut n'est pas écrit au premier lancement : il est simplement rendu quand rien
 * n'est enregistré. L'application démarre donc sans écriture disque.
 */
class ServerRepositoryImpl(private val dataStore: DataStore<Preferences>, private val client: MotisClient) :
  ServerRepository {

  private val json = Json { ignoreUnknownKeys = true }

  override val current: Flow<ServerConfig> = readable().map { preferences ->
    val baseUrl = preferences[KEY_CURRENT]
    storedServers(preferences).firstOrNull { it.baseUrl == baseUrl } ?: DEFAULT_SERVER
  }

  /** Tant que rien n'a été enregistré, le seul serveur « connu » est l'instance publique. */
  override val knownServers: Flow<List<ServerConfig>> = readable().map { preferences ->
    storedServers(preferences).ifEmpty { listOf(DEFAULT_SERVER) }
  }

  override suspend fun save(config: ServerConfig): Outcome<Unit> = write { preferences ->
    val others = storedServers(preferences).filterNot { it.baseUrl == config.baseUrl }
    preferences[KEY_SERVERS] = encode(listOf(config) + others)
    preferences[KEY_CURRENT] = config.baseUrl
  }

  override suspend fun forget(baseUrl: String): Outcome<Unit> = write { preferences ->
    preferences[KEY_SERVERS] = encode(storedServers(preferences).filterNot { it.baseUrl == baseUrl })
  }

  override suspend fun resetToDefault(): Outcome<Unit> = save(DEFAULT_SERVER)

  /**
   * Les trois étapes, menées l'une après l'autre, chacune annoncée puis tranchée avant que la
   * suivante ne parte (SPEC.md § 5.6.1).
   *
   * Elles restent séquentielles et ne sont pas lancées en parallèle : la première conditionne les
   * deux autres, et trois requêtes simultanées vers un serveur qu'on est en train d'éprouver
   * seraient un mauvais service à lui rendre comme à l'usager.
   */
  override fun test(baseUrl: String): Flow<ServerTestProgress> = flow {
    val normalized = ServerUrl.normalize(baseUrl)
    if (normalized == null) {
      emit(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      emit(
        ServerTestProgress.Finished(
          step = ServerTestStep.REACHABLE,
          passed = false,
          error = EscaleError.BadRequest(serverMessage = null),
        ),
      )
      emitSkipped()
      return@flow
    }
    // Étape 1 : le serveur répond-il ? C'est la seule étape dont l'échec arrête le test.
    emit(ServerTestProgress.Started(ServerTestStep.REACHABLE))
    val health = when (val outcome = client.health(normalized)) {
      is Outcome.Success -> outcome.value

      is Outcome.Failure -> {
        emit(
          ServerTestProgress.Finished(
            step = ServerTestStep.REACHABLE,
            passed = false,
            error = outcome.error,
          ),
        )
        emitSkipped()
        return@flow
      }
    }
    emit(ServerTestProgress.Finished(step = ServerTestStep.REACHABLE, passed = true, health = health))

    // Étapes 2 et 3 : une API trop ancienne ou un serveur sans tuiles se signalent par un verdict
    // négatif, jamais par une erreur (SPEC.md § 5.6.1).
    emit(ServerTestProgress.Started(ServerTestStep.API_VERSION))
    val apiCompatible = client.probeApiVersion(normalized) is Outcome.Success
    emit(ServerTestProgress.Finished(step = ServerTestStep.API_VERSION, passed = apiCompatible))

    emit(ServerTestProgress.Started(ServerTestStep.TILES))
    val tilesAvailable = when (val outcome = client.probeTiles(normalized)) {
      is Outcome.Success -> outcome.value
      is Outcome.Failure -> false
    }
    emit(ServerTestProgress.Finished(step = ServerTestStep.TILES, passed = tilesAvailable))
  }

  /**
   * Les deux étapes que l'échec de la première rend sans objet.
   *
   * Elles ne sont pas menées : les lancer vers un serveur qui n'a pas répondu ferait attendre
   * l'usager deux expirations de plus pour ne rien lui apprendre. Elles ne sont pas non plus
   * marquées en échec, ce qui affirmerait sur ce serveur quelque chose qui n'a pas été mesuré.
   */
  private suspend fun FlowCollector<ServerTestProgress>.emitSkipped() {
    emit(ServerTestProgress.Skipped(ServerTestStep.API_VERSION))
    emit(ServerTestProgress.Skipped(ServerTestStep.TILES))
  }

  /**
   * DataStore signale une lecture impossible par une [IOException] : un fichier illisible ne doit
   * pas priver l'application de son serveur par défaut.
   */
  private fun readable(): Flow<Preferences> = dataStore.data.catch { cause ->
    if (cause is IOException) emit(emptyPreferences()) else throw cause
  }

  private suspend fun write(block: (MutablePreferences) -> Unit): Outcome<Unit> = try {
    dataStore.edit(block)
    Outcome.Success(Unit)
  } catch (failure: IOException) {
    Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
  }

  /**
   * Serveurs effectivement enregistrés, sans y ajouter l'instance par défaut : une préférence
   * illisible se traduit par une liste vide, jamais par un plantage.
   */
  private fun storedServers(preferences: Preferences): List<ServerConfig> {
    val raw = preferences[KEY_SERVERS] ?: return emptyList()
    return runCatching {
      json.decodeFromString(SERVERS_SERIALIZER, raw).map(::toDomain)
    }.getOrDefault(emptyList())
  }

  private fun encode(servers: List<ServerConfig>): String =
    json.encodeToString(SERVERS_SERIALIZER, servers.map(::toDto))

  private fun toDomain(dto: ServerPrefsDto) = ServerConfig(
    baseUrl = dto.baseUrl,
    label = dto.label,
    hasTiles = dto.hasTiles,
    lastCheckedAt = dto.lastCheckedAtMillis?.let(Instant::ofEpochMilli),
  )

  private fun toDto(config: ServerConfig) = ServerPrefsDto(
    baseUrl = config.baseUrl,
    label = config.label,
    hasTiles = config.hasTiles,
    lastCheckedAtMillis = config.lastCheckedAt?.toEpochMilli(),
  )

  companion object {
    /** Instance publique communautaire (SPEC.md § 4.1). */
    val DEFAULT_SERVER = ServerConfig(
      baseUrl = ServerUrl.DEFAULT_BASE_URL,
      label = "api.transitous.org",
    )

    private val KEY_CURRENT = stringPreferencesKey("server_current_base_url")
    private val KEY_SERVERS = stringPreferencesKey("server_known")
    private val SERVERS_SERIALIZER =
      kotlinx.serialization.builtins.ListSerializer(ServerPrefsDto.serializer())
  }
}
