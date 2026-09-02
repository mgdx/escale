package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.geo.StopsCache
import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.StopsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le cache par emprise du dépôt d'arrêts (SPEC.md § 5.7, règle 4), compté en requêtes réellement
 * parties : c'est la seule mesure qui prouve qu'une emprise déjà couverte n'est pas redemandée.
 */
class StopsRepositoryImplTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  /** L'emprise déjà chargée, et une emprise strictement incluse dedans. */
  private val wide = BoundingBox(min = LatLon(48.84, 2.32), max = LatLon(48.88, 2.38))
  private val inside = BoundingBox(min = LatLon(48.85, 2.34), max = LatLon(48.86, 2.36))
  private val elsewhere = BoundingBox(min = LatLon(45.74, 4.82), max = LatLon(45.78, 4.86))

  private val rail = ZoomTier.MAJOR_STATIONS.stopModes
  private val everything = ZoomTier.ALL_STOPS.stopModes

  private var requests = 0

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private fun engine() = MockEngine {
    requests += 1
    respond(fixture("map_stops_paris.json"), HttpStatusCode.OK, jsonHeaders)
  }

  private fun repository(servers: FakeServerRepository = FakeServerRepository(), cache: StopsCache = StopsCache()) =
    StopsRepositoryImpl(
      api = StopsApi(versionName = "1.0.0", engine = engine()),
      serverRepository = servers,
      cache = cache,
    )

  @Test
  fun `une emprise deja couverte par le cache n'est pas redemandee`() = runTest {
    val repository = repository()
    val first = repository.stopsIn(wide, rail)
    val second = repository.stopsIn(inside, rail)

    assertEquals(1, requests)
    assertEquals((first as Outcome.Success).value, (second as Outcome.Success).value)
  }

  @Test
  fun `une emprise ailleurs est bien demandee`() = runTest {
    val repository = repository()
    repository.stopsIn(wide, rail)
    repository.stopsIn(elsewhere, rail)
    assertEquals(2, requests)
  }

  @Test
  fun `redescendre d'un palier ne redemande rien`() = runTest {
    // SPEC.md § 5.7, règle 5, vue du dépôt : la réponse du palier 13 contient déjà les gares du
    // palier 11. Repasser au palier 11 sur une emprise incluse n'a rien à demander.
    val repository = repository()
    repository.stopsIn(wide, everything)
    repository.stopsIn(inside, rail)
    assertEquals(1, requests)
  }

  @Test
  fun `monter d'un palier demande les modes qui manquent`() = runTest {
    val repository = repository()
    repository.stopsIn(wide, rail)
    repository.stopsIn(inside, everything)
    assertEquals(2, requests)
  }

  @Test
  fun `passe dix minutes, l'emprise est redemandee`() = runTest {
    var clock = Instant.parse("2026-09-01T08:00:00Z")
    val repository = repository(cache = StopsCache(now = { clock }))
    repository.stopsIn(wide, rail)
    clock = clock.plus(Duration.ofMinutes(11))
    repository.stopsIn(inside, rail)
    assertEquals(2, requests)
  }

  @Test
  fun `changer de serveur vide le cache`() = runTest {
    // Les identifiants d'arrêts d'un serveur ne veulent rien dire sur un autre (SPEC.md § 5.6.1).
    val servers = FakeServerRepository()
    val repository = repository(servers = servers)
    repository.stopsIn(wide, rail)
    servers.save(ServerConfig(baseUrl = "https://autre.exemple.org", label = "Autre"))
    repository.stopsIn(inside, rail)
    assertEquals(2, requests)
  }
}
