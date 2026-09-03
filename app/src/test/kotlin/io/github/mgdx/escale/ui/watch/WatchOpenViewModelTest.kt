package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.detail.RecordingPlanRepository
import io.github.mgdx.escale.ui.map.FakePreferencesRepository
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * L'ouverture du détail depuis la notification (SPEC.md § 5.5.1).
 *
 * Ce qui est vérifié ici : le trajet **rafraîchi** est redemandé au serveur, publié pour l'écran de
 * détail, et la navigation n'est déclenchée qu'une fois — jamais avant d'avoir un trajet à montrer,
 * jamais deux fois pour un même appui.
 */
class WatchOpenViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val zone: ZoneId = ZoneId.of("Europe/Paris")

  /** Lundi 7 septembre 2026, 7 h 10 à Paris : une heure avant le départ surveillé de 8 h 10. */
  private val now: Instant = Instant.parse("2026-09-07T05:10:00Z")
  private val departure: Instant = Instant.parse("2026-09-07T06:10:00Z")

  private val favorite = FavoriteJourney(
    id = 3,
    label = null,
    from = location("Gare de Lyon", "stop-lyon"),
    to = location("Nation", "stop-nation"),
    category = JourneyCategory.TRANSIT,
    createdAt = now,
  )

  private val watch = WatchedJourney(
    journeyId = 3,
    schedule = WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.MONDAY)),
    itineraryId = "itin-1",
    createdAt = now,
  )

  private val watched = FakeWatchedJourneysRepository(listOf(watch))
  private val favorites = FakeFavoritesRepository(listOf(favorite))
  private val plans = RecordingPlanRepository()
  private val requests = WatchOpenRequests()
  private val selection = SelectedJourneyStore()

  @Test
  fun `l'appui redemande le trajet rafraichi et le publie avant de naviguer`() {
    plans.refreshAnswer = Outcome.Success(refreshed())
    val model = viewModel()

    requests.open(journeyId = 3)

    // Le trajet vient de `refresh-itinerary`, avec l'identifiant enregistré du jalon 6A.
    assertEquals("itin-1" to false, plans.refreshCalls.single())
    // Il est publié pour l'écran de détail **avant** que la navigation soit demandée : ouvrir
    // l'écran sans trajet le referait se fermer aussitôt.
    assertEquals("rafraichi", selection.selected.value?.id)
    assertNotNull(model.uiState.value.ready)
  }

  @Test
  fun `l'ouverture date la consultation, pour la regle des trente minutes`() {
    plans.refreshAnswer = Outcome.Success(refreshed())
    viewModel()

    requests.open(journeyId = 3)

    assertEquals(now, watched.viewedAt)
  }

  @Test
  fun `la demande n'est servie qu'une fois`() {
    plans.refreshAnswer = Outcome.Success(refreshed())
    val model = viewModel()
    requests.open(journeyId = 3)

    model.onOpened()

    // Plus rien à ouvrir : une rotation de l'écran ne rouvrira pas le trajet.
    assertNull(model.uiState.value.ready)
    assertNull(requests.request.value)
  }

  @Test
  fun `un identifiant refuse par le serveur passe par le repli sur plan`() {
    plans.refreshAnswer = Outcome.Failure(EscaleError.BadRequest(serverMessage = null))
    plans.planAnswer = Outcome.Success(JourneyPage(journeys = listOf(refreshed())))
    val model = viewModel()

    requests.open(journeyId = 3)

    assertEquals(1, plans.planCalls.size)
    assertEquals("rafraichi", selection.selected.value?.id)
    assertNotNull(model.uiState.value.ready)
  }

  @Test
  fun `un echec se dit, et ne navigue pas`() {
    plans.refreshAnswer = Outcome.Failure(EscaleError.NoNetwork)
    val model = viewModel()

    requests.open(journeyId = 3)

    assertEquals(EscaleError.NoNetwork, model.uiState.value.error)
    assertNull(model.uiState.value.ready)
    assertNull(selection.selected.value)
  }

  @Test
  fun `reessayer rejoue la meme demande`() {
    plans.refreshAnswer = Outcome.Failure(EscaleError.Timeout)
    val model = viewModel()
    requests.open(journeyId = 3)
    plans.refreshAnswer = Outcome.Success(refreshed())

    model.onRetry()

    assertNull(model.uiState.value.error)
    assertNotNull(model.uiState.value.ready)
  }

  @Test
  fun `fermer le message d'echec abandonne l'ouverture`() {
    plans.refreshAnswer = Outcome.Failure(EscaleError.Timeout)
    val model = viewModel()
    requests.open(journeyId = 3)

    model.onDismissError()

    assertNull(model.uiState.value.error)
    assertNull(requests.request.value)
  }

  @Test
  fun `un trajet dont la surveillance a disparu n'ouvre rien et ne dit rien`() {
    val model = viewModel(watched = FakeWatchedJourneysRepository())

    requests.open(journeyId = 3)

    assertNull(model.uiState.value.ready)
    assertNull(model.uiState.value.error)
    assertTrue(plans.refreshCalls.isEmpty())
    assertNull(requests.request.value)
  }

  @Test
  fun `deux notifications successives ouvrent chacune son trajet`() {
    val second = favorite.copy(id = 9)
    val secondWatch = watch.copy(journeyId = 9, itineraryId = "itin-9")
    val model = viewModel(
      watched = FakeWatchedJourneysRepository(listOf(watch, secondWatch)),
      favorites = FakeFavoritesRepository(listOf(favorite, second)),
    )
    plans.refreshAnswer = Outcome.Success(refreshed())

    requests.open(journeyId = 3)
    val first = model.uiState.value.ready
    model.onOpened()
    requests.open(journeyId = 9)

    assertEquals(listOf("itin-1" to false, "itin-9" to false), plans.refreshCalls)
    // Deux jetons distincts : la seconde ouverture n'est pas confondue avec la première.
    assertTrue(model.uiState.value.ready != first)
  }

  private fun viewModel(
    watched: FakeWatchedJourneysRepository = this.watched,
    favorites: FakeFavoritesRepository = this.favorites,
  ) = WatchOpenViewModel(
    watched = watched,
    favorites = favorites,
    plans = plans,
    preferences = FakePreferencesRepository(),
    requests = requests,
    selection = selection,
    zone = zone,
    now = { now },
  )

  private fun refreshed(): Journey {
    val leg = JourneyLeg.Transit(
      startTime = departure.plus(Duration.ofMinutes(8)),
      endTime = departure.plus(Duration.ofMinutes(28)),
      scheduledStartTime = departure,
      scheduledEndTime = departure.plus(Duration.ofMinutes(20)),
      duration = Duration.ofMinutes(20),
      from = place("Gare de Lyon", departure),
      to = place("Nation", departure.plus(Duration.ofMinutes(20))),
      realTime = true,
      lineName = "1",
    )
    return Journey(
      id = "rafraichi",
      startTime = leg.startTime,
      endTime = leg.endTime,
      scheduledStartTime = leg.scheduledStartTime,
      scheduledEndTime = leg.scheduledEndTime,
      duration = leg.duration,
      transfers = 0,
      legs = listOf(leg),
    )
  }

  private fun place(name: String, at: Instant) = Place(
    name = name,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    stopId = null,
    track = null,
    scheduledTime = at,
    time = at,
  )

  private fun location(name: String, id: String) = Location(
    id = id,
    name = name,
    description = null,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    kind = PlaceKind.STOP,
  )
}
