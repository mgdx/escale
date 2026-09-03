package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPropulsionType
import io.github.mgdx.escale.core.model.RentalVehicleKind
import io.github.mgdx.escale.core.model.countsByKind
import io.github.mgdx.escale.core.model.hasDockCounts
import io.github.mgdx.escale.core.model.stationFor
import io.github.mgdx.escale.core.model.vehiclesFor
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `/api/v1/rentals`, éprouvé sur des captures réelles d'`api.transitous.org`
 * (docs/architecture.md § 10 : aucun test ne touche le réseau réel).
 *
 * Les trois fixtures sont des lieux publics, capturées le 2 septembre 2026 vers 05 h 06 UTC avec
 * l'en-tête `User-Agent: Escale/1.0.0 (+https://github.com/mgdx/escale)` :
 *
 * - `rentals_berlin_hauptbahnhof.json` — trois exploitants sur le même parvis, dont un qui décline
 *   trois types de vélos ; c'est la station de `plan_rental_direct.json` ;
 * - `rentals_brussels_closed_station.json` — une station **sans nom, vide, et qui ne loue plus** ;
 * - `rentals_konstanz_free_floating.json` — six véhicules isolés et une station à deux natures.
 *
 * **Le libre-service est une donnée volatile** : ces fichiers valent pour ce qu'ils étaient à cette
 * minute-là, et c'est précisément la raison d'être du cache d'une minute et de l'heure de relevé
 * affichée à l'écran (SPEC.md § 5.3 et § 5.7).
 */
class RentalsApiTest {

  private val baseUrl = "https://exemple.org"
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val at = Instant.parse("2026-09-02T05:06:25Z")

  private val berlin = LatLon(52.52369, 13.37076)
  private val nextbike = "Jelbi S+U Hauptbahnhof/Washingtonplatz (MOA/HW)"

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private fun api(engine: MockEngine) = RentalsApi(versionName = "1.0.0", engine = engine)

  // --- La requête -------------------------------------------------------------------------------

  @Test
  fun `le point et le rayon partent tels que la spec les decrit`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("rentals_berlin_hauptbahnhof.json"), HttpStatusCode.OK, jsonHeaders)
    }
    api(engine).use { it.around(baseUrl, berlin, radiusMeters = 50, retrievedAt = at) }

    val url = checkNotNull(request).url
    assertEquals("/api/v1/rentals", url.encodedPath)
    assertEquals("52.52369,13.37076", url.parameters["point"])
    assertEquals("50", url.parameters["radius"])
    assertEquals("false", url.parameters["withVehicles"])
    assertEquals("false", url.parameters["withZones"])
  }

  @Test
  fun `l'en-tete User-Agent impose par Transitous accompagne chaque requete`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("rentals_berlin_hauptbahnhof.json"), HttpStatusCode.OK, jsonHeaders)
    }
    api(engine).use { it.around(baseUrl, berlin, radiusMeters = 50, retrievedAt = at) }

    assertEquals(
      "Escale/1.0.0 (+https://github.com/mgdx/escale)",
      checkNotNull(request).headers[HttpHeaders.UserAgent],
    )
  }

  @Test
  fun `l'emprise de la carte part en coin sud-ouest puis coin nord-est`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("rentals_konstanz_free_floating.json"), HttpStatusCode.OK, jsonHeaders)
    }
    val area = BoundingBox(min = LatLon(47.66, 9.173), max = LatLon(47.664, 9.178))
    api(engine).use { it.within(baseUrl, area, retrievedAt = at) }

    val url = checkNotNull(request).url
    assertEquals("47.66,9.173", url.parameters["min"])
    assertEquals("47.664,9.178", url.parameters["max"])
  }

  // --- Les stations -----------------------------------------------------------------------------

  @Test
  fun `les stations d'un parvis sont lues avec leur exploitant et leur heure de releve`() = runTest {
    val stations = around("rentals_berlin_hauptbahnhof.json")

    assertEquals(3, stations.size)
    val station = checkNotNull(stations.stationFor(berlin, nextbike))
    assertEquals(12, station.numVehiclesAvailable)
    assertEquals("https://app.nextbike.net/station?id=171667497", station.rentalUriAndroid)
    assertEquals(listOf(RentalFormFactor.BICYCLE), station.formFactors)
    assertEquals(at, station.retrievedAt)
  }

  @Test
  fun `les identifiants de types opaques sont traduits en nature de vehicule`() = runTest {
    val station = checkNotNull(around("rentals_berlin_hauptbahnhof.json").stationFor(berlin, nextbike))

    // `196`, `431` et `446` ne veulent rien dire hors de la table `vehicleTypes` de l'exploitant.
    assertEquals(
      RentalVehicleKind(RentalFormFactor.BICYCLE, RentalPropulsionType.ELECTRIC_ASSIST),
      station.vehicleKinds["446"],
    )
    assertEquals(
      RentalVehicleKind(RentalFormFactor.BICYCLE, RentalPropulsionType.HUMAN),
      station.vehicleKinds["431"],
    )
    // Onze vélos mécaniques — deux références confondues — et un vélo à assistance électrique.
    assertEquals(listOf(11, 1), station.countsByKind().map { it.vehicles })
  }

  @Test
  fun `la table des types est reduite a ce que la station propose vraiment`() = runTest {
    val station = checkNotNull(around("rentals_berlin_hauptbahnhof.json").stationFor(berlin, nextbike))

    // L'exploitant publie treize entrées de types ; la station n'en cite que trois.
    assertEquals(setOf("196", "431", "446"), station.vehicleKinds.keys)
  }

  @Test
  fun `un lien d'exploitant vide devient une absence de lien`() = runTest {
    // Vingt-quatre stations relevées place de la Bastille, `rentalUriAndroid` valait `""` partout :
    // sans cette conversion, l'écran offrirait un bouton qui n'ouvre rien.
    val dott = around("rentals_berlin_hauptbahnhof.json").first {
      it.stationId.isNotEmpty() && it.name.startsWith("MOA")
    }
    assertNull(dott.rentalUriAndroid)
  }

  @Test
  fun `aucun flux releve ne publie de places libres, et l'absence se distingue du zero`() = runTest {
    // Vélib', Villo et nextbike ont pourtant des bornes. SPEC.md § 5.3 demande « 4 places libres à
    // l'arrivée » ; le champ est mappé fidèlement, et l'écran tait la mention faute de donnée.
    assertTrue(around("rentals_berlin_hauptbahnhof.json").none { it.hasDockCounts() })
  }

  // --- Les cas limites --------------------------------------------------------------------------

  @Test
  fun `une station vide qui ne loue plus est lue telle quelle`() = runTest {
    val stations = around("rentals_brussels_closed_station.json")
    val closed = checkNotNull(stations.firstOrNull { it.name.isEmpty() })

    assertEquals(0, closed.numVehiclesAvailable)
    assertFalse("une station hors service ne loue pas", closed.isRenting)
    assertFalse("et n'accepte pas de retour : cela change le trajet", closed.isReturning)
    // Elle détaille pourtant ses types, tous à zéro : « vide » n'est pas « inconnue ».
    assertEquals(0, closed.vehiclesFor(RentalFormFactor.BICYCLE))
    assertTrue(closed.countsByKind().isEmpty())
  }

  @Test
  fun `les vehicules en free-floating deviennent des disponibilites d'un vehicule`() = runTest {
    val area = BoundingBox(min = LatLon(47.66, 9.173), max = LatLon(47.664, 9.178))
    val engine = MockEngine { respond(fixture("rentals_konstanz_free_floating.json"), HttpStatusCode.OK, jsonHeaders) }
    val outcome = api(engine).use { it.within(baseUrl, area, retrievedAt = at) }
    val all = (outcome as Outcome.Success).value

    // Une station nommée, et six véhicules isolés qui n'en portent aucun nom.
    assertEquals(7, all.size)
    val vehicles = all.filter { it.name.isEmpty() }
    assertEquals(6, vehicles.size)
    assertTrue("un véhicule libre vaut un", vehicles.all { it.numVehiclesAvailable == 1 })
    assertEquals(
      listOf(RentalFormFactor.SCOOTER_STANDING),
      vehicles.first { it.formFactors.contains(RentalFormFactor.SCOOTER_STANDING) }.formFactors,
    )
  }

  @Test
  fun `une reponse enrichie de champs inconnus se lit quand meme`() = runTest {
    val body = """
      {
        "providers": [
          {
            "id": "p", "name": "P", "champInconnu": 1,
            "vehicleTypes": [{"id": "t", "formFactor": "MARS_ROVER", "propulsionType": "ANTIMATIERE"}]
          }
        ],
        "stations": [
          {
            "id": "s", "providerId": "p", "name": "Station", "lat": 1.0, "lon": 2.0,
            "isRenting": true, "isReturning": true, "numVehiclesAvailable": 4,
            "formFactors": ["BICYCLE", "SOUCOUPE"], "vehicleTypesAvailable": {"t": 4},
            "vehicleDocksAvailable": {}, "bbox": [2.0, 1.0, 2.0, 1.0]
          }
        ],
        "vehicles": [], "zones": [], "providerGroups": []
      }
    """.trimIndent()
    val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }
    val outcome = api(engine).use { it.around(baseUrl, LatLon(1.0, 2.0), radiusMeters = 50, retrievedAt = at) }

    val station = (outcome as Outcome.Success).value.single()
    assertEquals(4, station.numVehiclesAvailable)
    // Une valeur d'énumération inconnue se replie, elle ne fait pas échouer toute la réponse :
    // `/api/v1/rentals` est marqué expérimental côté MOTIS.
    assertEquals(listOf(RentalFormFactor.BICYCLE), station.formFactors)
    assertEquals(RentalVehicleKind(null, null), station.vehicleKinds["t"])
  }

  @Test
  fun `un serveur muet donne une erreur explicite, pas une liste vide`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
    val outcome = api(engine).use { it.around(baseUrl, berlin, radiusMeters = 50, retrievedAt = at) }

    assertEquals(
      EscaleError.ServerUnreachable(HttpStatusCode.ServiceUnavailable.value),
      (outcome as Outcome.Failure).error,
    )
  }

  @Test
  fun `un 404 sur ce point d'entree v1 ne veut pas dire serveur trop ancien`() = runTest {
    // Le piège n° 7 de docs/motis-api.md ne vaut que pour `/api/v6/` : ici, un 404 reste ordinaire.
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = api(engine).use { it.around(baseUrl, berlin, radiusMeters = 50, retrievedAt = at) }

    assertEquals(EscaleError.ServerUnreachable(HttpStatusCode.NotFound.value), (outcome as Outcome.Failure).error)
  }

  private suspend fun around(fixture: String) = api(
    MockEngine { respond(fixture(fixture), HttpStatusCode.OK, jsonHeaders) },
  ).use { (it.around(baseUrl, berlin, radiusMeters = 50, retrievedAt = at) as Outcome.Success).value }
}
