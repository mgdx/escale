package io.github.mgdx.escale.data

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.MotisClient
import io.github.mgdx.escale.data.repository.FakeServerRepository
import io.github.mgdx.escale.data.repository.PlanCache
import io.github.mgdx.escale.data.repository.PlanRepositoryImpl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope

/**
 * Outillage commun des tests de recherche d'itinéraire.
 *
 * Aucun test ne touche le réseau réel (docs/architecture.md § 10) : tout passe par le `MockEngine`
 * de Ktor et par des réponses réelles capturées depuis `api.transitous.org`, rangées dans
 * `src/test/resources/fixtures/`.
 */
internal object PlanTestSupport {

  const val BASE_URL = "https://exemple.org"

  val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  fun fixture(name: String): String = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
    "fixture manquante : $name"
  }.readBytes().decodeToString()

  /** Un moteur qui répond toujours la même fixture, quelle que soit la requête. */
  fun engineServing(name: String): MockEngine = MockEngine { respond(fixture(name), HttpStatusCode.OK, jsonHeaders) }

  fun repository(engine: MockEngine, scope: CoroutineScope, cache: PlanCache = PlanCache()) = PlanRepositoryImpl(
    client = MotisClient(versionName = "1.0.0", engine = engine),
    servers = FakeServerRepository(BASE_URL),
    cache = cache,
    scope = scope,
  )

  fun query(category: JourneyCategory = JourneyCategory.TRANSIT) = SearchQuery(
    from = Location(
      id = null,
      name = "Place de la Gare",
      description = null,
      coordinates = LatLon(lat = 49.4458, lon = 11.0821),
      kind = PlaceKind.ADDRESS,
    ),
    to = Location(
      id = null,
      name = "Jardin zoologique",
      description = null,
      coordinates = LatLon(lat = 49.4478, lon = 11.1497),
      kind = PlaceKind.PLACE,
    ),
    time = TimeChoice.Now,
    category = category,
  )

  /** La page rendue par une fixture, telle que l'interface la recevra. */
  suspend fun page(
    name: String,
    scope: CoroutineScope,
    category: JourneyCategory = JourneyCategory.TRANSIT,
  ): JourneyPage {
    val outcome = repository(engineServing(name), scope).plan(query(category))
    return (outcome as Outcome.Success).value
  }
}
