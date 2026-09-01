package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/architecture.md § 10 : aucun test ne touche le réseau réel. Le corps de réponse est une
 * capture d'`api.transitous.org`, rangée dans `src/test/resources/fixtures/`.
 */
class MapApiTest {

  private val baseUrl = "https://exemple.org"
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  @Test
  fun `le cadrage initial est lu sur le bon point d'entrée`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("map_initial.json"), HttpStatusCode.OK, jsonHeaders)
    }
    val outcome = MapApi(versionName = "1.0.0", engine = engine).use { it.initialCamera(baseUrl) }

    val url = checkNotNull(request).url
    assertEquals("/api/v1/map/initial", url.encodedPath)
    assertTrue(outcome is Outcome.Success)
    val camera = (outcome as Outcome.Success).value
    assertEquals(45.187606782877495, camera.center.lat, 1e-12)
    assertEquals(12.903830464929342, camera.center.lon, 1e-12)
    assertEquals(4.0, camera.zoom, 1e-12)
  }

  @Test
  fun `l'en-tête User-Agent obligatoire de Transitous est posé`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("map_initial.json"), HttpStatusCode.OK, jsonHeaders)
    }
    MapApi(versionName = "9.9.9", engine = engine).use { it.initialCamera(baseUrl) }

    assertEquals(
      "Escale/9.9.9 (+https://github.com/mgdx/escale)",
      checkNotNull(request).headers[HttpHeaders.UserAgent],
    )
  }

  @Test
  fun `le champ serverConfig inconnu du DTO ne fait pas échouer la lecture`() = runTest {
    // La fixture porte `serverConfig` que le DTO ignore : `ignoreUnknownKeys` est ce qui permet à
    // MOTIS d'ajouter des champs sans casser l'application (docs/architecture.md § 7).
    val engine = MockEngine { respond(fixture("map_initial.json"), HttpStatusCode.OK, jsonHeaders) }
    val outcome = MapApi(versionName = "1.0.0", engine = engine).use { it.initialCamera(baseUrl) }
    assertTrue(outcome is Outcome.Success)
  }

  @Test
  fun `un serveur en panne rend une erreur, jamais une exception`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
    val outcome = MapApi(versionName = "1.0.0", engine = engine).use { it.initialCamera(baseUrl) }
    assertEquals(Outcome.Failure(EscaleError.ServerUnreachable(503)), outcome)
  }

  @Test
  fun `un point d'entrée absent n'est pas confondu avec une version d'API trop ancienne`() = runTest {
    // `/api/v1/map/initial` n'est pas un point d'entrée v6 : un 404 n'y veut pas dire
    // « serveur trop ancien » (SPEC.md § 4.3), seulement que ce serveur ne le sert pas.
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = MapApi(versionName = "1.0.0", engine = engine).use { it.initialCamera(baseUrl) }
    assertEquals(Outcome.Failure(EscaleError.ServerUnreachable(404)), outcome)
  }
}
