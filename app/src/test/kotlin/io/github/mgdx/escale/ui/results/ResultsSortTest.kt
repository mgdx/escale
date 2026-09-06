package io.github.mgdx.escale.ui.results

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.JourneySort
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le tri de la liste d'un onglet (SPEC.md § 5.2), tel que la feuille de résultats l'applique.
 *
 * L'ordre lui-même est une règle de `:core`, vérifiée par `JourneySortTest`. Ce qui se joue ici est
 * ce que `:core` ne peut pas savoir : **aucune requête n'en découle**, **chaque onglet garde son
 * propre ordre**, la carte suit la liste affichée, une nouvelle recherche remet les quatre onglets
 * à l'ordre des départs, et les choix survivent à la mort du processus.
 */
class ResultsSortTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val session = SearchSession()
  private val repository = FakePlanRepository()
  private val selection = SelectedJourneyStore()
  private val preferences = MutableStateFlow(SearchPreferences())
  private val display = MutableStateFlow(DisplayPreferences())
  private val clock = Instant.parse("2026-09-01T08:00:00Z")

  /** Part le premier, mais met une heure. */
  private val slow = journey("lent", afterMinutes = 0, minutes = 60)

  /** Part dix minutes plus tard et arrive bien avant. */
  private val quick = journey("rapide", afterMinutes = 10, minutes = 15)

  @Test
  fun `le tri ordonne la liste affichee sans aucune requete`() = runTest {
    val model = searching()
    val requests = repository.calls.size

    assertEquals(JourneySort.DEPARTURE, model.uiState.value.sort)
    assertEquals(listOf("lent", "rapide"), model.uiState.value.visibleJourneys.map { it.id })

    model.onSortChanged(JourneySort.DURATION)

    assertEquals(listOf("rapide", "lent"), model.uiState.value.visibleJourneys.map { it.id })
    // Le tri est local : la liste affichée en est dérivée, rien n'est redemandé au serveur.
    assertEquals(requests, repository.calls.size)
  }

  @Test
  fun `changer de tri met en evidence le premier trajet de la liste affichee`() = runTest {
    val model = searching()
    assertEquals(slow, selection.selected.value)

    model.onSortChanged(JourneySort.DURATION)

    // La carte trace le premier trajet de la liste affichée (SPEC.md § 5.1) : trier l'y déplace.
    assertEquals(quick, selection.selected.value)
    assertEquals(quick.stableKey(), model.uiState.value.selectedKey)
  }

  // --- Un ordre par onglet (SPEC.md § 5.2) -------------------------------------------------------

  @Test
  fun `trier un onglet ne touche pas aux autres`() = runTest {
    val model = searching()
    model.onSortChanged(JourneySort.DURATION)

    model.onCategorySelected(JourneyCategory.WALK)

    // L'onglet À pied n'a jamais été trié : il s'en tient à l'ordre des départs, et sa liste avec.
    assertEquals(JourneySort.DEPARTURE, model.uiState.value.sort)
    assertEquals(listOf("lent", "rapide"), model.uiState.value.visibleJourneys.map { it.id })
  }

  @Test
  fun `le tri d un onglet survit a un aller-retour entre onglets`() = runTest {
    val model = searching()
    model.onSortChanged(JourneySort.DURATION)

    model.onCategorySelected(JourneyCategory.WALK)
    model.onSortChanged(JourneySort.TRANSFERS)
    model.onCategorySelected(JourneyCategory.TRANSIT)

    // Chaque onglet retrouve l'ordre qu'on y avait choisi.
    assertEquals(JourneySort.DURATION, model.uiState.value.sort)
    assertEquals(listOf("rapide", "lent"), model.uiState.value.visibleJourneys.map { it.id })
    assertEquals(
      mapOf(JourneyCategory.TRANSIT to JourneySort.DURATION, JourneyCategory.WALK to JourneySort.TRANSFERS),
      model.uiState.value.sorts,
    )
  }

  @Test
  fun `une nouvelle recherche remet les quatre onglets a l ordre des departs`() = runTest {
    val model = searching()
    model.onSortChanged(JourneySort.DURATION)
    model.onCategorySelected(JourneyCategory.WALK)
    model.onSortChanged(JourneySort.TRANSFERS)

    session.setTime(TimeChoice.DepartAt(Instant.parse("2026-09-01T09:00:00Z")))

    // Les tris portaient sur des trajets qui ne sont plus affichés : aucun onglet n'en garde un.
    assertEquals(emptyMap<JourneyCategory, JourneySort>(), model.uiState.value.sorts)
    assertEquals(JourneySort.DEPARTURE, model.uiState.value.sort)
  }

  @Test
  fun `la table des tris survit a la mort du processus`() = runTest {
    val savedState = SavedStateHandle()
    val model = searching(savedState)
    model.onSortChanged(JourneySort.DURATION)
    model.onCategorySelected(JourneyCategory.WALK)
    model.onSortChanged(JourneySort.TRANSFERS)

    // Le processus meurt : seul `SavedStateHandle` est restitué. La recherche repart d'une requête,
    // mais ce n'est pas une nouvelle recherche : les ordres choisis lui survivent, tous.
    val restored = viewModel(savedState)

    assertEquals(
      mapOf(JourneyCategory.TRANSIT to JourneySort.DURATION, JourneyCategory.WALK to JourneySort.TRANSFERS),
      restored.uiState.value.sorts,
    )
  }

  // --- Fabriques ---------------------------------------------------------------------------------

  /** Une recherche complète, dont chaque onglet propose [slow] puis [quick]. */
  private fun searching(savedState: SavedStateHandle = SavedStateHandle()): ResultsViewModel {
    val page = Outcome.Success(JourneyPage(journeys = listOf(slow, quick)))
    JourneyCategory.entries.forEach { repository.answers[it] = page }
    val model = viewModel(savedState)
    session.setFrom(location("depart"))
    session.setTo(location("arrivee"))
    return model
  }

  private fun viewModel(savedState: SavedStateHandle) =
    ResultsViewModel(session, repository, preferences, display, selection, savedState) { clock }

  private fun location(name: String) = Location(
    id = null,
    name = name,
    description = null,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    kind = PlaceKind.ADDRESS,
  )

  private fun journey(id: String, afterMinutes: Long, minutes: Long): Journey {
    val start = Instant.parse("2026-09-01T08:00:00Z").plusSeconds(afterMinutes * 60)
    val end = start.plusSeconds(Duration.ofMinutes(minutes).seconds)
    return Journey(
      id = id,
      startTime = start,
      endTime = end,
      scheduledStartTime = start,
      scheduledEndTime = end,
      duration = Duration.ofMinutes(minutes),
      transfers = 0,
      legs = listOf(
        JourneyLeg.Bike(
          startTime = start,
          endTime = end,
          scheduledStartTime = start,
          scheduledEndTime = end,
          duration = Duration.ofMinutes(minutes),
          from = place(start),
          to = place(end),
        ),
      ),
    )
  }

  private fun place(time: Instant) = Place(
    name = "arret",
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    stopId = null,
    track = null,
    scheduledTime = time,
    time = time,
  )
}
