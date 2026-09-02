package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.TripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Le dépôt des départs et des courses (SPEC.md § 5.4 et § 5.3).
 *
 * Il n'a presque pas de logique, et c'est voulu : **aucun cache**, contrairement aux arrêts de la
 * carte, parce qu'un horaire temps réel se périme en secondes. Ce fichier vérifie les deux seules
 * choses qu'il décide — le serveur qu'il interroge, et l'ancrage d'une page suivante.
 */
class TripRepositoryImplTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val stopId = "at-Railway-Current-Reference-Data-2026_de:02000:10950:11:1"
  private val time: Instant = Instant.parse("2026-09-02T05:48:00Z")

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private fun repository(name: String, capture: (HttpRequestData) -> Unit = {}): TripRepository {
    val engine = MockEngine { request ->
      capture(request)
      respond(fixture(name), HttpStatusCode.OK, jsonHeaders)
    }
    return TripRepositoryImpl(
      api = TripApi(versionName = "1.0.0", engine = engine),
      serverRepository = FakeServerRepository("https://serveur.exemple"),
    )
  }

  @Test
  fun `la requete part sur le serveur configure, relu a chaque appel`() = runTest {
    var request: HttpRequestData? = null
    val outcome = repository("stoptimes_hamburg.json") { request = it }
      .departures(stopId, time, modes = DepartureModeFilter.TRAIN.requestModes)

    assertEquals("serveur.exemple", checkNotNull(request).url.host)
    assertTrue(outcome is Outcome.Success)
  }

  @Test
  fun `une page suivante s'ancre sur son curseur, jamais sur une heure en plus`() = runTest {
    var request: HttpRequestData? = null
    repository("stoptimes_hamburg.json") { request = it }
      .departures(stopId, time, cursor = "EARLIER|1788328020")

    val url = checkNotNull(request).url
    assertEquals("EARLIER|1788328020", url.parameters["pageCursor"])
    assertNull(url.parameters["time"])
  }

  @Test
  fun `le nombre d'evenements demande par defaut est celui du contrat`() = runTest {
    var request: HttpRequestData? = null
    repository("stoptimes_hamburg.json") { request = it }.departures(stopId, time)

    assertEquals(TripRepository.DEFAULT_EVENT_COUNT.toString(), checkNotNull(request).url.parameters["n"])
  }

  @Test
  fun `une course se demande par son seul identifiant`() = runTest {
    var request: HttpRequestData? = null
    val outcome = repository("trip_ice91.json") { request = it }.trip("course", detailedLegs = false)

    assertEquals("/api/v6/trip", checkNotNull(request).url.encodedPath)
    assertTrue(outcome is Outcome.Success)
  }
}
