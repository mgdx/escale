package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.format.Delay
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.model.DepartureFilters
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.DisruptionEffect
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.Disruptions
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.calls
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
 * `/api/v6/stoptimes` et `/api/v6/trip`, éprouvés sur des captures réelles d'`api.transitous.org`
 * (docs/architecture.md § 10 : aucun test ne touche le réseau réel).
 *
 * Les fixtures viennent de deux arrêts publics — Hamburg Hbf et Châtelet — et d'une course
 * grandes lignes. Aucune coordonnée d'usager n'entre dans ce dépôt.
 */
class TripApiTest {

  private val baseUrl = "https://exemple.org"
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val stopId = "at-Railway-Current-Reference-Data-2026_de:02000:10950:11:1"
  private val time: Instant = Instant.parse("2026-09-02T05:48:00Z")

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private suspend fun departures(
    name: String,
    modes: Set<TransitMode> = emptySet(),
    cursor: String? = null,
    capture: (HttpRequestData) -> Unit = {},
  ): StopTimePage {
    val engine = MockEngine { request ->
      capture(request)
      respond(fixture(name), HttpStatusCode.OK, jsonHeaders)
    }
    val outcome = TripApi(versionName = "1.0.0", engine = engine).use {
      it.stopTimes(baseUrl, stopId, time, count = 20, modes = modes, arriveBy = false, cursor = cursor)
    }
    return (outcome as Outcome.Success).value
  }

  // --- La requête ----------------------------------------------------------------------------

  @Test
  fun `les parametres de la spec partent sur stoptimes`() = runTest {
    var request: HttpRequestData? = null
    departures("stoptimes_hamburg.json") { request = it }

    val url = checkNotNull(request).url
    assertEquals("/api/v6/stoptimes", url.encodedPath)
    assertEquals(stopId, url.parameters["stopId"])
    assertEquals("2026-09-02T05:48:00Z", url.parameters["time"])
    assertEquals("20", url.parameters["n"])
    // SPEC.md § 5.4 l'exige nommément : sans lui, aucun bandeau de perturbation n'est possible.
    assertEquals("true", url.parameters["withAlerts"])
    assertEquals("LATER", url.parameters["direction"])
  }

  @Test
  fun `le filtre train envoie les feuilles de RAIL, jamais le parapluie`() = runTest {
    var request: HttpRequestData? = null
    val page = departures("stoptimes_rail_only.json", modes = DepartureModeFilter.TRAIN.requestModes) { request = it }

    val modes = checkNotNull(checkNotNull(request).url.parameters["mode"]).split(",").toSet()
    assertEquals(DepartureModeFilter.TRAIN.requestModes.map { it.name }.toSet(), modes)
    assertFalse(modes.contains(TransitMode.RAIL.name))

    // La capture est la preuve de terrain du piège n° 8 de docs/motis-api.md : demandée avec les
    // cinq feuilles, la réponse ne contient **aucun** métro, là où `mode=RAIL` en rendrait — et
    // elle contient des `REGIONAL_RAIL`, que le parapluie pris pour une feuille aurait fait
    // disparaître du filtre d'affichage.
    assertFalse(page.entries.any { it.mode == TransitMode.SUBWAY })
    assertTrue(page.entries.any { it.mode == TransitMode.REGIONAL_RAIL })
    assertTrue(page.entries.all { DepartureFilters.of(it.mode) == DepartureModeFilter.TRAIN })
  }

  @Test
  fun `une page suivante ne part qu'avec son curseur`() = runTest {
    var request: HttpRequestData? = null
    departures("stoptimes_hamburg.json", cursor = "LATER|1788328140") { request = it }

    val url = checkNotNull(request).url
    assertEquals("LATER|1788328140", url.parameters["pageCursor"])
    assertNull(url.parameters["time"])
  }

  @Test
  fun `l'en-tete User-Agent obligatoire de Transitous est pose`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("trip_ice91.json"), HttpStatusCode.OK, jsonHeaders)
    }
    TripApi(versionName = "9.9.9", engine = engine).use { it.trip(baseUrl, "course", detailedLegs = false) }

    assertEquals(
      "Escale/9.9.9 (+https://github.com/mgdx/escale)",
      checkNotNull(request).headers[HttpHeaders.UserAgent],
    )
  }

  // --- Les départs ---------------------------------------------------------------------------

  @Test
  fun `l'arret interroge, ses modes et les deux curseurs sont lus`() = runTest {
    val page = departures("stoptimes_hamburg.json")

    assertEquals("Hamburg Hauptbahnhof", checkNotNull(page.stop).name)
    assertEquals("EARLIER|1788328020", page.previousPageCursor)
    assertEquals("LATER|1788328140", page.nextPageCursor)
    assertEquals(8, page.entries.size)
  }

  @Test
  fun `heure, ligne, direction et quai alimentent la liste`() = runTest {
    val entry = departures("stoptimes_hamburg.json").entries.first()

    assertEquals(TransitMode.HIGHSPEED_RAIL, entry.mode)
    assertEquals("ICE 91", entry.lineName)
    assertEquals("Nürnberg Hbf", entry.headsign)
    assertEquals(Instant.parse("2026-09-02T05:48:00Z"), entry.time)
    assertEquals("20260902_07:34_de-DELFI_3383408655", entry.tripId)

    val bus = checkNotNull(departures("stoptimes_hamburg.json").entries.firstOrNull { it.lineName == "12" })
    assertEquals("1", bus.track)
  }

  @Test
  fun `une annulation est portee par l'entree, course comprise`() = runTest {
    val cancelled = departures("stoptimes_hamburg.json").entries.filter { it.cancelled }
    assertEquals(1, cancelled.size)
    assertEquals("18", cancelled.single().lineName)
    assertTrue(cancelled.single().tripCancelled)
  }

  @Test
  fun `une entree sans temps reel n'est jamais annoncee a l'heure`() = runTest {
    // Le piège respecté partout ailleurs (SPEC.md § 5.2) : sans temps réel, les deux heures sont
    // égales par construction et un écart nul ne prouve rien.
    val page = departures("stoptimes_alerts.json")
    assertTrue(page.entries.none { it.realTime })
    assertTrue(page.entries.all { it.delay() == null })
  }

  @Test
  fun `un retard se calcule sur l'ecart des deux heures`() = runTest {
    val late = checkNotNull(
      departures("stoptimes_rail_only.json").entries.firstOrNull { it.headsign == "Aumühle" },
    )
    val delay = checkNotNull(late.delay())
    assertEquals(2, delay.difference.toMinutes())
    assertEquals(DelayQuality.SLIGHT, delay.quality)
  }

  @Test
  fun `une avance se lit comme une avance, pas comme un retard`() = runTest {
    val early = checkNotNull(
      departures("stoptimes_hamburg.json").entries.firstOrNull { it.lineName == "X35" },
    )
    assertEquals(DelayQuality.EARLY, checkNotNull(early.delay()).quality)
  }

  @Test
  fun `les perturbations arrivent dans le place de l'entree, pas a sa racine`() = runTest {
    val disrupted = departures("stoptimes_alerts.json").entries.filter { it.alerts.isNotEmpty() }
    assertEquals(1, disrupted.size)
    val alert = disrupted.single().alerts.single()
    assertTrue(alert.headerText.startsWith("Bus 85"))
    assertTrue(alert.periods.isNotEmpty())
  }

  @Test
  fun `la description d'une perturbation arrive en texte, jamais en HTML`() = runTest {
    // La fixture porte bien le balisage tel que le réseau francilien l'émet : c'est le mapping,
    // et non l'écran, qui le réduit (docs/architecture.md § 1, SPEC.md § 2 — aucune WebView).
    val raw = fixture("stoptimes_alerts.json")
    assertTrue(raw.contains("<p>"))

    val alert = departures("stoptimes_alerts.json").entries.first { it.alerts.isNotEmpty() }.alerts.single()
    assertFalse(alert.descriptionText.contains('<'))
    assertTrue(alert.descriptionText.startsWith("La ligne 85 est déviée"))
    assertTrue(alert.descriptionText.endsWith("Raison : difficultés de circulation."))
  }

  @Test
  fun `la couleur de ligne est normalisee en dièse RRGGBB`() = runTest {
    val entry = checkNotNull(departures("stoptimes_alerts.json").entries.firstOrNull { it.lineName == "4" })
    assertEquals("#a0006e", entry.routeColor?.lowercase())
  }

  // --- La desserte d'une course ---------------------------------------------------------------

  @Test
  fun `la desserte complete d'une course va d'un terminus a l'autre`() = runTest {
    var request: HttpRequestData? = null
    val engine = MockEngine { captured ->
      request = captured
      respond(fixture("trip_ice91.json"), HttpStatusCode.OK, jsonHeaders)
    }
    val outcome = TripApi(versionName = "1.0.0", engine = engine).use {
      it.trip(baseUrl, "20260902_07:34_de-DELFI_3383408655", detailedLegs = false)
    }

    val url = checkNotNull(request).url
    assertEquals("/api/v6/trip", url.encodedPath)
    assertEquals("20260902_07:34_de-DELFI_3383408655", url.parameters["tripId"])
    assertEquals("false", url.parameters["detailedLegs"])

    val journey = (outcome as Outcome.Success).value
    val calls = journey.calls
    // Neuf arrêts intermédiaires plus les deux extrémités : c'est le recollement de `TripCalls`
    // qui rend les onze arrêts réellement desservis.
    assertEquals(11, calls.size)
    assertEquals("Altona", calls.first().place.name)
    assertEquals("Nürnberg Hbf", calls.last().place.name)
    assertEquals("Hamburg Hbf", calls[1].place.name)
    // Les quais sont rendus quand la source les publie, et seulement là.
    assertEquals("1", checkNotNull(calls.firstOrNull { it.place.name == "Ludwigslust Bahnhof" }).place.track)
    assertNull(calls[1].place.track)
  }

  @Test
  fun `une course porte des perturbations a deux niveaux, la course et chaque arret`() = runTest {
    // Fixture **enrichie à la main**, comme `plan_with_alerts_enriched.json` : aucune course
    // capturée sur api.transitous.org ne portait d'alerte par arrêt, et le schéma `Place` en prévoit
    // pourtant un tableau (docs/motis-openapi.yaml, schéma `Place`, champ `alerts`).
    val engine = MockEngine { respond(fixture("trip_ice91_stop_alerts.json"), HttpStatusCode.OK, jsonHeaders) }
    val journey = (
      TripApi(versionName = "1.0.0", engine = engine).use {
        it.trip(baseUrl, "20260902_07:34_de-DELFI_3383408655", detailedLegs = false)
      } as Outcome.Success
      ).value

    // Le niveau « course » ne récupère pas les perturbations d'arrêt, et réciproquement.
    assertEquals("ICE 91 : retard prévisible", journey.alerts.single().headerText)

    val disrupted = journey.calls.filter { it.place.alerts.isNotEmpty() }
    assertEquals(1, disrupted.size)
    assertEquals("S+U Berlin Hauptbahnhof", disrupted.single().place.name)
    val alert = disrupted.single().place.alerts.single()
    assertEquals(DisruptionSeverity.WARNING, alert.severity)
    assertEquals(DisruptionEffect.STOP_MOVED, alert.effect)
    // Le HTML de la description est réduit à l'entrée, l'adresse du lien étant conservée.
    assertEquals(
      "En raison de travaux, l'ICE 91 part voie 8 et non voie 3. " +
        "Voir l'information (https://www.bahn.de/meldungen).",
      alert.descriptionText,
    )
    // Ce que l'écran de course affichera sur cet arrêt : la perturbation de l'arrêt, pas celle de
    // la course, que le bandeau de tête annonce déjà.
    assertEquals(listOf(alert), Disruptions.excluding(disrupted.single().place.alerts, journey.alerts))
  }

  @Test
  fun `les arrets intermediaires d'un trajet portent aussi leurs perturbations`() = runTest {
    // Le même mapping sert à `plan` et à `trip` : un arrêt sans alerte rend une liste vide, jamais
    // un nul, et l'écran de détail n'a rien de particulier à traiter.
    val engine = MockEngine { respond(fixture("trip_ice91.json"), HttpStatusCode.OK, jsonHeaders) }
    val journey = (
      TripApi(versionName = "1.0.0", engine = engine).use { it.trip(baseUrl, "course", detailedLegs = false) }
        as Outcome.Success
      ).value
    assertTrue(journey.calls.all { it.place.alerts.isEmpty() })
  }

  // --- Les erreurs ----------------------------------------------------------------------------

  @Test
  fun `un serveur trop ancien est nomme, pas confondu avec une ressource absente`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val outcome = TripApi(versionName = "1.0.0", engine = engine).use {
      it.stopTimes(baseUrl, stopId, time, count = 20, modes = emptySet(), arriveBy = false, cursor = null)
    }
    assertEquals(Outcome.Failure(EscaleError.ApiVersionTooOld("/api/v6/stoptimes")), outcome)
  }

  @Test
  fun `un identifiant de course inconnu rend le message du serveur`() = runTest {
    val engine = MockEngine {
      respond(fixture("error_bad_request.json"), HttpStatusCode.BadRequest, jsonHeaders)
    }
    val outcome = TripApi(versionName = "1.0.0", engine = engine).use {
      it.trip(baseUrl, "course-inconnue", detailedLegs = false)
    }
    assertTrue(outcome is Outcome.Failure && outcome.error is EscaleError.BadRequest)
  }

  @Test
  fun `un champ inconnu ajoute par le serveur ne fait pas echouer la lecture`() = runTest {
    // Les captures portent `tripFrom`, `tripTo`, `reservation`, `source`, `importance` et bien
    // d'autres, que les DTO ne lisent pas : `ignoreUnknownKeys` est ce qui permet à MOTIS
    // d'ajouter des champs sans préavis.
    assertEquals(8, departures("stoptimes_hamburg.json").entries.size)
  }

  private fun StopTimeEntry.delay(): Delay? = Delay.between(time, scheduledTime, realTime)
}
