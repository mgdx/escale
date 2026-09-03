package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.ORIGIN
import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.transit
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * La vérification d'un trajet surveillé (SPEC.md § 5.5.1) : ce qui part sur le réseau, ce qui n'en
 * part pas, et ce qui se tait.
 */
class JourneyWatchCheckTest {

  /** L'exécution a lieu une heure avant le départ surveillé. */
  private val now: Instant = ORIGIN.minus(WatchPlanning.LEAD_TIME)

  private val favorite = FavoriteJourney(
    id = 1,
    label = null,
    from = location("Gare de Lyon", "stop-lyon"),
    to = location("Nation", "stop-nation"),
    category = JourneyCategory.TRANSIT,
    createdAt = ORIGIN.minus(Duration.ofDays(30)),
  )

  private val watch = WatchedJourney(
    journeyId = 1,
    schedule = WatchSchedule(departureTime = LocalTime.of(8, 10), date = null, days = emptySet()),
    itineraryId = "itin-1",
    itineraryCapturedAt = ORIGIN.minus(Duration.ofDays(1)),
    lastViewedAt = null,
    createdAt = ORIGIN.minus(Duration.ofDays(30)),
  )

  @Test
  fun `un trajet consulte il y a dix minutes n'emet aucune requete`() = runTest {
    val plans = FakePlanRepository()
    val watched = FakeWatchedJourneysRepository()
    val recent = watch.copy(lastViewedAt = now.minus(Duration.ofMinutes(10)))

    val result = JourneyWatchCheck(plans, watched).run(recent, favorite, ORIGIN, now)

    assertEquals(WatchCheck.Skipped, result)
    assertEquals(0, plans.refreshCalls)
    assertEquals(0, plans.planCalls)
  }

  @Test
  fun `le cas normal est une requete unique, sans repli`() = runTest {
    val trip = journey(id = "itin-1", legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true)))
    val plans = FakePlanRepository(refresh = Outcome.Success(trip))
    val watched = FakeWatchedJourneysRepository()

    val result = JourneyWatchCheck(plans, watched).run(watch, favorite, ORIGIN, now)

    assertEquals(WatchCheck.NothingToReport, result)
    assertEquals(1, plans.refreshCalls)
    assertEquals(0, plans.planCalls)
    // L'identifiant n'a pas changé : rien n'est réécrit.
    assertFalse(watched.itineraryRemembered)
  }

  @Test
  fun `un refus en 400 declenche le repli sur la requete plan d'origine`() = runTest {
    val replacement = journey(id = "itin-2", legs = listOf(transit(afterMinutes = 12, minutes = 20)))
    val plans = FakePlanRepository(
      refresh = Outcome.Failure(EscaleError.BadRequest(serverMessage = null)),
      plan = Outcome.Success(JourneyPage(journeys = listOf(replacement, far()))),
    )
    val watched = FakeWatchedJourneysRepository()

    val result = JourneyWatchCheck(plans, watched).run(watch, favorite, ORIGIN, now)

    assertEquals(1, plans.planCalls)
    val notify = result as WatchCheck.Notify
    // Le trajet le plus proche en heure de départ, et pas le premier venu.
    assertEquals("itin-2", notify.journey?.id)
    assertEquals(WatchIssue.DELAYED, notify.notice.issue)
    // Le nouvel identifiant remplace celui que le serveur a refusé.
    assertEquals("itin-2", watched.lastItineraryId)
  }

  @Test
  fun `un 404 sur refresh-itinerary declenche aussi le repli`() = runTest {
    val replacement = journey(id = "itin-2", legs = listOf(transit(afterMinutes = 0, minutes = 20)))
    val plans = FakePlanRepository(
      refresh = Outcome.Failure(EscaleError.ApiVersionTooOld(endpoint = "/api/v6/refresh-itinerary")),
      plan = Outcome.Success(JourneyPage(journeys = listOf(replacement))),
    )

    val result = JourneyWatchCheck(plans, FakeWatchedJourneysRepository()).run(watch, favorite, ORIGIN, now)

    assertEquals(1, plans.planCalls)
    assertEquals(WatchCheck.NothingToReport, result)
  }

  @Test
  fun `la requete rejouee est bien celle du favori, a l'heure surveillee`() = runTest {
    val plans = FakePlanRepository(
      refresh = Outcome.Failure(EscaleError.BadRequest(serverMessage = null)),
      plan = Outcome.Success(JourneyPage(journeys = emptyList())),
    )

    JourneyWatchCheck(plans, FakeWatchedJourneysRepository()).run(watch, favorite, ORIGIN, now)

    val query = requireNotNull(plans.lastQuery)
    assertEquals("stop-lyon", query.from.id)
    assertEquals("stop-nation", query.to.id)
    assertEquals(JourneyCategory.TRANSIT, query.category)
    assertEquals(TimeChoice.DepartAt(ORIGIN), query.time)
    // Une réponse légère suffit à décider (SPEC.md § 7.6), et le cache mémoire est contourné :
    // la vérification a pour objet le temps réel du moment.
    assertEquals(false, plans.lastDetailedLegs)
    assertTrue(plans.lastFresh)
  }

  @Test
  fun `une panne passagere n'entraine pas de seconde requete`() = runTest {
    val plans = FakePlanRepository(refresh = Outcome.Failure(EscaleError.Timeout))

    val result = JourneyWatchCheck(plans, FakeWatchedJourneysRepository()).run(watch, favorite, ORIGIN, now)

    assertEquals(WatchCheck.Failed, result)
    assertEquals(0, plans.planCalls)
  }

  @Test
  fun `un echec du repli ne dit rien`() = runTest {
    val plans = FakePlanRepository(
      refresh = Outcome.Failure(EscaleError.BadRequest(serverMessage = null)),
      plan = Outcome.Failure(EscaleError.NoNetwork),
    )

    val result = JourneyWatchCheck(plans, FakeWatchedJourneysRepository()).run(watch, favorite, ORIGIN, now)

    assertEquals(WatchCheck.Failed, result)
  }

  @Test
  fun `un serveur qui ne propose plus rien annonce un trajet devenu impossible`() = runTest {
    val plans = FakePlanRepository(
      refresh = Outcome.Failure(EscaleError.BadRequest(serverMessage = null)),
      plan = Outcome.Success(JourneyPage(journeys = emptyList())),
    )
    val watched = FakeWatchedJourneysRepository()

    val result = JourneyWatchCheck(plans, watched).run(watch, favorite, ORIGIN, now)

    assertEquals(WatchIssue.IMPOSSIBLE, (result as WatchCheck.Notify).notice.issue)
    assertNull(result.journey)
    // L'identifiant devenu invalide est oublié plutôt que resservi à la prochaine occurrence.
    assertTrue(watched.itineraryRemembered)
    assertNull(watched.lastItineraryId)
  }

  @Test
  fun `sans identifiant d'itineraire, la requete plan part directement`() = runTest {
    val trip = journey(id = "itin-9", legs = listOf(transit(afterMinutes = 0, minutes = 20)))
    val plans = FakePlanRepository(plan = Outcome.Success(JourneyPage(journeys = listOf(trip))))
    val watched = FakeWatchedJourneysRepository()

    val result = JourneyWatchCheck(plans, watched).run(watch.copy(itineraryId = null), favorite, ORIGIN, now)

    assertEquals(0, plans.refreshCalls)
    assertEquals(1, plans.planCalls)
    assertEquals(WatchCheck.NothingToReport, result)
    assertEquals("itin-9", watched.lastItineraryId)
  }

  private fun far() = journey(id = "itin-loin", legs = listOf(transit(afterMinutes = 90, minutes = 20)))

  private fun location(name: String, id: String) = Location(
    id = id,
    name = name,
    description = null,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    kind = PlaceKind.STOP,
  )
}

/** Un dépôt d'itinéraires qui ne touche à rien : il rend ce qu'on lui a donné et compte les appels. */
private class FakePlanRepository(
  private val refresh: Outcome<Journey>? = null,
  private val plan: Outcome<JourneyPage>? = null,
) : PlanRepository {

  var refreshCalls = 0
  var planCalls = 0
  var lastQuery: SearchQuery? = null
  var lastDetailedLegs: Boolean? = null
  var lastFresh: Boolean = false

  override suspend fun plan(
    query: SearchQuery,
    cursor: String?,
    detailedLegs: Boolean,
    fresh: Boolean,
  ): Outcome<JourneyPage> {
    planCalls++
    lastQuery = query
    lastDetailedLegs = detailedLegs
    lastFresh = fresh
    return plan ?: Outcome.Failure(EscaleError.Unknown(cause = "NoStub"))
  }

  override suspend fun refresh(itineraryId: String, detailedLegs: Boolean): Outcome<Journey> {
    refreshCalls++
    return refresh ?: Outcome.Failure(EscaleError.Unknown(cause = "NoStub"))
  }

  override suspend fun clearCache(): Outcome<Unit> = Outcome.Success(Unit)
}

/** Le stockage des surveillances, réduit à ce que la vérification en attend. */
private class FakeWatchedJourneysRepository : WatchedJourneysRepository {

  var itineraryRemembered = false
  var lastItineraryId: String? = null

  override val watched: Flow<List<WatchedJourney>> = flowOf(emptyList())

  override suspend fun watch(journeyId: Long, schedule: WatchSchedule): Outcome<WatchDecision> =
    Outcome.Success(WatchDecision.ACCEPTED)

  override suspend fun unwatch(journeyId: Long): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun rememberItinerary(journeyId: Long, itineraryId: String?, capturedAt: Instant): Outcome<Unit> {
    itineraryRemembered = true
    lastItineraryId = itineraryId
    return Outcome.Success(Unit)
  }

  override suspend fun markViewed(journeyId: Long, viewedAt: Instant): Outcome<Unit> = Outcome.Success(Unit)
}
