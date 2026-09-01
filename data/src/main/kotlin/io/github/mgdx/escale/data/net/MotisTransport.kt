package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.ErrorDto
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import java.io.IOException

/**
 * **La politique de transport HTTP du projet, écrite une seule fois.**
 *
 * [MotisClient] et [GeocodeApi] restent deux clients distincts — le second porte un cache disque de
 * 24 h que le reste du trafic ne doit surtout pas subir (SPEC.md § 7.5) — mais ils n'ont aucune
 * raison de diverger sur la manière de parler au serveur. Ce qui est imposé par la spec, et non
 * choisi point d'entrée par point d'entrée, vit donc ici :
 *
 * - `ignoreUnknownKeys = true` : l'API MOTIS ajoute des champs sans préavis (SPEC.md § 3) ;
 * - en-tête `User-Agent` sur **chaque** requête, au format imposé par la politique d'usage de
 *   Transitous, la version venant de `BuildConfig` et jamais d'une constante recopiée
 *   (SPEC.md § 4.2) ;
 * - délai d'expiration de 30 s, **une seule** reprise, aucune reprise sur 4xx (SPEC.md § 7.8) ;
 * - **aucune journalisation** de corps de requête ni de réponse, même en débogage : le greffon
 *   `Logging` de Ktor n'est délibérément pas installé (SPEC.md § 8 et § 11).
 *
 * Ces cinq points sont couverts par les tests de `MotisClientTest` et de `GeocodeApiTest`, sur les
 * deux clients : la factorisation ne les dispense pas d'être vérifiés là où ils s'appliquent.
 */
internal object MotisTransport {

  /**
   * L'unique configuration de désérialisation du projet.
   *
   * `Json` est immuable et sans état : une instance partagée par tous les clients est aussi sûre
   * qu'une par client, et supprime le risque qu'un `ignoreUnknownKeys` soit oublié quelque part.
   */
  val json: Json = Json {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis.
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
  }

  /**
   * Un cache disque à attacher au moteur, et la durée de vie à imposer aux réponses qui y entrent.
   *
   * MOTIS ne renvoie aucun en-tête de cache : sans réécriture, OkHttp n'écrirait jamais rien.
   */
  data class ResponseCache(val cache: Cache, val lifetimeSeconds: Int)

  /**
   * Un client Ktor conforme à la politique ci-dessus.
   *
   * @param versionName version de l'application, lue depuis `BuildConfig` par `:app`.
   * @param engine moteur du client : celui de [okHttpEngine] en production, le `MockEngine` de Ktor
   *   dans les tests.
   */
  fun httpClient(versionName: String, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    // Les statuts d'échec sont traduits en EscaleError par HttpFailures, pas levés en exception.
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
      agent = "Escale/$versionName (+https://github.com/mgdx/escale)"
    }
  }

  /**
   * Le moteur OkHttp de production.
   *
   * @param responseCache cache disque à attacher, ou `null` pour le trafic qui ne doit pas être mis
   *   en cache — c'est-à-dire tout sauf le géocodage (SPEC.md § 7.5).
   */
  fun okHttpEngine(responseCache: ResponseCache? = null): HttpClientEngine = if (responseCache == null) {
    OkHttp.create()
  } else {
    OkHttp.create {
      config {
        cache(responseCache.cache)
        addNetworkInterceptor(cacheLifetimeInterceptor(responseCache.lifetimeSeconds))
      }
    }
  }

  /**
   * Traduit un statut d'échec en [io.github.mgdx.escale.core.result.EscaleError], en reprenant le
   * champ `error` du corps quand il s'y trouve. Rien n'est journalisé : le corps comme l'URL
   * peuvent porter la requête de l'usager (SPEC.md § 8 et § 11).
   *
   * @param endpoint chemin relatif appelé, par exemple `/api/v6/plan`, sans hôte ni paramètre.
   */
  suspend fun <T> failure(response: HttpResponse, endpoint: String): Outcome<T> {
    val message = runCatching {
      json.decodeFromString(ErrorDto.serializer(), response.bodyAsText()).error
    }.getOrNull()
    return Outcome.Failure(HttpFailures.fromStatus(response.status.value, endpoint, message))
  }

  /**
   * Rattrape toute défaillance du transport pour la traduire en `EscaleError` : SPEC.md § 8 impose
   * que l'application explique l'échec plutôt que de planter.
   *
   * L'annulation de coroutine, elle, doit continuer de se propager : c'est elle qui met fin à la
   * requête précédente quand l'usager tape un caractère de plus, et sans quoi les annulations de
   * requêtes de SPEC.md § 7 ne marcheraient plus.
   */
  inline fun <T> runCatchingHttp(block: () -> Outcome<T>): Outcome<T> = try {
    block()
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (failure: Exception) {
    Outcome.Failure(HttpFailures.fromThrowable(failure))
  }

  private const val TIMEOUT_MILLIS = 30_000L
  private const val MAX_RETRIES = 1
  private const val RETRY_DELAY_MILLIS = 500L
  private const val SERVER_ERROR_START = 500

  /** Impose la durée de vie voulue à la réponse avant qu'OkHttp ne décide de l'écrire. */
  private fun cacheLifetimeInterceptor(lifetimeSeconds: Int) = Interceptor { chain ->
    chain.proceed(chain.request())
      .newBuilder()
      .removeHeader("Pragma")
      .header("Cache-Control", "public, max-age=$lifetimeSeconds")
      .build()
  }

  /**
   * Une panne de transport mérite une seconde tentative, pas une expiration : reprendre après un
   * délai de 30 s ferait attendre l'usager une minute pour rien.
   */
  private fun isRetryable(cause: Throwable): Boolean = when (cause) {
    is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> false
    is IOException -> true
    else -> false
  }
}
