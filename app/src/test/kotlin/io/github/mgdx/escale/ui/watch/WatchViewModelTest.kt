package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * L'activation d'une surveillance depuis l'écran de détail (SPEC.md § 5.5.1).
 *
 * Les tests portent sur l'enchaînement — ce qui est enregistré, ce qui est programmé, ce qui est
 * refusé — et non sur les règles elles-mêmes, qui sont couvertes dans `:core`.
 */
class WatchViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val zone: ZoneId = ZoneId.of("Europe/Paris")
  private val now: Instant = Instant.parse("2026-09-07T05:00:00Z")
  private val departure: Instant = Instant.parse("2026-09-07T06:10:00Z")

  private val lyon = location("Gare de Lyon", "stop-lyon")
  private val nation = location("Nation", "stop-nation")

  private val favorite = FavoriteJourney(
    id = 3,
    label = null,
    from = lyon,
    to = nation,
    category = JourneyCategory.TRANSIT,
    createdAt = now,
  )

  private val favorites = FakeFavoritesRepository(listOf(favorite))
  private val watched = FakeWatchedJourneysRepository()
  private val store = FakeWatchSettingsStore()
  private val alarms = FakeWatchAlarms()
  private val notifier = FakeWatchNotifier()
  private val selection = SelectedJourneyStore()
  private val session = SearchSession()

  @Test
  fun `un trajet qui n'est pas en favori n'affiche rien`() {
    val other = SearchSession().apply {
      setFrom(location("Ailleurs", "stop-ailleurs"))
      setTo(nation)
    }

    val state = viewModel(session = other).uiState.value

    assertNull(state.favoriteId)
  }

  @Test
  fun `le favori affiche propose l'heure de depart du trajet`() {
    val state = viewModel().uiState.value

    assertEquals(3L, state.favoriteId)
    assertFalse(state.watched)
    // 06:10 UTC, soit 08:10 à Paris : l'heure proposée est celle du trajet regardé.
    assertEquals(LocalTime.of(8, 10), state.time)
  }

  @Test
  fun `activer enregistre la surveillance, retient l'itineraire et programme une echeance`() {
    val model = viewModel()

    model.onWatchedChanged(true)

    assertTrue(model.uiState.value.watched)
    assertEquals("itin-1", watched.lastItineraryId)
    assertEquals(1, alarms.scheduled.size)
    assertEquals(3L, alarms.scheduled.single().journeyId)
  }

  @Test
  fun `sans jour coche, la surveillance vaut pour une date unique`() {
    val model = viewModel()

    model.onWatchedChanged(true)

    val schedule = alarms.scheduled.single().schedule
    assertFalse(schedule.isRecurring)
    // 08:10 est encore à venir ce jour-là : la date unique est celle du jour.
    assertEquals(LocalTime.of(8, 10), schedule.departureTime)
    assertEquals(now.atZone(zone).toLocalDate(), schedule.date)
  }

  @Test
  fun `cocher des jours rend la surveillance recurrente et reprogramme`() {
    val model = viewModel()
    model.onWatchedChanged(true)

    model.onDayToggled(DayOfWeek.MONDAY)
    model.onDayToggled(DayOfWeek.TUESDAY)

    val schedule = alarms.scheduled.last().schedule
    assertTrue(schedule.isRecurring)
    assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), schedule.days)
    // Une reprogrammation par changement, jamais une échéance qui s'ajoute à la précédente.
    assertEquals(3, alarms.scheduled.size)
  }

  @Test
  fun `arreter la surveillance annule la tache, la notification et le dernier resultat`() {
    val model = viewModel()
    model.onWatchedChanged(true)

    model.onWatchedChanged(false)

    assertFalse(model.uiState.value.watched)
    assertEquals(listOf(3L), alarms.cancelled)
    assertEquals(listOf(3L), notifier.cancelled)
    assertNull(model.uiState.value.lastCheck)
  }

  @Test
  fun `la sixieme surveillance est refusee et le dit`() {
    val others = (100L..104L).map {
      WatchedJourney(
        journeyId = it,
        schedule = WatchSchedule(LocalTime.of(9, 0), days = setOf(DayOfWeek.MONDAY)),
        createdAt = now,
      )
    }
    val full = FakeWatchedJourneysRepository(others)
    val model = viewModel(watched = full)

    model.onWatchedChanged(true)

    assertFalse(model.uiState.value.watched)
    assertTrue(model.uiState.value.limitReached)
    assertTrue(model.uiState.value.limitBlocking)
    assertTrue(alarms.scheduled.isEmpty())
  }

  @Test
  fun `le seuil de retard et le rappel systematique sont enregistres`() {
    val model = viewModel()

    model.onThresholdChanged(Duration.ofMinutes(10))
    model.onNotifyAlwaysChanged(true)

    assertEquals(Duration.ofMinutes(10), model.uiState.value.settings.delayThreshold)
    assertTrue(model.uiState.value.settings.notifyWhenNothingChanged)
  }

  @Test
  fun `consulter le trajet date la consultation, pour la regle des trente minutes`() {
    val model = viewModel()

    model.onJourneyViewed()

    assertEquals(now, watched.viewedAt)
  }

  @Test
  fun `un refus de notification n'empeche pas la surveillance`() {
    notifier.allowed = false
    val model = viewModel()

    model.onWatchedChanged(true)

    assertTrue(model.uiState.value.watched)
    assertFalse(model.uiState.value.notificationsAllowed)
    assertEquals(1, alarms.scheduled.size)
  }

  @Test
  fun `les echeances sont remises en place a l'ouverture, apres un redemarrage`() {
    val existing = WatchedJourney(
      journeyId = 3,
      schedule = WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.MONDAY)),
      createdAt = now,
    )

    viewModel(watched = FakeWatchedJourneysRepository(listOf(existing)))

    assertEquals(listOf(existing), alarms.synchronised)
  }

  private fun viewModel(
    watched: FakeWatchedJourneysRepository = this.watched,
    session: SearchSession = this.session.apply {
      setFrom(lyon)
      setTo(nation)
    },
  ): WatchViewModel {
    selection.select(journey())
    return WatchViewModel(
      favorites = favorites,
      watched = watched,
      store = store,
      scheduler = alarms,
      notifications = notifier,
      selection = selection,
      session = session,
      zone = zone,
      now = { now },
    )
  }

  private fun journey(): Journey {
    val leg = JourneyLeg.Transit(
      startTime = departure,
      endTime = departure.plus(Duration.ofMinutes(20)),
      scheduledStartTime = departure,
      scheduledEndTime = departure.plus(Duration.ofMinutes(20)),
      duration = Duration.ofMinutes(20),
      from = place("Gare de Lyon", departure),
      to = place("Nation", departure.plus(Duration.ofMinutes(20))),
      lineName = "1",
    )
    return Journey(
      id = "itin-1",
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
