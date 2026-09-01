package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.ServerHealth
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.HealthResponseDto
import io.github.mgdx.escale.data.dto.PlanItineraryDto
import io.github.mgdx.escale.data.dto.PlanResponseDto
import io.github.mgdx.escale.data.mapper.toDomain
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import java.io.Closeable

/**
 * Client HTTP du serveur MOTIS (docs/architecture.md § 7).
 *
 * La politique de transport — expiration, reprise, `User-Agent`, négociation de contenu,
 * traduction des exceptions — vient de [MotisTransport], qui la tient pour tous les clients du
 * projet. Ce fichier ne décrit plus que les points d'entrée eux-mêmes.
 *
 * L'URL racine est passée à chaque appel plutôt que retenue par le client : l'écran « Serveur
 * MOTIS » doit pouvoir tester un serveur candidat sans changer celui qui est en service.
 */
class MotisClient(versionName: String, engine: HttpClientEngine = MotisTransport.okHttpEngine()) : Closeable {

  private val json = MotisTransport.json

  private val client = MotisTransport.httpClient(versionName, engine)

  /**
   * `GET /api/v1/health` : le serveur répond-il, et avec quels flux ?
   *
   * Le point d'entrée répond **400 avec le même corps** tant qu'il n'a pas parcouru un cycle
   * complet de ses flux. Ce cas est donc un succès, avec `fullyStarted = false`.
   */
  suspend fun health(baseUrl: String): Outcome<ServerHealth> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.HEALTH))
    when {
      response.status.isSuccess() -> Outcome.Success(decodeHealth(response, fullyStarted = true))
      response.status.value == BAD_REQUEST -> Outcome.Success(decodeHealth(response, fullyStarted = false))
      else -> MotisTransport.failure(response, MotisEndpoints.HEALTH)
    }
  }

  /**
   * Étape 2 du test de connexion (SPEC.md § 5.6.1) : un point d'entrée `v6` répond-il ?
   *
   * Interroge `map/stops` sur une emprise minuscule, la requête la moins coûteuse qui exerce `v6`.
   * Un 404 devient [EscaleError.ApiVersionTooOld].
   */
  suspend fun probeApiVersion(baseUrl: String): Outcome<Unit> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.MAP_STOPS)) {
      parameter("min", PROBE_BOX_MIN)
      parameter("max", PROBE_BOX_MAX)
    }
    if (response.status.isSuccess()) {
      Outcome.Success(Unit)
    } else {
      MotisTransport.failure(response, MotisEndpoints.MAP_STOPS)
    }
  }

  /**
   * Étape 3 du test de connexion : le serveur sert-il un fond de carte ?
   *
   * Un serveur sans tuiles reste parfaitement utilisable (SPEC.md § 5.7) : l'absence de tuile n'est
   * donc pas une erreur, mais un `false`.
   */
  suspend fun probeTiles(baseUrl: String): Outcome<Boolean> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.tileUrl(baseUrl, PROBE_TILE_ZOOM, 0, 0))
    Outcome.Success(response.status.isSuccess())
  }

  /**
   * `GET /api/v6/plan` : la recherche d'itinéraire.
   *
   * Les paramètres sont assemblés par `:core.query`, jamais ici : ce client ne fait que les poser
   * sur la requête. Une valeur vide reste envoyée telle quelle — `directModes=` est justement ce
   * qui empêche les trajets directs d'éliminer les trajets en transport en commun (SPEC.md § 5.2).
   */
  internal suspend fun plan(baseUrl: String, parameters: Map<String, String>): Outcome<PlanResponseDto> =
    getJson(baseUrl, MotisEndpoints.PLAN, parameters, PlanResponseDto.serializer())

  /** `GET /api/v6/refresh-itinerary` : recalcule un trajet déjà obtenu avec le temps réel du moment. */
  internal suspend fun refreshItinerary(baseUrl: String, parameters: Map<String, String>): Outcome<PlanItineraryDto> =
    getJson(baseUrl, MotisEndpoints.REFRESH_ITINERARY, parameters, PlanItineraryDto.serializer())

  override fun close() {
    client.close()
  }

  /** Un GET qui rend un corps JSON décodé, ou l'[EscaleError] correspondant au statut reçu. */
  private suspend fun <T> getJson(
    baseUrl: String,
    endpoint: String,
    parameters: Map<String, String>,
    serializer: DeserializationStrategy<T>,
  ): Outcome<T> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, endpoint)) {
      parameters.forEach { (name, value) -> parameter(name, value) }
    }
    if (response.status.isSuccess()) {
      Outcome.Success(json.decodeFromString(serializer, response.bodyAsText()))
    } else {
      MotisTransport.failure(response, endpoint)
    }
  }

  private suspend fun decodeHealth(response: HttpResponse, fullyStarted: Boolean): ServerHealth =
    json.decodeFromString(HealthResponseDto.serializer(), response.bodyAsText())
      .toDomain(fullyStarted)

  private companion object {
    const val BAD_REQUEST = 400
    const val PROBE_TILE_ZOOM = 0

    // Emprise volontairement minuscule : on teste la version de l'API, pas les données.
    const val PROBE_BOX_MIN = "0.0,0.0"
    const val PROBE_BOX_MAX = "0.001,0.001"
  }
}
