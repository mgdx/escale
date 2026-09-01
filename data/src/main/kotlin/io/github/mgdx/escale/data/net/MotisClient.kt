package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.ServerHealth
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.ErrorDto
import io.github.mgdx.escale.data.dto.HealthResponseDto
import io.github.mgdx.escale.data.mapper.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.IOException

/**
 * Client HTTP du serveur MOTIS (docs/architecture.md § 7).
 *
 * Points non négociables mis en œuvre ici :
 * - `ignoreUnknownKeys = true` : l'API MOTIS ajoute des champs sans préavis ;
 * - en-tête `User-Agent` sur **chaque** requête, version lue depuis `BuildConfig` et transmise par
 *   :app au constructeur, jamais codée en dur (SPEC.md § 4.2) ;
 * - délai d'expiration de 30 s, **une seule** reprise, aucune reprise sur 4xx (SPEC.md § 7.8) ;
 * - **aucune journalisation** de corps de requête ni de réponse, même en débogage : le greffon
 *   `Logging` de Ktor n'est délibérément pas installé (SPEC.md § 8 et § 11).
 *
 * L'URL racine est passée à chaque appel plutôt que retenue par le client : l'écran « Serveur
 * MOTIS » doit pouvoir tester un serveur candidat sans changer celui qui est en service.
 */
class MotisClient(versionName: String, engine: HttpClientEngine = OkHttp.create()) : Closeable {

  private val json = Json {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis.
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
  }

  private val client = HttpClient(engine) {
    // Les statuts d'échec sont traduits en EscaleError, pas levés en exception.
    expectSuccess = false

    install(ContentNegotiation) {
      json(json)
    }

    install(HttpTimeout) {
      requestTimeoutMillis = TIMEOUT_MILLIS
      connectTimeoutMillis = TIMEOUT_MILLIS
      socketTimeoutMillis = TIMEOUT_MILLIS
    }

    install(HttpRequestRetry) {
      // Une seule reprise, et jamais sur une erreur 4xx : la requête ne deviendra pas valide.
      maxRetries = MAX_RETRIES
      retryIf { _, response -> response.status.value >= SERVER_ERROR_START }
      retryOnExceptionIf(maxRetries = MAX_RETRIES) { _, cause -> isRetryable(cause) }
      constantDelay(millis = RETRY_DELAY_MILLIS)
    }

    // Obligation de la politique d'usage de Transitous (SPEC.md § 4.2).
    install(UserAgent) {
      agent = userAgent(versionName)
    }
  }

  /**
   * `GET /api/v1/health` : le serveur répond-il, et avec quels flux ?
   *
   * Le point d'entrée répond **400 avec le même corps** tant qu'il n'a pas parcouru un cycle
   * complet de ses flux. Ce cas est donc un succès, avec `fullyStarted = false`.
   */
  suspend fun health(baseUrl: String): Outcome<ServerHealth> = runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.HEALTH))
    when {
      response.status.isSuccess() -> Outcome.Success(decodeHealth(response, fullyStarted = true))
      response.status.value == BAD_REQUEST -> Outcome.Success(decodeHealth(response, fullyStarted = false))
      else -> failure(response, MotisEndpoints.HEALTH)
    }
  }

  /**
   * Étape 2 du test de connexion (SPEC.md § 5.6.1) : un point d'entrée `v6` répond-il ?
   *
   * Interroge `map/stops` sur une emprise minuscule, la requête la moins coûteuse qui exerce `v6`.
   * Un 404 devient [EscaleError.ApiVersionTooOld].
   */
  suspend fun probeApiVersion(baseUrl: String): Outcome<Unit> = runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.MAP_STOPS)) {
      parameter("min", PROBE_BOX_MIN)
      parameter("max", PROBE_BOX_MAX)
    }
    if (response.status.isSuccess()) {
      Outcome.Success(Unit)
    } else {
      failure(response, MotisEndpoints.MAP_STOPS)
    }
  }

  /**
   * Étape 3 du test de connexion : le serveur sert-il un fond de carte ?
   *
   * Un serveur sans tuiles reste parfaitement utilisable (SPEC.md § 5.7) : l'absence de tuile n'est
   * donc pas une erreur, mais un `false`.
   */
  suspend fun probeTiles(baseUrl: String): Outcome<Boolean> = runCatchingHttp {
    val response = client.get(MotisEndpoints.tileUrl(baseUrl, PROBE_TILE_ZOOM, 0, 0))
    Outcome.Success(response.status.isSuccess())
  }

  override fun close() {
    client.close()
  }

  private suspend fun decodeHealth(response: HttpResponse, fullyStarted: Boolean): ServerHealth =
    json.decodeFromString(HealthResponseDto.serializer(), response.bodyAsText())
      .toDomain(fullyStarted)

  /** Lit le champ `error` du corps quand il s'y trouve, sans jamais rien journaliser. */
  private suspend fun <T> failure(response: HttpResponse, endpoint: String): Outcome<T> {
    val message = runCatching {
      json.decodeFromString(ErrorDto.serializer(), response.bodyAsText()).error
    }.getOrNull()
    return Outcome.Failure(HttpFailures.fromStatus(response.status.value, endpoint, message))
  }

  /**
   * Rattrape toute défaillance du transport pour la traduire en [EscaleError] : SPEC.md § 8 impose
   * que l'application explique l'échec plutôt que de planter. L'annulation de coroutine, elle, doit
   * continuer de se propager, sans quoi les annulations de requêtes de SPEC.md § 7 ne marcheraient
   * plus.
   */
  private inline fun <T> runCatchingHttp(block: () -> Outcome<T>): Outcome<T> = try {
    block()
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (failure: Exception) {
    Outcome.Failure(HttpFailures.fromThrowable(failure))
  }

  private companion object {
    const val TIMEOUT_MILLIS = 30_000L
    const val MAX_RETRIES = 1
    const val RETRY_DELAY_MILLIS = 500L
    const val SERVER_ERROR_START = 500
    const val BAD_REQUEST = 400
    const val PROBE_TILE_ZOOM = 0

    // Emprise volontairement minuscule : on teste la version de l'API, pas les données.
    const val PROBE_BOX_MIN = "0.0,0.0"
    const val PROBE_BOX_MAX = "0.001,0.001"

    fun userAgent(versionName: String): String = "Escale/$versionName (+https://github.com/mgdx/escale)"

    /**
     * Une panne de transport mérite une seconde tentative, pas une expiration : reprendre après
     * un délai de 30 s ferait attendre l'usager une minute pour rien.
     */
    fun isRetryable(cause: Throwable): Boolean = when (cause) {
      is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> false
      is IOException -> true
      else -> false
    }
  }
}
