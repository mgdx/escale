package io.github.mgdx.escale.ui.detail

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DetailViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val repository = RecordingPlanRepository()

  private val selection = SelectedJourneyStore()

  private val savedState = SavedStateHandle()

  private val fixedNow = Instant.parse("2026-09-01T09:30:00Z")

  @Test
  fun `sans trajet choisi, l'ecran se referme et n'emet aucune requete`() {
    val viewModel = viewModel()

    assertTrue(viewModel.uiState.value.closed)
    assertTrue(repository.refreshCalls.isEmpty())
    assertTrue(repository.planCalls.isEmpty())
  }

  @Test
  fun `a l'ouverture, le trajet sommaire est affiche avant meme la requete detaillee`() {
    val summary = journeyOf("id-1")
    selection.select(summary)
    repository.refreshAnswer = Outcome.Failure(EscaleError.Timeout)

    val viewModel = viewModel()

    // La requête a échoué : c'est le trajet venu de la liste qui reste à l'écran, sous le bandeau.
    assertEquals(summary, viewModel.uiState.value.journey)
    assertFalse(viewModel.uiState.value.detailed)
    assertEquals(EscaleError.Timeout, viewModel.uiState.value.error)
  }

  @Test
  fun `l'ouverture demande le detail complet, jamais la variante allegee`() {
    selection.select(journeyOf("id-1"))
    val detailed = journeyOf("id-1", legs = listOf(walkLeg(0, 5), transitLeg(5, 20)))
    repository.refreshAnswer = Outcome.Success(detailed)

    val viewModel = viewModel()

    assertEquals(listOf("id-1" to true), repository.refreshCalls)
    assertTrue(viewModel.uiState.value.detailed)
    assertEquals(fixedNow, viewModel.uiState.value.refreshedAt)
  }

  @Test
  fun `le trajet detaille est republie, pour que la carte en trace la geometrie`() {
    selection.select(journeyOf("id-1"))
    val detailed = journeyOf("id-1", legs = listOf(walkLeg(0, 6), transitLeg(6, 20)))
    repository.refreshAnswer = Outcome.Success(detailed)

    viewModel()

    assertEquals(detailed, selection.selected.value)
  }

  @Test
  fun `un identifiant d'itineraire refuse declenche le repli sur la requete plan`() {
    val summary = journeyOf("id-perime")
    selection.select(summary)
    repository.refreshAnswer = Outcome.Failure(EscaleError.BadRequest(serverMessage = null))
    val closest = journeyOf("proche", legs = listOf(walkLeg(1, 6), transitLeg(6, 21)))
    val far = journeyOf("loin", legs = listOf(walkLeg(45, 50), transitLeg(50, 65)))
    repository.planAnswer = Outcome.Success(JourneyPage(journeys = listOf(far, closest)))

    val viewModel = viewModel()

    assertEquals(listOf(true), repository.planCalls)
    assertEquals("proche", viewModel.uiState.value.journey?.id)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun `un 404 sur refresh-itinerary declenche aussi le repli`() {
    selection.select(journeyOf("id-perime"))
    repository.refreshAnswer = Outcome.Failure(EscaleError.ApiVersionTooOld("/api/v6/refresh-itinerary"))
    repository.planAnswer = Outcome.Success(JourneyPage(journeys = listOf(journeyOf("rejoue"))))

    val viewModel = viewModel()

    assertEquals("rejoue", viewModel.uiState.value.journey?.id)
  }

  @Test
  fun `une panne passagere ne declenche pas de seconde requete`() {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Failure(EscaleError.NoNetwork)

    val viewModel = viewModel()

    assertTrue(repository.planCalls.isEmpty())
    assertEquals(EscaleError.NoNetwork, viewModel.uiState.value.error)
  }

  @Test
  fun `un trajet sans identifiant passe directement par la requete plan`() {
    selection.select(journeyOf(id = null))
    repository.planAnswer = Outcome.Success(JourneyPage(journeys = emptyList(), direct = listOf(journeyOf("direct"))))

    val viewModel = viewModel()

    assertTrue(repository.refreshCalls.isEmpty())
    assertEquals(listOf(true), repository.planCalls)
    assertEquals("direct", viewModel.uiState.value.journey?.id)
  }

  @Test
  fun `sans recherche en cours, le repli est impossible et l'echec est annonce`() {
    selection.select(journeyOf(id = null))

    val viewModel = viewModel(session = SearchSession())

    assertTrue(repository.planCalls.isEmpty())
    assertTrue(viewModel.uiState.value.error is EscaleError.Unknown)
  }

  @Test
  fun `une requete supplantee ne s'affiche jamais`() {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Failure(EscaleError.Superseded)

    val viewModel = viewModel()

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun `le depliage d'une portion survit a la mort du processus`() {
    selection.select(journeyOf("id-1"))
    val viewModel = viewModel()

    viewModel.onLegToggled(1)
    viewModel.onStopsToggled(1)
    viewModel.onStepsToggled(0)

    assertEquals(setOf(1), viewModel.uiState.value.expandedLegs)
    // Le même SavedStateHandle, relu par un ViewModel neuf : c'est ce que fait le système au retour.
    val restored = viewModel()
    assertEquals(setOf(1), restored.uiState.value.expandedLegs)
    assertEquals(setOf(1), restored.uiState.value.expandedStops)
    assertEquals(setOf(0), restored.uiState.value.expandedSteps)
  }

  @Test
  fun `un second appui replie la portion`() {
    selection.select(journeyOf("id-1"))
    val viewModel = viewModel()

    viewModel.onLegToggled(2)
    viewModel.onLegToggled(2)

    assertEquals(emptySet<Int>(), viewModel.uiState.value.expandedLegs)
  }

  @Test
  fun `un trajet recompose par le repli remet le depliage a zero`() {
    selection.select(journeyOf("id-perime"))
    repository.refreshAnswer = Outcome.Failure(EscaleError.BadRequest(serverMessage = null))
    // Le trajet rejoué n'a pas le même nombre de portions : les positions dépliées ne désignent
    // plus les mêmes portions.
    repository.planAnswer = Outcome.Success(
      JourneyPage(journeys = listOf(journeyOf("rejoue", legs = listOf(walkLeg(0, 20))))),
    )
    savedState["detail.legs"] = intArrayOf(1)

    val viewModel = viewModel()

    assertEquals(emptySet<Int>(), viewModel.uiState.value.expandedLegs)
  }

  private fun viewModel(session: SearchSession = sessionWithSearch()) = DetailViewModel(
    selection = selection,
    session = session,
    planRepository = repository,
    savedState = savedState,
    now = { fixedNow },
  )
}
