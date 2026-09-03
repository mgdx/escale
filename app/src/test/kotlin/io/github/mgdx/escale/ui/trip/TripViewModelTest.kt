package io.github.mgdx.escale.ui.trip

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.departures.RecordingTripRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** L'écran « détail de la course » (SPEC.md § 5.3). */
class TripViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val trips = RecordingTripRepository()
  private val fixedNow: Instant = Instant.parse("2026-09-02T05:34:00Z")
  private var clock = fixedNow

  private fun viewModel(lineName: String = "", headsign: String = "") = TripViewModel(
    tripId = "course",
    lineName = lineName,
    headsign = headsign,
    tripRepository = trips,
    now = { clock },
  )

  private fun place(name: String, at: Instant, track: String? = null, alerts: List<Disruption> = emptyList()) = Place(
    name = name,
    coordinates = LatLon(53.55, 10.0),
    stopId = "stop:$name",
    track = track,
    scheduledTime = at,
    time = at,
    alerts = alerts,
  )

  private fun journey(stops: List<String> = listOf("Hamburg Hbf", "Berlin Hbf")): Journey {
    val end = fixedNow.plusSeconds(3600)
    val leg = JourneyLeg.Transit(
      startTime = fixedNow,
      endTime = end,
      scheduledStartTime = fixedNow,
      scheduledEndTime = end,
      duration = Duration.ofHours(1),
      from = place("Altona", fixedNow),
      to = place("Nürnberg Hbf", end),
      mode = TransitMode.HIGHSPEED_RAIL,
      lineName = "ICE 91",
      headsign = "Nürnberg Hbf",
      agencyName = "DB Fernverkehr AG",
      intermediateStops = stops.mapIndexed { index, name ->
        val at = fixedNow.plusSeconds((index + 1) * 600L)
        StopVisit(place = place(name, at), arrival = at, departure = at.plusSeconds(120))
      },
    )
    return Journey(
      id = null,
      startTime = fixedNow,
      endTime = end,
      scheduledStartTime = fixedNow,
      scheduledEndTime = end,
      duration = Duration.ofHours(1),
      transfers = 0,
      legs = listOf(leg),
    )
  }

  @Test
  fun `un arret perturbe n'affiche que ce qui lui est propre`() {
    // Le serveur attache le même message aux deux niveaux : au niveau de la course, où le bandeau
    // de tête l'annonce déjà, et au niveau de l'arrêt. L'écran ne doit pas le dire deux fois.
    val course = Disruption(headerText = "Retard prévisible", descriptionText = "5 min")
    val stopOnly = Disruption(headerText = "Départ voie 8", descriptionText = "Voie modifiée")
    val end = fixedNow.plusSeconds(3600)
    val at = fixedNow.plusSeconds(600)
    val leg = JourneyLeg.Transit(
      startTime = fixedNow,
      endTime = end,
      scheduledStartTime = fixedNow,
      scheduledEndTime = end,
      duration = Duration.ofHours(1),
      from = place("Altona", fixedNow),
      to = place("Nürnberg Hbf", end),
      alerts = listOf(course),
      mode = TransitMode.HIGHSPEED_RAIL,
      lineName = "ICE 91",
      intermediateStops = listOf(
        StopVisit(
          place = place("Berlin Hbf", at, alerts = listOf(course, stopOnly)),
          arrival = at,
          departure = at.plusSeconds(120),
        ),
      ),
    )
    trips.tripAnswer = Outcome.Success(
      Journey(
        id = null,
        startTime = fixedNow,
        endTime = end,
        scheduledStartTime = fixedNow,
        scheduledEndTime = end,
        duration = Duration.ofHours(1),
        transfers = 0,
        legs = listOf(leg),
      ),
    )

    val state = viewModel().uiState.value
    val berlin = state.calls.single { it.place.name == "Berlin Hbf" }

    assertEquals(listOf(course), state.alerts)
    assertEquals(listOf(stopOnly), state.alertsAt(berlin))
    assertTrue(state.alertsAt(state.calls.first()).isEmpty())
  }

  @Test
  fun `l'ouverture demande la course sans sa geometrie`() {
    trips.tripAnswer = Outcome.Success(journey())

    val viewModel = viewModel()

    // SPEC.md § 7.6 : la géométrie ne se demande que là où elle sert, et cet écran ne trace rien.
    assertEquals(listOf("course" to false), trips.tripCalls)
    assertEquals(fixedNow, viewModel.uiState.value.loadedAt)
  }

  @Test
  fun `la desserte affichee va d'un terminus a l'autre`() {
    trips.tripAnswer = Outcome.Success(journey())

    val calls = viewModel().uiState.value.calls

    assertEquals(listOf("Altona", "Hamburg Hbf", "Berlin Hbf", "Nürnberg Hbf"), calls.map { it.place.name })
  }

  @Test
  fun `le titre venu de la navigation cede la place a celui du serveur`() {
    trips.tripAnswer = Outcome.Success(journey())

    val state = viewModel(lineName = "ICE", headsign = "").uiState.value

    assertEquals("ICE 91", state.lineName)
    assertEquals("Nürnberg Hbf", state.headsign)
  }

  @Test
  fun `un echec garde le titre provisoire plutot qu'un ecran anonyme`() {
    trips.tripAnswer = Outcome.Failure(EscaleError.Timeout)

    val state = viewModel(lineName = "ICE 91", headsign = "Nürnberg Hbf").uiState.value

    assertEquals("ICE 91", state.lineName)
    assertEquals(EscaleError.Timeout, state.error)
    assertNull(state.journey)
    assertFalse(state.loading)
  }

  @Test
  fun `un rafraichissement qui echoue laisse la desserte a l'ecran`() {
    trips.tripAnswer = Outcome.Success(journey())
    val viewModel = viewModel()

    trips.tripAnswer = Outcome.Failure(EscaleError.NoNetwork)
    viewModel.onRefresh()

    assertEquals(EscaleError.NoNetwork, viewModel.uiState.value.error)
    assertEquals(4, viewModel.uiState.value.calls.size)
  }

  @Test
  fun `une course sans arret publie donne un etat vide, pas un ecran muet`() {
    trips.tripAnswer = Outcome.Success(
      Journey(
        id = null,
        startTime = fixedNow,
        endTime = fixedNow,
        scheduledStartTime = fixedNow,
        scheduledEndTime = fixedNow,
        duration = Duration.ZERO,
        transfers = 0,
        legs = emptyList(),
      ),
    )

    val state = viewModel().uiState.value

    assertTrue(state.calls.isEmpty())
    assertNull(state.leg)
  }

  @Test
  fun `le retour au premier plan ne rafraichit qu'au dela de soixante secondes`() {
    trips.tripAnswer = Outcome.Success(journey())
    val viewModel = viewModel()

    clock = fixedNow.plusSeconds(30)
    viewModel.onForeground()
    assertEquals(1, trips.tripCalls.size)

    clock = fixedNow.plusSeconds(61)
    viewModel.onForeground()
    assertEquals(2, trips.tripCalls.size)
  }
}
