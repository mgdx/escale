package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.ErrorDto
import io.github.mgdx.escale.data.dto.GeocodeMatchDto
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Les deux points d'entrée de géocodage (`/api/v1/geocode`, `/api/v1/reverse-geocode`).
 *
 * **Pourquoi un client HTTP distinct de [MotisClient] ?** SPEC.md § 7.5 réclame un cache disque de
 * 24 h pour le seul géocodage, et SPEC.md § 5.6.1 réclame de pouvoir le vider en changeant de
 * serveur, sans toucher au reste. Un cache OkHttp est attaché à un répertoire et à un client : lui
 * en donner un à lui seul est ce qui rend la purge sûre et immédiate. La politique de transport
 * (expiration de 30 s, une seule reprise, jamais sur 4xx, `User-Agent` obligatoire, aucune
 * journalisation) est reprise à l'identique de [MotisClient] : elle est imposée par SPEC.md § 4.2,
 * § 7.8 et § 11, pas choisie point d'entrée par point d'entrée.
 *
 * **Confidentialité** : ce cache contient les lieux cherchés par l'usager. Il vit dans le
 * répertoire de cache privé de l'application, jamais dans un répertoire partagé, et [clearCache]
 * l'efface (SPEC.md § 11).
 */
class GeocodeApi private constructor(versionName: String, engine: HttpClientEngine, private val diskCache: Cache?) :
  Closeable {

  /**
   * Client de production : cache disque de 24 h dans [cacheDirectory], qui doit être un
   * sous-répertoire du cache privé de l'application.
   */
  constructor(versionName: String, cacheDirectory: File) :
    this(versionName, Cache(cacheDirectory, MAX_CACHE_BYTES))

  private constructor(versionName: String, cache: Cache) :
    this(versionName, cachingEngine(cache), cache)

  /** Client sans cache disque, pour les tests : le `MockEngine` de Ktor ne passe pas par OkHttp. */
  internal constructor(versionName: String, engine: HttpClientEngine) : this(versionName, engine, null)

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
   * `GET /api/v1/geocode` : autocomplétion sur [text] (SPEC.md § 5.1).
   *
   * @param bias `place`, le centre de la carte, qui privilégie les résultats proches.
   * @param language `language`, la langue de l'interface.
   * @param limit `numResults`.
   */
  suspend fun geocode(
    baseUrl: String,
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> = runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.GEOCODE)) {
      parameter("text", text)
      bias?.let { parameter("place", it.asParameter()) }
      language?.let { parameter("language", it) }
      parameter("numResults", limit)
    }
    if (response.status.isSuccess()) {
      Outcome.Success(decodeMatches(response))
    } else {
      failure(response, MotisEndpoints.GEOCODE)
    }
  }

  /**
   * `GET /api/v1/reverse-geocode` : les lieux les plus proches de [point] (SPEC.md § 5.1).
   *
   * Ce point d'entrée ne prend pas de paramètre `language` — voir `docs/motis-openapi.yaml` : il ne
   * connaît que `place`, `type` et `numResults`. Le libellé rendu est celui d'OpenStreetMap.
   */
  suspend fun reverseGeocode(baseUrl: String, point: LatLon, limit: Int): Outcome<List<Location>> = runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.REVERSE_GEOCODE)) {
      parameter("place", point.asParameter())
      parameter("numResults", limit)
    }
    if (response.status.isSuccess()) {
      Outcome.Success(decodeMatches(response))
    } else {
      failure(response, MotisEndpoints.REVERSE_GEOCODE)
    }
  }

  /**
   * Vide le cache disque de géocodage (SPEC.md § 5.6.1 et § 11). Bloquant : l'appelant s'assure de
   * ne pas être sur le fil principal.
   */
  fun clearCache() {
    diskCache?.evictAll()
  }

  override fun close() {
    client.close()
    diskCache?.close()
  }

  private suspend fun decodeMatches(response: HttpResponse): List<Location> =
    json.decodeFromString(ListSerializer(GeocodeMatchDto.serializer()), response.bodyAsText()).toDomain()

  /** Lit le champ `error` du corps quand il s'y trouve, sans jamais rien journaliser. */
  private suspend fun <T> failure(response: HttpResponse, endpoint: String): Outcome<T> {
    val message = runCatching {
      json.decodeFromString(ErrorDto.serializer(), response.bodyAsText()).error
    }.getOrNull()
    return Outcome.Failure(HttpFailures.fromStatus(response.status.value, endpoint, message))
  }

  /**
   * Rattrape toute défaillance du transport pour la traduire en `EscaleError` (SPEC.md § 8).
   * L'annulation de coroutine, elle, continue de se propager : c'est elle qui met fin à la requête
   * précédente quand l'usager tape un caractère de plus (SPEC.md § 7.1).
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

    /** SPEC.md § 7.5 : « cache disque de 24 h pour les résultats de géocodage ». */
    const val CACHE_SECONDS = 24 * 60 * 60

    /** De quoi retenir quelques centaines de réponses ; au-delà, OkHttp évince les plus anciennes. */
    const val MAX_CACHE_BYTES = 4L * 1024 * 1024

    fun userAgent(versionName: String): String = "Escale/$versionName (+https://github.com/mgdx/escale)"

    /** `latitude,longitude` en degrés, format attendu par `place` (docs/motis-openapi.yaml). */
    fun LatLon.asParameter(): String = "$lat,$lon"

    /**
     * Le moteur OkHttp et son cache disque.
     *
     * MOTIS ne renvoie aucun en-tête de cache sur ces deux points d'entrée : sans réécriture, rien
     * ne serait jamais mis en cache. L'intercepteur réseau impose donc la durée de vie voulue par
     * SPEC.md § 7.5 à la réponse avant qu'OkHttp ne décide de l'écrire.
     */
    fun cachingEngine(cache: Cache): HttpClientEngine = OkHttp.create {
      config {
        cache(cache)
        addNetworkInterceptor(
          Interceptor { chain ->
            chain.proceed(chain.request())
              .newBuilder()
              .removeHeader("Pragma")
              .header("Cache-Control", "public, max-age=$CACHE_SECONDS")
              .build()
          },
        )
      }
    }

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
