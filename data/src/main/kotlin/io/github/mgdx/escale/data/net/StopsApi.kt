package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.query.StopsQueryBuilder
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.StopInfoDto
import io.github.mgdx.escale.data.dto.StopPlaceDto
import io.github.mgdx.escale.data.mapper.toStop
import io.github.mgdx.escale.data.mapper.toStops
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import java.io.Closeable

/**
 * Les arrêts affichés sur la carte : `GET /api/v6/map/stops` et `GET /api/v6/stop` (SPEC.md § 5.7).
 *
 * La politique de transport — expiration, reprise, `User-Agent`, **aucune journalisation** — vient
 * de [MotisTransport], comme pour tous les clients du projet. Les paramètres, eux, viennent de
 * `:core.query` : ce fichier ne fait que les poser sur la requête.
 *
 * **Rien n'est journalisé ici, et rien ne doit l'être** : ce point d'entrée reçoit l'emprise de
 * l'écran de l'usager à chaque arrêt de caméra, c'est-à-dire sa position à la minute près
 * (SPEC.md § 8 et § 11).
 */
class StopsApi(versionName: String, engine: HttpClientEngine = MotisTransport.okHttpEngine()) : Closeable {

  private val json = MotisTransport.json

  private val client = MotisTransport.httpClient(versionName, engine)

  /**
   * Les arrêts contenus dans [area], filtrés sur [modes].
   *
   * Un `Place` sans `stopId` est écarté par le mapping : il ne mènerait à aucune infobulle.
   */
  suspend fun mapStops(
    baseUrl: String,
    area: BoundingBox,
    modes: Set<TransitMode>,
    grouped: Boolean,
  ): Outcome<List<Stop>> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.MAP_STOPS)) {
      StopsQueryBuilder.mapStops(area, modes, grouped).forEach { (name, value) -> parameter(name, value) }
    }
    if (response.status.isSuccess()) {
      val places = json.decodeFromString(ListSerializer(StopPlaceDto.serializer()), response.bodyAsText())
      Outcome.Success(places.toStops())
    } else {
      MotisTransport.failure(response, MotisEndpoints.MAP_STOPS)
    }
  }

  /** L'arrêt et les lignes qui le desservent, pour l'infobulle de SPEC.md § 5.7. */
  suspend fun stop(baseUrl: String, stopId: String): Outcome<Stop> = MotisTransport.runCatchingHttp {
    val response = client.get(MotisEndpoints.url(baseUrl, MotisEndpoints.STOP)) {
      StopsQueryBuilder.stopInfo(stopId).forEach { (name, value) -> parameter(name, value) }
    }
    if (response.status.isSuccess()) {
      val info = json.decodeFromString(StopInfoDto.serializer(), response.bodyAsText())
      // Un corps valide décrivant un point sans identifiant : le serveur connaît le lieu mais pas
      // l'arrêt. Ce n'est pas une panne de transport, c'est une réponse inexploitable.
      info.toStop()?.let { Outcome.Success(it) } ?: Outcome.Failure(EscaleError.Unknown(cause = null))
    } else {
      MotisTransport.failure(response, MotisEndpoints.STOP)
    }
  }

  override fun close() {
    client.close()
  }
}
