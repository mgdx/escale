package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.query.StopTimesQueryBuilder
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.PlanItineraryDto
import io.github.mgdx.escale.data.dto.StopTimesResponseDto
import io.github.mgdx.escale.data.mapper.toDomain
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.Closeable
import java.time.Instant

/**
 * Les prochains départs à un arrêt et la desserte d'une course : `GET /api/v6/stoptimes` et
 * `GET /api/v6/trip` (SPEC.md § 5.4 et § 5.3).
 *
 * La politique de transport — expiration, reprise, `User-Agent`, **aucune journalisation** — vient
 * de [MotisTransport], comme pour tous les clients du projet. Les paramètres viennent de
 * `:core.query` : ce fichier ne fait que les poser sur la requête.
 *
 * **Rien n'est journalisé ici, et rien ne doit l'être** : un identifiant d'arrêt dit où se trouve
 * l'usager (SPEC.md § 8 et § 11).
 */
class TripApi(versionName: String, engine: HttpClientEngine = MotisTransport.okHttpEngine()) : Closeable {

  private val json = MotisTransport.json

  private val client = MotisTransport.httpClient(versionName, engine)

  /** Les prochains départs (ou arrivées) à un arrêt, avec leurs perturbations. */
  @Suppress("LongParameterList")
  suspend fun stopTimes(
    baseUrl: String,
    stopId: String,
    time: Instant?,
    count: Int,
    modes: Set<TransitMode>,
    arriveBy: Boolean,
    cursor: String?,
  ): Outcome<StopTimePage> = MotisTransport.runCatchingHttp {
    val parameters = StopTimesQueryBuilder.stopTimes(
      stopId = stopId,
      time = time,
      count = count,
      modes = modes,
      arriveBy = arriveBy,
      cursor = cursor,
    )
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.STOPTIMES)) {
      parameters.forEach { (name, value) -> parameter(name, value) }
    }
    if (response.status.isSuccess()) {
      val body = json.decodeFromString(StopTimesResponseDto.serializer(), response.bodyAsText())
      Outcome.Success(body.toDomain(arriveBy))
    } else {
      MotisTransport.failure(response, MotisEndpoints.STOPTIMES)
    }
  }

  /**
   * La desserte complète d'une course.
   *
   * Le serveur la rend sous la forme d'un `Itinerary` : le mapping est donc **celui des
   * itinéraires**, réemployé tel quel. Écrire un second mapping pour le même schéma serait la
   * garantie que les deux divergent le jour où l'API change.
   */
  suspend fun trip(baseUrl: String, tripId: String, detailedLegs: Boolean): Outcome<Journey> =
    MotisTransport.runCatchingHttp {
      val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.TRIP)) {
        StopTimesQueryBuilder.trip(tripId, detailedLegs).forEach { (name, value) -> parameter(name, value) }
      }
      if (response.status.isSuccess()) {
        val body = json.decodeFromString(PlanItineraryDto.serializer(), response.bodyAsText())
        Outcome.Success(body.toDomain())
      } else {
        MotisTransport.failure(response, MotisEndpoints.TRIP)
      }
    }

  override fun close() {
    client.close()
  }
}
