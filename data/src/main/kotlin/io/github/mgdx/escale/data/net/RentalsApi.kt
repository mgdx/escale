package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.query.RentalsQueryBuilder
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.RentalsResponseDto
import io.github.mgdx.escale.data.mapper.toAvailabilities
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.Closeable
import java.time.Instant

/**
 * Le libre-service : `GET /api/v1/rentals` (SPEC.md § 5.3 et § 5.7).
 *
 * La politique de transport — expiration de 30 s, **une seule** reprise, aucune reprise sur 4xx,
 * `User-Agent`, `ignoreUnknownKeys`, **aucune journalisation** — vient de [MotisTransport], comme
 * pour tous les clients du projet : elle est imposée par SPEC.md § 7.8 et § 4.2, elle n'est pas un
 * choix à refaire ici. Les paramètres, eux, viennent de `:core.query`.
 *
 * **Rien n'est journalisé, et rien ne doit l'être** : les coordonnées passées à ce point d'entrée
 * sont celles d'une station devant laquelle l'usager va se rendre, à l'heure où il va s'y rendre.
 * C'est exactement la donnée que SPEC.md § 8 et § 11 interdisent de tracer.
 *
 * Le point d'entrée est en `v1` : un 404 y reste un `ServerUnreachable` ordinaire, et ne signifie
 * pas « serveur trop ancien » (`docs/motis-api.md`, piège n° 7).
 */
class RentalsApi(versionName: String, engine: HttpClientEngine = MotisTransport.okHttpEngine()) : Closeable {

  private val json = MotisTransport.json

  private val client = MotisTransport.httpClient(versionName, engine)

  /**
   * Les stations autour d'un point, pour la portion en libre-service d'un trajet.
   *
   * @param radiusMeters rayon volontairement faible : on vise une station précise (SPEC.md § 5.3).
   * @param retrievedAt heure du relevé, portée par chaque disponibilité rendue et affichée telle
   *   quelle à côté du compte. Elle est fournie par l'appelant pour que le cache et l'écran datent
   *   la même réponse de la même heure.
   */
  suspend fun around(
    baseUrl: String,
    point: LatLon,
    radiusMeters: Int,
    retrievedAt: Instant,
  ): Outcome<List<RentalAvailability>> = get(baseUrl, RentalsQueryBuilder.around(point, radiusMeters), retrievedAt)

  /** Les stations et les véhicules isolés d'une emprise, pour les marqueurs de la carte. */
  suspend fun within(baseUrl: String, area: BoundingBox, retrievedAt: Instant): Outcome<List<RentalAvailability>> =
    get(baseUrl, RentalsQueryBuilder.within(area), retrievedAt)

  private suspend fun get(
    baseUrl: String,
    parameters: Map<String, String>,
    retrievedAt: Instant,
  ): Outcome<List<RentalAvailability>> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.RENTALS)) {
      parameters.forEach { (name, value) -> parameter(name, value) }
    }
    if (response.status.isSuccess()) {
      val body = json.decodeFromString(RentalsResponseDto.serializer(), response.bodyAsText())
      Outcome.Success(body.toAvailabilities(retrievedAt))
    } else {
      MotisTransport.failure(response, MotisEndpoints.RENTALS)
    }
  }

  override fun close() {
    client.close()
  }
}
