package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.GeocodeMatchDto
import io.github.mgdx.escale.data.mapper.toDomain
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Cache
import java.io.Closeable
import java.io.File

/**
 * Les deux points d'entrée de géocodage (`/api/v1/geocode`, `/api/v1/reverse-geocode`).
 *
 * **Pourquoi un client HTTP distinct de [MotisClient] ?** SPEC.md § 7.5 réclame un cache disque de
 * 24 h pour le seul géocodage, et SPEC.md § 5.6.1 réclame de pouvoir le vider en changeant de
 * serveur, sans toucher au reste. Un cache OkHttp est attaché à un répertoire et à un client : lui
 * en donner un à lui seul est ce qui rend la purge sûre et immédiate. La politique de transport,
 * elle, n'est **pas** recopiée : elle vient de [MotisTransport], comme pour [MotisClient]. Seul le
 * cache disque distingue les deux clients.
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
    this(
      versionName,
      // SPEC.md § 7.5 : 24 h de cache disque, et pour le seul géocodage.
      MotisTransport.okHttpEngine(MotisTransport.ResponseCache(cache, CACHE_SECONDS)),
      cache,
    )

  /** Client sans cache disque, pour les tests : le `MockEngine` de Ktor ne passe pas par OkHttp. */
  internal constructor(versionName: String, engine: HttpClientEngine) : this(versionName, engine, null)

  private val json = MotisTransport.json

  private val client = MotisTransport.httpClient(versionName, engine)

  /**
   * `GET /api/v1/geocode` : autocomplétion sur [text] (SPEC.md § 5.1).
   *
   * @param bias `place`, le centre de la carte, qui privilégie les résultats proches. Il est
   *   accompagné de `placeBias` ([PLACE_BIAS]), sans quoi le poids du serveur reste à 1.
   * @param language `language`, la langue de l'interface.
   * @param limit `numResults`.
   */
  suspend fun geocode(
    baseUrl: String,
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.GEOCODE)) {
      parameter("text", text)
      // Un poids sans point de référence n'a pas de sens, et un paramètre de plus salit le cache
      // disque : `placeBias` ne part qu'avec `place`.
      bias?.let {
        parameter("place", it.asParameter())
        parameter("placeBias", PLACE_BIAS)
      }
      language?.let { parameter("language", it) }
      parameter("numResults", limit)
    }
    if (response.status.isSuccess()) {
      Outcome.Success(decodeMatches(response))
    } else {
      MotisTransport.failure(response, MotisEndpoints.GEOCODE)
    }
  }

  /**
   * `GET /api/v1/reverse-geocode` : les lieux les plus proches de [point] (SPEC.md § 5.1).
   *
   * Ce point d'entrée ne prend pas de paramètre `language` — voir `docs/motis-openapi.yaml` : il ne
   * connaît que `place`, `type` et `numResults`. Le libellé rendu est celui d'OpenStreetMap.
   */
  suspend fun reverseGeocode(baseUrl: String, point: LatLon, limit: Int): Outcome<List<Location>> =
    MotisTransport.runCatchingHttp {
      val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.REVERSE_GEOCODE)) {
        parameter("place", point.asParameter())
        parameter("numResults", limit)
      }
      if (response.status.isSuccess()) {
        Outcome.Success(decodeMatches(response))
      } else {
        MotisTransport.failure(response, MotisEndpoints.REVERSE_GEOCODE)
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

  internal companion object {
    /**
     * `placeBias`, le poids du biais géographique de `/api/v1/geocode`.
     *
     * Le défaut du serveur vaut 1, et c'est trop peu : les arrêts homonymes de tout le pays passent
     * devant les adresses voisines. Relevé sur Transitous, carte centrée sur Paris,
     * `text=rue de la paix` :
     *
     * | `placeBias` | Six premiers résultats |
     * |---|---|
     * | absent (défaut 1) | dix arrêts « Rue de la Paix », de Bitche à Vierzon, aucune adresse |
     * | 3 | deux lieux à Vincennes, puis les mêmes arrêts lointains |
     * | 10 | lieux et adresses de la petite couronne uniquement |
     *
     * 5 est le compromis retenu : au-delà de 10, les grands arrêts nationaux (« Paris Gare de
     * Lyon », les gares TGV) disparaissent derrière des commerces homonymes du quartier.
     */
    const val PLACE_BIAS = 5

    /** SPEC.md § 7.5 : « cache disque de 24 h pour les résultats de géocodage ». */
    const val CACHE_SECONDS = 24 * 60 * 60

    /** De quoi retenir quelques centaines de réponses ; au-delà, OkHttp évince les plus anciennes. */
    const val MAX_CACHE_BYTES = 4L * 1024 * 1024

    /** `latitude,longitude` en degrés, format attendu par `place` (docs/motis-openapi.yaml). */
    fun LatLon.asParameter(): String = "$lat,$lon"
  }
}
