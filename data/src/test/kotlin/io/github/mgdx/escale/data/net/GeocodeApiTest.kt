package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.LatLon
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * docs/architecture.md § 10 : aucun test ne touche le réseau réel. Les corps de réponse sont des
 * captures d'`api.transitous.org` rangées dans `src/test/resources/fixtures/`.
 */
class GeocodeApiTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val baseUrl = "https://exemple.org"
  private val paris = LatLon(48.8566, 2.3522)
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private fun engineServing(name: String, capture: (HttpRequestData) -> Unit = {}) = MockEngine { request ->
    capture(request)
    respond(fixture(name), HttpStatusCode.OK, jsonHeaders)
  }

  @Test
  fun `l autocompletion envoie les quatre parametres de la spec`() = runTest {
    var request: HttpRequestData? = null
    val engine = engineServing("geocode_rue_de_rivoli.json") { request = it }
    GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Rue de Rivoli", bias = paris, language = "fr", limit = 10)
    }
    val url = checkNotNull(request).url
    assertEquals("https://exemple.org/api/v1/geocode", "${url.protocol.name}://${url.host}${url.encodedPath}")
    assertEquals("Rue de Rivoli", url.parameters["text"])
    // SPEC.md § 5.1 : `place` est le centre de la carte, il biaise les résultats vers l'usager.
    assertEquals("48.8566,2.3522", url.parameters["place"])
    assertEquals("fr", url.parameters["language"])
    assertEquals("10", url.parameters["numResults"])
  }

  @Test
  fun `sans biais ni langue, les parametres facultatifs sont absents`() = runTest {
    var request: HttpRequestData? = null
    val engine = engineServing("geocode_rue_de_rivoli.json") { request = it }
    GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Rue de Rivoli", bias = null, language = null, limit = 10)
    }
    assertNull(checkNotNull(request).url.parameters["place"])
    assertNull(checkNotNull(request).url.parameters["language"])
  }

  @Test
  fun `chaque requete porte l en-tete User-Agent au format impose`() = runTest {
    var request: HttpRequestData? = null
    val engine = engineServing("geocode_rue_de_rivoli.json") { request = it }
    GeocodeApi(versionName = "1.2.3", engine = engine).use {
      it.geocode(baseUrl, "Rue de Rivoli", bias = null, language = null, limit = 10)
    }
    assertEquals(
      "Escale/1.2.3 (+https://github.com/mgdx/escale)",
      checkNotNull(request).headers[HttpHeaders.UserAgent],
    )
  }

  @Test
  fun `une reponse melant arrets et adresses est rendue dans l ordre du serveur`() = runTest {
    val engine = engineServing("geocode_rue_de_rivoli.json")
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Rue de Rivoli", bias = paris, language = "fr", limit = 10)
    }
    val locations = (outcome as Outcome.Success).value
    assertEquals(10, locations.size)
    assertEquals("Rue de Rivoli", locations.first().name)
    assertEquals("Louvre - Rivoli", locations[5].name)
  }

  @Test
  fun `une autocompletion sans resultat est un succes, pas une erreur`() = runTest {
    val engine = engineServing("geocode_no_result.json")
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "qzxvwkjhgfd", bias = null, language = null, limit = 10)
    }
    assertEquals(emptyList<Any>(), (outcome as Outcome.Success).value)
  }

  @Test
  fun `les champs inconnus de la reponse sont ignores`() = runTest {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis.
    val engine = engineServing("geocode_unknown_fields.json")
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Gare de Lyon", bias = null, language = null, limit = 10)
    }
    assertEquals(2, (outcome as Outcome.Success).value.size)
  }

  @Test
  fun `le geocodage inverse demande le point et le nombre de resultats`() = runTest {
    var request: HttpRequestData? = null
    val engine = engineServing("reverse_geocode_bastille.json") { request = it }
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.reverseGeocode(baseUrl, LatLon(48.8532, 2.3692), limit = 1)
    }
    val url = checkNotNull(request).url
    assertEquals(
      "https://exemple.org/api/v1/reverse-geocode",
      "${url.protocol.name}://${url.host}${url.encodedPath}",
    )
    assertEquals("48.8532,2.3692", url.parameters["place"])
    assertEquals("1", url.parameters["numResults"])
    assertEquals("Colonne de Juillet", (outcome as Outcome.Success).value.first().name)
  }

  @Test
  fun `un 400 remonte le champ error de la reponse`() = runTest {
    val engine = MockEngine {
      respond(fixture("error_bad_request.json"), HttpStatusCode.BadRequest, jsonHeaders)
    }
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Gare", bias = null, language = null, limit = 10)
    }
    assertEquals(EscaleError.BadRequest("invalid time format"), (outcome as Outcome.Failure).error)
  }

  @Test
  fun `une erreur 5xx distingue le serveur en panne de l absence de resultat`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Gare", bias = null, language = null, limit = 10)
    }
    assertEquals(EscaleError.ServerUnreachable(500), (outcome as Outcome.Failure).error)
  }

  @Test
  fun `l absence de reseau devient une erreur, jamais une exception`() = runTest {
    val engine = MockEngine { throw java.net.UnknownHostException("exemple.org") }
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Gare", bias = null, language = null, limit = 10)
    }
    assertEquals(EscaleError.NoNetwork, (outcome as Outcome.Failure).error)
  }

  @Test
  fun `un 404 sur un point d entree v1 ne parle pas de version de serveur`() = runTest {
    // SPEC.md § 4.3 ne réserve `ApiVersionTooOld` qu'aux points d'entrée v6.
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = GeocodeApi(versionName = "1.0.0", engine = engine).use {
      it.geocode(baseUrl, "Gare", bias = null, language = null, limit = 10)
    }
    assertTrue((outcome as Outcome.Failure).error is EscaleError.ServerUnreachable)
  }

  @Test
  fun `vider le cache d un client sans cache disque ne fait rien`() = runTest {
    val engine = engineServing("geocode_no_result.json")
    GeocodeApi(versionName = "1.0.0", engine = engine).use { it.clearCache() }
  }

  @Test
  fun `le cache disque tient dans le repertoire fourni et se vide sur demande`() {
    // SPEC.md § 11 : les lieux cherchés ne sortent pas du répertoire privé que :app fournit.
    // SPEC.md § 5.6.1 : le changement de serveur doit pouvoir les effacer.
    val directory = temporaryFolder.newFolder("geocode-http")
    GeocodeApi(versionName = "1.0.0", cacheDirectory = directory).use { it.clearCache() }
    // OkHttp a bien pris ce répertoire : il y a posé son journal, et rien ailleurs.
    assertEquals(listOf("journal"), directory.listFiles().orEmpty().map { it.name })
  }
}
