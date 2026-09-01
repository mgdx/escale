package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.result.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.IOException

/**
 * `GET /api/v1/map/initial` : le cadrage que le serveur propose sur sa propre zone de données.
 *
 * Dernier recours du cadrage initial de SPEC.md § 5.1, appelé une fois au démarrage et seulement
 * si ni la caméra mémorisée ni la position déjà connue de l'usager n'ont pu servir. La politique
 * de transport est celle qu'imposent SPEC.md § 4.2, § 7.8 et § 11 : `User-Agent` obligatoire,
 * expiration de 30 s, une seule reprise, jamais sur une 4xx, et **aucune journalisation**.
 *
 * Le corps porte aussi un champ `serverConfig` que l'application n'exploite pas encore ;
 * `ignoreUnknownKeys` le laisse passer, comme tout ce que MOTIS ajoutera sans préavis.
 */
class MapApi(versionName: String, engine: HttpClientEngine = OkHttp.create()) : Closeable {

  private val json = Json {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis.
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
  }

  private val client = HttpClient(engine) {
    // Les statuts d'échec sont traduits en EscaleError, pas levés en exception.
    expectSuccess = false

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

  /** Centre et zoom de la zone de données du serveur, ou l'erreur qui a empêché de les lire. */
  suspend fun initialCamera(baseUrl: String): Outcome<MapCamera> = try {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.INITIAL_MAP))
    if (response.status.isSuccess()) {
      val body = json.decodeFromString(InitialMapDto.serializer(), response.bodyAsText())
      Outcome.Success(MapCamera(center = LatLon(body.lat, body.lon), zoom = body.zoom))
    } else {
      Outcome.Failure(HttpFailures.fromStatus(response.status.value, MotisEndpoints.INITIAL_MAP, null))
    }
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (failure: Exception) {
    Outcome.Failure(HttpFailures.fromThrowable(failure))
  }

  override fun close() {
    client.close()
  }

  /**
   * Le corps de `/api/v1/map/initial`. Il reste privé à ce fichier : un DTO ne sort jamais de
   * `:data` (docs/architecture.md § 2).
   */
  @Serializable
  private data class InitialMapDto(val lat: Double, val lon: Double, val zoom: Double)

  private companion object {
    const val TIMEOUT_MILLIS = 30_000L
    const val MAX_RETRIES = 1
    const val RETRY_DELAY_MILLIS = 500L
    const val SERVER_ERROR_START = 500

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
