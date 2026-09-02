package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
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
import org.junit.Test

/**
 * `/api/v6/map/stops` et `/api/v6/stop`, éprouvés sur des captures réelles d'`api.transitous.org`
 * (docs/architecture.md § 10 : aucun test ne touche le réseau réel).
 *
 * Les fixtures viennent d'une emprise du centre de Paris — Châtelet, Pont Neuf, la Cité — et d'un
 * arrêt public. Aucune coordonnée d'usager n'entre dans ce dépôt.
 */
class StopsApiTest {

  private val baseUrl = "https://exemple.org"
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val area = BoundingBox(min = LatLon(48.8534, 2.335), max = LatLon(48.862, 2.35))

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  @Test
  fun `l'emprise et le palier de zoom partent tels que la spec les decrit`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("map_stops_rail_only.json"), HttpStatusCode.OK, jsonHeaders)
    }
    StopsApi(versionName = "1.0.0", engine = engine).use {
      it.mapStops(baseUrl, area, ZoomTier.MAJOR_STATIONS.stopModes, grouped = true)
    }

    val url = checkNotNull(request).url
    assertEquals("/api/v6/map/stops", url.encodedPath)
    assertEquals("48.8534,2.335", url.parameters["min"])
    assertEquals("48.862,2.35", url.parameters["max"])
    assertEquals("true", url.parameters["grouped"])
    assertEquals(
      TransitMode.HEAVY_RAIL_MODES.map { it.name }.toSet(),
      checkNotNull(url.parameters["modes"]).split(",").toSet(),
    )
  }

  @Test
  fun `les arrets d'une emprise sont lus avec leurs modes`() = runTest {
    val engine = MockEngine { respond(fixture("map_stops_paris.json"), HttpStatusCode.OK, jsonHeaders) }
    val outcome = StopsApi(versionName = "1.0.0", engine = engine).use {
      it.mapStops(baseUrl, area, ZoomTier.ALL_STOPS.stopModes, grouped = true)
    }

    val stops = (outcome as Outcome.Success).value
    assertEquals(11, stops.size)
    val chatelet = checkNotNull(stops.firstOrNull { it.name == "Châtelet" })
    assertEquals(listOf(TransitMode.SUBWAY, TransitMode.BUS), chatelet.modes)
    assertEquals(48.85835266113281, chatelet.coordinates.lat, 1e-12)
    // Les lignes ne viennent pas de `map/stops` : les demander pour chaque point coûterait une
    // requête par arrêt (SPEC.md § 7).
    assertTrue(chatelet.lines.isEmpty())
  }

  @Test
  fun `les lignes desservies alimentent l'infobulle`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("stop_chatelet.json"), HttpStatusCode.OK, jsonHeaders)
    }
    val outcome = StopsApi(versionName = "1.0.0", engine = engine).use { it.stop(baseUrl, "IDFM:71264") }

    assertEquals("/api/v6/stop", checkNotNull(request).url.encodedPath)
    assertEquals("IDFM:71264", checkNotNull(request).url.parameters["stopId"])

    val stop = (outcome as Outcome.Success).value
    assertEquals("Châtelet", stop.name)
    // Trente lignes à Châtelet, cinq de métro et le reste en bus : le serveur les rend déjà
    // dédoublonnées, mais dans un ordre alphabétique où le bus 21 précède le métro 4.
    assertEquals(30, stop.lines.size)
    // Les modes structurants d'abord, puis les numéros dans l'ordre où l'usager les lit :
    // « 11 » vient après « 7 », pas après « 1 ».
    val metro = stop.lines.filter { it.mode == TransitMode.SUBWAY }.map { it.label }
    assertEquals(listOf("1", "4", "7", "11", "14"), metro)
    assertEquals(TransitMode.SUBWAY, stop.lines.first().mode)
    val line14 = checkNotNull(stop.lines.firstOrNull { it.label == "14" })
    assertEquals("#640082", line14.color)
    assertEquals("#FFFFFF", line14.textColor)
    assertEquals("RATP", line14.agencyName)
  }

  @Test
  fun `l'en-tete User-Agent obligatoire de Transitous est pose`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("map_stops_paris.json"), HttpStatusCode.OK, jsonHeaders)
    }
    StopsApi(versionName = "9.9.9", engine = engine).use { it.mapStops(baseUrl, area, emptySet(), true) }

    assertEquals(
      "Escale/9.9.9 (+https://github.com/mgdx/escale)",
      checkNotNull(request).headers[HttpHeaders.UserAgent],
    )
  }

  @Test
  fun `un ensemble de modes vide n'envoie pas de parametre modes`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("map_stops_paris.json"), HttpStatusCode.OK, jsonHeaders)
    }
    StopsApi(versionName = "1.0.0", engine = engine).use { it.mapStops(baseUrl, area, emptySet(), true) }

    assertNull(checkNotNull(request).url.parameters["modes"])
  }

  @Test
  fun `un serveur trop ancien est nomme, pas confondu avec une ressource absente`() = runTest {
    // `/api/v6/map/stops` est un point d'entrée v6 : un 404 y veut dire MOTIS antérieur à 2.9
    // (SPEC.md § 4.3), pas « aucun arrêt ».
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = StopsApi(versionName = "1.0.0", engine = engine).use {
      it.mapStops(baseUrl, area, emptySet(), true)
    }
    assertEquals(Outcome.Failure(EscaleError.ApiVersionTooOld("/api/v6/map/stops")), outcome)
  }

  @Test
  fun `un serveur en panne rend une erreur, jamais une exception`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
    val outcome = StopsApi(versionName = "1.0.0", engine = engine).use {
      it.mapStops(baseUrl, area, emptySet(), true)
    }
    assertEquals(Outcome.Failure(EscaleError.ServerUnreachable(503)), outcome)
  }

  @Test
  fun `un champ inconnu ajoute par le serveur ne fait pas echouer la lecture`() = runTest {
    // La capture porte `importance`, `tz`, `level` et `vertexType`, que le DTO ne lit pas :
    // `ignoreUnknownKeys` est ce qui permet à MOTIS d'ajouter des champs sans préavis.
    val engine = MockEngine { respond(fixture("map_stops_paris.json"), HttpStatusCode.OK, jsonHeaders) }
    val outcome = StopsApi(versionName = "1.0.0", engine = engine).use {
      it.mapStops(baseUrl, area, emptySet(), true)
    }
    assertTrue(outcome is Outcome.Success)
  }
}
