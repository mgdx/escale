package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.geo.RentalsCache
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.RentalsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Le cache de **soixante secondes** du dépôt de libre-service (SPEC.md § 5.7, règle 4), compté en
 * requêtes réellement parties : c'est la seule mesure qui prouve qu'une disponibilité fraîche n'est
 * pas redemandée, et surtout qu'une disponibilité périmée l'est.
 *
 * La règle elle-même est vérifiée dans `:core` (`RentalsCacheTest`) ; ce qui s'éprouve ici, c'est
 * son branchement : l'horloge du relevé, celle du cache et le compteur de requêtes doivent parler
 * de la même minute.
 */
class RentalsRepositoryImplTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private val berlin = LatLon(52.52369, 13.37076)
  private val elsewhere = LatLon(48.8534, 2.335)

  private val wide = BoundingBox(min = LatLon(52.51, 13.36), max = LatLon(52.53, 13.39))
  private val inside = BoundingBox(min = LatLon(52.520, 13.368), max = LatLon(52.526, 13.374))

  private val start = Instant.parse("2026-09-02T05:06:25Z")
  private var now = start
  private var requests = 0

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  private fun repository(servers: FakeServerRepository = FakeServerRepository()) = RentalsRepositoryImpl(
    api = RentalsApi(
      versionName = "1.0.0",
      engine = MockEngine {
        requests += 1
        respond(fixture("rentals_berlin_hauptbahnhof.json"), HttpStatusCode.OK, jsonHeaders)
      },
    ),
    serverRepository = servers,
    cache = RentalsCache(now = { now }),
    now = { now },
  )

  @Test
  fun `une disponibilite de moins d'une minute n'est pas redemandee`() = runTest {
    val repository = repository()
    repository.availabilityNear(berlin)
    now = start.plusSeconds(59)
    repository.availabilityNear(berlin)

    assertEquals(1, requests)
  }

  @Test
  fun `passe la minute, elle est redemandee`() = runTest {
    val repository = repository()
    repository.availabilityNear(berlin)
    now = start.plusSeconds(61)
    repository.availabilityNear(berlin)

    assertEquals("une disponibilité périmée est une information fausse, pas une information vieille", 2, requests)
  }

  @Test
  fun `l'heure de releve est celle de la reponse, et elle avance avec la requete`() = runTest {
    val repository = repository()
    val first = repository.availabilityNear(berlin)
    assertEquals(start, (first as Outcome.Success).value.first().retrievedAt)

    now = start.plusSeconds(120)
    val second = repository.availabilityNear(berlin)
    assertEquals(now, (second as Outcome.Success).value.first().retrievedAt)
  }

  @Test
  fun `une reponse rendue par le cache garde l'heure a laquelle elle a ete obtenue`() = runTest {
    // C'est tout l'enjeu de la mention à l'écran : redater un relevé qu'on n'a pas refait ferait
    // passer pour fraîche une donnée qui ne l'est pas (SPEC.md § 5.3).
    val repository = repository()
    repository.availabilityNear(berlin)
    now = start.plusSeconds(30)
    val cached = repository.availabilityNear(berlin)

    assertEquals(start, (cached as Outcome.Success).value.first().retrievedAt)
    assertEquals(1, requests)
  }

  @Test
  fun `un releve demande par l'usager contourne le cache`() = runTest {
    // Le cache d'une minute retient les requêtes automatiques, pas une action délibérée : un bouton
    // qui ne fait rien pendant une minute, sans le dire, fait croire qu'on a redemandé (§ 7.4).
    val repository = repository()
    repository.availabilityNear(berlin)
    now = start.plusSeconds(5)
    repository.availabilityNear(berlin, fresh = true)

    assertEquals(2, requests)
  }

  @Test
  fun `le releve ainsi obtenu remplace celui du cache, heure comprise`() = runTest {
    val repository = repository()
    repository.availabilityNear(berlin)
    now = start.plusSeconds(5)
    repository.availabilityNear(berlin, fresh = true)

    // C'est une nouvelle réponse : elle doit être datée comme telle, et servir les lectures
    // suivantes jusqu'à sa propre expiration.
    now = start.plusSeconds(6)
    val cached = repository.availabilityNear(berlin)
    assertEquals(start.plusSeconds(5), (cached as Outcome.Success).value.first().retrievedAt)
    assertEquals(2, requests)
  }

  @Test
  fun `la carte, elle, ne contourne jamais le cache`() = runTest {
    // Ses requêtes viennent de la caméra, jamais d'un geste : ce sont exactement celles que le
    // cache est là pour retenir.
    val repository = repository()
    repository.stationsIn(wide)
    now = start.plusSeconds(30)
    repository.stationsIn(wide)

    assertEquals(1, requests)
  }

  @Test
  fun `une autre station est une autre demande`() = runTest {
    val repository = repository()
    repository.availabilityNear(berlin)
    repository.availabilityNear(elsewhere)

    assertEquals(2, requests)
  }

  @Test
  fun `un rayon different est une autre demande`() = runTest {
    val repository = repository()
    repository.availabilityNear(berlin, radiusMeters = 50)
    repository.availabilityNear(berlin, radiusMeters = 200)

    assertEquals(2, requests)
  }

  @Test
  fun `une emprise deja couverte n'est pas redemandee`() = runTest {
    val repository = repository()
    repository.stationsIn(wide)
    repository.stationsIn(inside)

    assertEquals(1, requests)
  }

  @Test
  fun `un changement de serveur vide le cache`() = runTest {
    val servers = FakeServerRepository()
    val repository = repository(servers)
    repository.availabilityNear(berlin)
    servers.save(ServerConfig(baseUrl = "https://autre.example", label = "Autre"))
    repository.availabilityNear(berlin)

    assertEquals("les exploitants d'un serveur ne sont pas ceux d'un autre", 2, requests)
  }
}
