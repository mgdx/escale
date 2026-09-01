package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/architecture.md § 10 : aucun test ne touche le réseau réel, tout passe par le `MockEngine`
 * de Ktor et par des corps de réponse rangés dans `src/test/resources/fixtures/`.
 */
class MotisClientTest {

  private val baseUrl = "https://exemple.org"

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  @Test
  fun `chaque requete porte l en-tete User-Agent au format impose`() = runTest {
    var seenUserAgent: String? = null
    val engine = MockEngine { request ->
      seenUserAgent = request.headers[HttpHeaders.UserAgent]
      respond(fixture("health.json"), HttpStatusCode.OK, jsonHeaders)
    }
    MotisClient(versionName = "1.2.3", engine = engine).use { it.health(baseUrl) }
    assertEquals("Escale/1.2.3 (+https://github.com/mgdx/escale)", seenUserAgent)
  }

  @Test
  fun `l URL est construite a partir de la racine du serveur`() = runTest {
    var seenUrl: String? = null
    val engine = MockEngine { request ->
      seenUrl = request.url.toString()
      respond(fixture("health.json"), HttpStatusCode.OK, jsonHeaders)
    }
    MotisClient(versionName = "1.0.0", engine = engine).use { it.health("https://exemple.org/") }
    assertEquals("https://exemple.org/api/v1/health", seenUrl)
  }

  @Test
  fun `les champs inconnus de la reponse sont ignores`() = runTest {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis.
    val engine = MockEngine { respond(fixture("health.json"), HttpStatusCode.OK, jsonHeaders) }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.health(baseUrl) }
    val health = (outcome as Outcome.Success).value
    assertTrue(health.realtime)
    assertTrue(health.gbfs)
    assertTrue(health.fullyStarted)
  }

  @Test
  fun `un health en 400 reste un succes mais signale un demarrage incomplet`() = runTest {
    val engine = MockEngine { respond("{\"rt\":false,\"gbfs\":false}", HttpStatusCode.BadRequest, jsonHeaders) }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.health(baseUrl) }
    val health = (outcome as Outcome.Success).value
    assertEquals(false, health.fullyStarted)
  }

  @Test
  fun `un 404 sur un point d entree v6 annonce un serveur trop ancien`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.probeApiVersion(baseUrl) }
    assertEquals(
      EscaleError.ApiVersionTooOld("/api/v6/map/stops"),
      (outcome as Outcome.Failure).error,
    )
  }

  @Test
  fun `un 400 remonte le champ error de la reponse`() = runTest {
    val engine = MockEngine {
      respond(fixture("error_bad_request.json"), HttpStatusCode.BadRequest, jsonHeaders)
    }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.probeApiVersion(baseUrl) }
    assertEquals(
      EscaleError.BadRequest("invalid time format"),
      (outcome as Outcome.Failure).error,
    )
  }

  @Test
  fun `une erreur 4xx n est jamais reprise`() = runTest {
    var attempts = 0
    val engine = MockEngine {
      attempts++
      respondError(HttpStatusCode.BadRequest)
    }
    MotisClient(versionName = "1.0.0", engine = engine).use { it.probeApiVersion(baseUrl) }
    assertEquals(1, attempts)
  }

  @Test
  fun `une erreur 5xx est reprise une seule fois`() = runTest {
    var attempts = 0
    val engine = MockEngine {
      attempts++
      respondError(HttpStatusCode.InternalServerError)
    }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.probeApiVersion(baseUrl) }
    assertEquals(2, attempts)
    assertEquals(EscaleError.ServerUnreachable(500), (outcome as Outcome.Failure).error)
  }

  @Test
  fun `une panne de transport devient une erreur, jamais une exception`() = runTest {
    val engine = MockEngine { throw java.net.UnknownHostException("exemple.org") }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.health(baseUrl) }
    assertEquals(EscaleError.NoNetwork, (outcome as Outcome.Failure).error)
  }

  @Test
  fun `l absence de tuile n est pas une erreur`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = MotisClient(versionName = "1.0.0", engine = engine).use { it.probeTiles(baseUrl) }
    assertEquals(false, (outcome as Outcome.Success).value)
  }

  @Test
  fun `la tuile est demandee sur le chemin tiles du serveur`() = runTest {
    var seenUrl: String? = null
    val engine = MockEngine { request ->
      seenUrl = request.url.toString()
      respond("", HttpStatusCode.OK)
    }
    MotisClient(versionName = "1.0.0", engine = engine).use { it.probeTiles(baseUrl) }
    assertEquals("https://exemple.org/tiles/0/0/0.mvt", seenUrl)
  }
}
