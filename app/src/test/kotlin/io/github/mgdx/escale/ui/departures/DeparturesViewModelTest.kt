package io.github.mgdx.escale.ui.departures

import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** L'écran des prochains départs (SPEC.md § 5.4). */
class DeparturesViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val trips = RecordingTripRepository()
  private val fixedNow: Instant = Instant.parse("2026-09-02T05:48:00Z")
  private var clock = fixedNow
  private var stops = FakeStopsRepository(Outcome.Failure(EscaleError.NoNetwork))

  private fun viewModel() = DeparturesViewModel(
    stopId = "arret",
    stopName = "Hamburg Hbf",
    tripRepository = trips,
    stopsRepository = stops,
    now = { clock },
  )

  private fun entry(
    line: String,
    mode: TransitMode,
    minutes: Long = 0,
    trip: String = "course-$line-$minutes",
    cancelled: Boolean = false,
    alerts: List<Disruption> = emptyList(),
  ) = StopTimeEntry(
    tripId = trip,
    mode = mode,
    lineName = line,
    headsign = "Terminus",
    agencyName = "Hochbahn",
    track = null,
    scheduledTime = fixedNow.plusSeconds(minutes * 60),
    time = fixedNow.plusSeconds(minutes * 60),
    cancelled = cancelled,
    alerts = alerts,
  )

  private fun page(
    entries: List<StopTimeEntry>,
    modes: List<TransitMode> = entries.map { it.mode },
    previous: String? = "EARLIER|1",
    next: String? = "LATER|1",
  ) = StopTimePage(
    entries = entries,
    stop = stopOf("Hamburg Hauptbahnhof", modes, emptyList()),
    previousPageCursor = previous,
    nextPageCursor = next,
  )

  // --- Le chargement --------------------------------------------------------------------------

  @Test
  fun `l'ouverture demande les departs de l'arret, sans filtre`() {
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))

    val viewModel = viewModel()

    assertEquals(1, trips.departureCalls.size)
    val call = trips.departureCalls.single()
    assertEquals("arret", call.stopId)
    assertEquals(fixedNow, call.time)
    assertTrue(call.modes.isEmpty())
    assertNull(call.cursor)
    assertEquals("Hamburg Hauptbahnhof", viewModel.uiState.value.stopName)
    assertEquals(fixedNow, viewModel.uiState.value.loadedAt)
  }

  @Test
  fun `une reponse vide se distingue d'un ecran jamais charge`() {
    trips.departuresAnswer = Outcome.Success(page(emptyList(), modes = emptyList()))

    val state = viewModel().uiState.value

    // SPEC.md § 8 : l'état vide explicite, et non une liste muette.
    assertTrue(state.isEmpty)
    assertFalse(state.loading)
  }

  @Test
  fun `un echec laisse les horaires deja lus a l'ecran`() {
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))
    val viewModel = viewModel()

    trips.departuresAnswer = Outcome.Failure(EscaleError.Timeout)
    viewModel.onRefresh()

    val state = viewModel.uiState.value
    assertEquals(EscaleError.Timeout, state.error)
    assertEquals(1, state.entries.size)
  }

  // --- Le filtre par mode : le piège des parapluies --------------------------------------------

  @Test
  fun `le filtre train envoie les feuilles de RAIL, jamais le parapluie`() {
    // **Le verrou du piège de docs/architecture.md § 11.5, côté interface.** Le `ViewModel` ne
    // recompose aucun ensemble : il transmet `requestModes`, qui ne contient que des feuilles.
    // Envoyer `RAIL` ramasserait le métro, qui a pourtant sa propre puce.
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))
    val viewModel = viewModel()

    viewModel.onFilterSelected(DepartureModeFilter.TRAIN)

    val modes = trips.departureCalls.last().modes
    assertEquals(DepartureModeFilter.TRAIN.requestModes, modes)
    assertFalse(modes.contains(TransitMode.RAIL))
    assertFalse(modes.contains(TransitMode.SUBWAY))
  }

  @Test
  fun `les puces viennent des modes annonces par le serveur, train regional compris`() {
    // Le cas de Châtelet - Les Halles : un arrêt que MOTIS annonce en `REGIONAL_RAIL` seul doit
    // avoir une puce « train ». Un filtre qui chercherait littéralement `RAIL` ne la donnerait pas.
    trips.departuresAnswer = Outcome.Success(
      page(
        entries = listOf(entry("RE8", TransitMode.REGIONAL_RAIL), entry("18", TransitMode.BUS, minutes = 2)),
        modes = listOf(TransitMode.REGIONAL_RAIL, TransitMode.BUS),
      ),
    )

    val state = viewModel().uiState.value

    assertEquals(listOf(DepartureModeFilter.TRAIN, DepartureModeFilter.BUS), state.filters)
  }

  @Test
  fun `filtrer ne fait pas disparaitre la puce qui a servi a filtrer`() {
    trips.departuresAnswer = Outcome.Success(
      page(
        entries = listOf(entry("RE8", TransitMode.REGIONAL_RAIL), entry("18", TransitMode.BUS, minutes = 2)),
        modes = listOf(TransitMode.REGIONAL_RAIL, TransitMode.BUS),
      ),
    )
    val viewModel = viewModel()

    // La réponse filtrée ne contient plus que des bus : recalculer les puces les réduirait à une.
    trips.departuresAnswer = Outcome.Success(
      page(entries = listOf(entry("18", TransitMode.BUS)), modes = listOf(TransitMode.BUS)),
    )
    viewModel.onFilterSelected(DepartureModeFilter.BUS)

    assertEquals(listOf(DepartureModeFilter.TRAIN, DepartureModeFilter.BUS), viewModel.uiState.value.filters)
    assertEquals(DepartureModeFilter.BUS, viewModel.uiState.value.filter)
  }

  @Test
  fun `rechoisir la meme puce n'emet aucune requete`() {
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))
    val viewModel = viewModel()
    viewModel.onFilterSelected(DepartureModeFilter.TRAIN)
    val before = trips.departureCalls.size

    viewModel.onFilterSelected(DepartureModeFilter.TRAIN)

    assertEquals(before, trips.departureCalls.size)
  }

  // --- La pagination --------------------------------------------------------------------------

  @Test
  fun `plus tard etend la liste avec le curseur du serveur`() {
    trips.departuresAnswer = Outcome.Success(
      page(listOf(entry("S1", TransitMode.SUBURBAN)), next = "LATER|1"),
    )
    val viewModel = viewModel()

    trips.departuresAnswer = Outcome.Success(
      page(listOf(entry("S2", TransitMode.SUBURBAN, minutes = 5)), previous = "EARLIER|2", next = "LATER|2"),
    )
    viewModel.onPage(DeparturesPage.LATER)

    assertEquals("LATER|1", trips.departureCalls.last().cursor)
    assertEquals(listOf("S1", "S2"), viewModel.uiState.value.entries.map { it.lineName })
    assertNull(viewModel.uiState.value.paging)
  }

  @Test
  fun `sans curseur, la pagination n'emet aucune requete`() {
    trips.departuresAnswer = Outcome.Success(
      page(listOf(entry("S1", TransitMode.SUBURBAN)), previous = null, next = null),
    )
    val viewModel = viewModel()
    val before = trips.departureCalls.size

    viewModel.onPage(DeparturesPage.LATER)
    viewModel.onPage(DeparturesPage.EARLIER)

    assertEquals(before, trips.departureCalls.size)
    assertFalse(viewModel.uiState.value.canPage(DeparturesPage.LATER))
  }

  // --- La sobriété ----------------------------------------------------------------------------

  @Test
  fun `le retour au premier plan ne rafraichit qu'au dela de soixante secondes`() {
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))
    val viewModel = viewModel()
    val before = trips.departureCalls.size

    clock = fixedNow.plusSeconds(30)
    viewModel.onForeground()
    assertEquals(before, trips.departureCalls.size)

    clock = fixedNow.plusSeconds(61)
    viewModel.onForeground()
    assertEquals(before + 1, trips.departureCalls.size)
  }

  @Test
  fun `la desserte de l'arret n'est demandee qu'une fois, jamais a chaque rafraichissement`() {
    stops = FakeStopsRepository(
      Outcome.Success(
        stopOf(
          name = "Hamburg Hauptbahnhof",
          modes = listOf(TransitMode.SUBURBAN),
          lines = listOf(
            StopLine(id = "l", shortName = "S1", longName = "", mode = TransitMode.SUBURBAN, agencyName = "DB"),
          ),
        ),
      ),
    )
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))
    val viewModel = viewModel()

    viewModel.onRefresh()
    viewModel.onRefresh()

    // Une desserte ne change pas d'une minute à l'autre, là où les horaires en dessous changent
    // sans arrêt (SPEC.md § 7).
    assertEquals(1, stops.calls)
    assertEquals(listOf("S1"), viewModel.uiState.value.lines.map { it.label })
  }

  @Test
  fun `une desserte introuvable ne fait pas echouer l'ecran`() {
    stops = FakeStopsRepository(Outcome.Failure(EscaleError.Timeout))
    trips.departuresAnswer = Outcome.Success(page(listOf(entry("S1", TransitMode.SUBURBAN))))

    val state = viewModel().uiState.value

    // Ce sont les départs qui font l'écran : un bandeau d'erreur ferait croire qu'ils ont échoué.
    assertNull(state.error)
    assertTrue(state.lines.isEmpty())
    assertEquals(1, state.entries.size)
  }

  @Test
  fun `les perturbations des departs alimentent le bandeau de l'arret`() {
    val alert = Disruption(headerText = "Travaux", descriptionText = "")
    trips.departuresAnswer = Outcome.Success(
      page(
        listOf(
          entry("S1", TransitMode.SUBURBAN, alerts = listOf(alert)),
          entry("18", TransitMode.BUS, minutes = 3, alerts = listOf(alert)),
        ),
      ),
    )

    // Le dédoublonnage revient à `Disruptions`, dans `:core` : l'état les réunit, il ne les trie pas.
    assertEquals(2, viewModel().uiState.value.alerts.size)
  }
}
