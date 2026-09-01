package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.query.RentalFormFactorQuery
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.PlanTestSupport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Le dépôt de recherche : requête émise, cache mémoire, erreurs de SPEC.md § 8.
 *
 * Aucun test ne touche le réseau réel (docs/architecture.md § 10).
 */
class PlanRepositoryImplTest {

  private lateinit var seen: MutableList<Url>

  /** Un moteur qui note chaque URL appelée avant de répondre la fixture demandée. */
  private fun recordingEngine(fixture: String = "plan_transit_transfer.json"): MockEngine {
    seen = mutableListOf()
    return MockEngine { request ->
      seen += request.url
      respond(PlanTestSupport.fixture(fixture), HttpStatusCode.OK, PlanTestSupport.jsonHeaders)
    }
  }

  private fun parameter(name: String): String? = seen.last().parameters[name]

  // --- La requête effectivement émise --------------------------------------------------------

  @Test
  fun `la requete part sur le point d entree v6 du serveur courant`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query())
    val url = seen.single()
    assertEquals("exemple.org", url.host)
    assertEquals("/api/v6/plan", url.encodedPath)
  }

  @Test
  fun `chaque onglet emet ses propres parametres`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)

    repository.plan(PlanTestSupport.query(JourneyCategory.TRANSIT))
    assertEquals("TRANSIT", parameter("transitModes"))
    assertEquals("", parameter("directModes"))
    assertEquals("WALK,RENTAL", parameter("preTransitModes"))
    assertEquals("false", parameter("detailedLegs"))

    repository.plan(PlanTestSupport.query(JourneyCategory.CAR))
    assertEquals("CAR", parameter("directModes"))
    assertEquals("", parameter("transitModes"))
    assertEquals("14400", parameter("maxDirectTime"))

    repository.plan(PlanTestSupport.query(JourneyCategory.BIKE))
    assertEquals("BIKE,RENTAL", parameter("directModes"))
    assertEquals("10800", parameter("maxDirectTime"))
    assertEquals(
      "BICYCLE,SCOOTER_STANDING,SCOOTER_SEATED",
      parameter(RentalFormFactorQuery.DIRECT),
    )

    repository.plan(PlanTestSupport.query(JourneyCategory.WALK))
    assertEquals("WALK", parameter("directModes"))
    assertEquals("7200", parameter("maxDirectTime"))
  }

  @Test
  fun `la pagination ne change que le curseur`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    val query = PlanTestSupport.query()
    repository.plan(query)
    repository.plan(query, cursor = "LATER|1756785600")
    assertNull(seen.first().parameters["pageCursor"])
    assertEquals("LATER|1756785600", parameter("pageCursor"))
    // Tout le reste est renvoyé tel quel (SPEC.md § 5.2).
    assertEquals(seen.first().parameters["fromPlace"], seen.last().parameters["fromPlace"])
    assertEquals(seen.first().parameters["transitModes"], seen.last().parameters["transitModes"])
  }

  @Test
  fun `l ouverture d un trajet demande les portions detaillees`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query(), detailedLegs = true)
    assertEquals("true", parameter("detailedLegs"))
    assertEquals("true", parameter("detailedTransfers"))
  }

  @Test
  fun `un rafraichissement n envoie que l identifiant de l itineraire`() = runTest {
    // Le point d'entrée rend un `Itinerary` seul, et non une page : la fixture est l'itinéraire
    // avec correspondance extrait de la réponse `plan` réelle.
    seen = mutableListOf()
    val engine = MockEngine { request ->
      seen += request.url
      respond(PlanTestSupport.fixture("refresh_itinerary.json"), HttpStatusCode.OK, PlanTestSupport.jsonHeaders)
    }
    val repository = PlanTestSupport.repository(engine, backgroundScope)
    val outcome = repository.refresh("opaque-id")
    assertTrue(outcome is Outcome.Success)
    assertEquals("/api/v6/refresh-itinerary", seen.single().encodedPath)
    assertEquals("opaque-id", parameter("itineraryId"))
    assertEquals("true", parameter("detailedLegs"))
  }

  // --- Cache mémoire, SPEC.md § 7.5 -----------------------------------------------------------

  @Test
  fun `une recherche deja faite ne repart pas sur le reseau`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    val query = PlanTestSupport.query()
    repository.plan(query)
    repository.plan(query)
    assertEquals(1, seen.size)
  }

  @Test
  fun `chaque onglet a sa propre entree de cache`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query(JourneyCategory.TRANSIT))
    repository.plan(PlanTestSupport.query(JourneyCategory.WALK))
    repository.plan(PlanTestSupport.query(JourneyCategory.TRANSIT))
    assertEquals(2, seen.size)
  }

  @Test
  fun `la liste et le detail ne partagent pas la meme entree de cache`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query())
    repository.plan(PlanTestSupport.query(), detailedLegs = true)
    assertEquals(2, seen.size)
  }

  @Test
  fun `vider le cache fait repartir la requete`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query())
    // C'est ce que déclenchera l'écran « Serveur MOTIS » au changement de serveur (SPEC.md § 4.1),
    // par l'interface de dépôt et non par la classe de cache : même geste que clearGeocodeCache.
    assertTrue(repository.clearCache() is Outcome.Success)
    repository.plan(PlanTestSupport.query())
    assertEquals(2, seen.size)
  }

  @Test
  fun `le cache partage par plusieurs onglets est vide d un seul coup`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine(), backgroundScope)
    repository.plan(PlanTestSupport.query(JourneyCategory.TRANSIT))
    repository.plan(PlanTestSupport.query(JourneyCategory.WALK))
    repository.clearCache()
    repository.plan(PlanTestSupport.query(JourneyCategory.TRANSIT))
    repository.plan(PlanTestSupport.query(JourneyCategory.WALK))
    assertEquals(4, seen.size)
  }

  // --- Une seule requête en vol par onglet, SPEC.md § 7.2 --------------------------------------

  // `runCurrent` est encore expérimental côté kotlinx-coroutines ; c'est pourtant le seul moyen de
  // faire démarrer une requête avant d'en lancer une seconde, donc d'éprouver la règle du § 7.2
  // sans dépendre d'un ordonnancement de threads.
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `deux appels identiques se partagent la meme requete`() = runTest {
    val calls = AtomicInteger()
    val release = CompletableDeferred<Unit>()
    val engine = MockEngine {
      calls.incrementAndGet()
      release.await()
      respond(PlanTestSupport.fixture("plan_transit_transfer.json"), HttpStatusCode.OK, PlanTestSupport.jsonHeaders)
    }
    val repository = PlanTestSupport.repository(engine, backgroundScope)

    val first = async { repository.plan(PlanTestSupport.query()) }
    runCurrent()
    val second = async { repository.plan(PlanTestSupport.query()) }
    runCurrent()
    release.complete(Unit)

    assertTrue(first.await() is Outcome.Success)
    assertTrue(second.await() is Outcome.Success)
    assertEquals(1, calls.get())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `une nouvelle recherche annule celle qui court sur le meme onglet`() = runTest {
    val release = CompletableDeferred<Unit>()
    val engine = MockEngine {
      release.await()
      respond(PlanTestSupport.fixture("plan_transit_transfer.json"), HttpStatusCode.OK, PlanTestSupport.jsonHeaders)
    }
    val repository = PlanTestSupport.repository(engine, backgroundScope)

    val abandoned = async { repository.plan(PlanTestSupport.query()) }
    runCurrent()
    // Même onglet, autre heure : c'est une recherche différente, elle remplace la précédente.
    val wanted = async {
      repository.plan(PlanTestSupport.query().copy(time = TimeChoice.DepartAt(Instant.parse("2026-09-02T06:00:00Z"))))
    }
    runCurrent()
    release.complete(Unit)

    // La recherche supplantée ne rend pas un résultat périmé : l'écran affiche celui de la
    // suivante. Le cas a sa propre valeur d'erreur, que l'interface ignore silencieusement — la
    // rendre en `Unknown` ferait afficher « une erreur est survenue » à qui vient de relancer sa
    // recherche.
    assertEquals(EscaleError.Superseded, (abandoned.await() as Outcome.Failure).error)
    assertTrue(wanted.await() is Outcome.Success)
  }

  // --- Erreurs, SPEC.md § 8 --------------------------------------------------------------------

  @Test
  fun `un 404 sur le point d entree v6 annonce un serveur trop ancien`() = runTest {
    // SPEC.md § 4.3 : l'application ne plante pas, elle explique.
    val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
    val repository = PlanTestSupport.repository(engine, backgroundScope)
    val outcome = repository.plan(PlanTestSupport.query())
    assertEquals(
      EscaleError.ApiVersionTooOld("/api/v6/plan"),
      (outcome as Outcome.Failure).error,
    )
  }

  @Test
  fun `un 400 remonte le champ error de la reponse`() = runTest {
    val engine = MockEngine {
      respond(
        PlanTestSupport.fixture("error_bad_request.json"),
        HttpStatusCode.BadRequest,
        PlanTestSupport.jsonHeaders,
      )
    }
    val outcome = PlanTestSupport.repository(engine, backgroundScope).plan(PlanTestSupport.query())
    assertEquals(EscaleError.BadRequest("invalid time format"), (outcome as Outcome.Failure).error)
  }

  @Test
  fun `un 5xx se distingue d une absence de resultat`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
    val outcome = PlanTestSupport.repository(engine, backgroundScope).plan(PlanTestSupport.query())
    assertEquals(EscaleError.ServerUnreachable(500), (outcome as Outcome.Failure).error)
  }

  @Test
  fun `une reponse sans resultat est un succes, pas une erreur`() = runTest {
    val repository = PlanTestSupport.repository(recordingEngine("plan_empty.json"), backgroundScope)
    val page = (repository.plan(PlanTestSupport.query()) as Outcome.Success).value
    assertTrue(page.isEmpty)
  }
}
