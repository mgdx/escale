package io.github.mgdx.escale.ui.results

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Les règles de la feuille de résultats qui ne se voient pas à l'écran (SPEC.md § 5.2 et § 7).
 *
 * Deux d'entre elles sont le cœur du lot et ne peuvent être vérifiées qu'ici : **un onglet non
 * consulté n'émet aucune requête**, et **une requête supplantée ne s'affiche jamais**.
 */
class ResultsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val session = SearchSession()
  private val repository = FakePlanRepository()
  private val selection = SelectedJourneyStore()

  private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
    ResultsViewModel(session, repository, selection, savedState)

  private fun completeSearch() {
    session.setFrom(location("depart"))
    session.setTo(location("arrivee"))
  }

  // --- Une requête par onglet, à l'ouverture de l'onglet ---------------------------------------

  @Test
  fun `une recherche incomplete n emet aucune requete`() = runTest {
    val model = viewModel()
    session.setFrom(location("depart"))

    assertTrue(repository.calls.isEmpty())
    assertFalse(model.uiState.value.open)
  }

  @Test
  fun `une recherche complete n interroge que l onglet transport en commun`() = runTest {
    val model = viewModel()
    completeSearch()

    // SPEC.md § 5.2 et § 7.3 : surtout pas quatre requêtes en parallèle au lancement.
    assertEquals(listOf(JourneyCategory.TRANSIT), repository.categories())
    assertTrue(model.uiState.value.open)
  }

  @Test
  fun `un onglet n est interroge qu a son ouverture`() = runTest {
    val model = viewModel()
    completeSearch()

    model.onCategorySelected(JourneyCategory.BIKE)

    assertEquals(listOf(JourneyCategory.TRANSIT, JourneyCategory.BIKE), repository.categories())
    assertEquals(2, model.uiState.value.tabs.size)
    // Les deux onglets jamais ouverts n'ont pas d'état du tout : ni requête, ni chargement.
    assertNull(model.uiState.value.tabs[JourneyCategory.CAR])
    assertNull(model.uiState.value.tabs[JourneyCategory.WALK])
  }

  @Test
  fun `revenir sur un onglet deja charge ne relance rien`() = runTest {
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.WALK)
    model.onCategorySelected(JourneyCategory.TRANSIT)
    model.onCategorySelected(JourneyCategory.WALK)

    // SPEC.md § 7.5 : le résultat est mis en cache pour la durée de la recherche.
    assertEquals(listOf(JourneyCategory.TRANSIT, JourneyCategory.WALK), repository.categories())
  }

  @Test
  fun `une nouvelle recherche repart de zero sans reveiller les autres onglets`() = runTest {
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.CAR)
    repository.calls.clear()

    session.setTo(location("autre-arrivee"))

    assertEquals(listOf(JourneyCategory.CAR), repository.categories())
    assertEquals(1, model.uiState.value.tabs.size)
  }

  // --- Les états de SPEC.md § 8 -----------------------------------------------------------------

  @Test
  fun `un onglet sans resultat donne un etat vide, pas une erreur`() = runTest {
    val model = viewModel()
    completeSearch()

    val tab = model.uiState.value.current
    assertTrue(tab.isEmpty)
    assertNull(tab.error)
    assertFalse(tab.loading)
  }

  @Test
  fun `un echec est expose tel quel et la reprise relance la requete`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Failure(EscaleError.NoNetwork)
    val model = viewModel()
    completeSearch()

    assertEquals(EscaleError.NoNetwork, model.uiState.value.current.error)

    repository.answers.remove(JourneyCategory.TRANSIT)
    model.onRetry()

    assertNull(model.uiState.value.current.error)
    assertEquals(2, repository.calls.size)
  }

  @Test
  fun `un echec ne se rejoue pas a chaque changement d onglet`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Failure(EscaleError.Timeout)
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.WALK)
    model.onCategorySelected(JourneyCategory.TRANSIT)

    assertEquals(listOf(JourneyCategory.TRANSIT, JourneyCategory.WALK), repository.categories())
  }

  @Test
  fun `une requete supplantee ne s affiche jamais`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Failure(EscaleError.Superseded)
    val model = viewModel()
    completeSearch()

    // SPEC.md § 7.2 : un résultat plus récent arrive derrière. Ni erreur, ni liste vide : l'attente
    // continue jusqu'à ce qu'il arrive.
    val tab = model.uiState.value.current
    assertNull(tab.error)
    assertNull(tab.feed)
    assertTrue(tab.loading)
  }

  // --- Pagination ------------------------------------------------------------------------------

  @Test
  fun `plus tard renvoie la meme requete avec le seul curseur suivant`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(
      JourneyPage(journeys = listOf(journey("a", 0)), previousPageCursor = "avant", nextPageCursor = "apres"),
    )
    val model = viewModel()
    completeSearch()

    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(
      JourneyPage(journeys = listOf(journey("b", 30)), nextPageCursor = "encore"),
    )
    model.onLater()

    assertEquals(FakePlanRepository.Call(JourneyCategory.TRANSIT, "apres"), repository.calls.last())
    val feed = model.uiState.value.current.feed
    assertEquals(listOf("a", "b"), feed?.journeys?.map { it.id })
    // Le curseur « plus tôt » ne bouge pas : la liste s'étend par le bas.
    assertEquals("avant", feed?.previousPageCursor)
    assertEquals("encore", feed?.nextPageCursor)
  }

  @Test
  fun `plus tot ajoute les trajets anterieurs en tete`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(
      JourneyPage(journeys = listOf(journey("b", 30)), previousPageCursor = "avant"),
    )
    val model = viewModel()
    completeSearch()

    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(
      JourneyPage(journeys = listOf(journey("a", 0)), previousPageCursor = "encore-avant"),
    )
    model.onEarlier()

    assertEquals(FakePlanRepository.Call(JourneyCategory.TRANSIT, "avant"), repository.calls.last())
    assertEquals(listOf("a", "b"), model.uiState.value.current.feed?.journeys?.map { it.id })
  }

  @Test
  fun `sans curseur, la pagination n emet rien`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    val model = viewModel()
    completeSearch()
    repository.calls.clear()

    model.onEarlier()
    model.onLater()

    assertTrue(repository.calls.isEmpty())
  }

  // --- Choix d'un trajet et filtre de l'onglet Vélo ----------------------------------------------

  @Test
  fun `le trajet choisi est publie pour la carte`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    completeSearch()

    model.onJourneySelected(trip)

    assertEquals(trip, selection.selected.value)
    assertEquals(trip.stableKey(), model.uiState.value.selectedKey)
  }

  @Test
  fun `une nouvelle recherche oublie le trajet choisi`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    completeSearch()
    model.onJourneySelected(trip)

    session.setTime(TimeChoice.DepartAt(Instant.parse("2026-09-01T09:00:00Z")))

    assertNull(selection.selected.value)
    assertNull(model.uiState.value.selectedKey)
  }

  @Test
  fun `le filtre de l onglet velo separe le velo personnel du libre-service`() = runTest {
    val own = journey("perso", 0)
    val shared = rentalJourney("partage", 10)
    repository.answers[JourneyCategory.BIKE] = Outcome.Success(JourneyPage(journeys = listOf(own, shared)))
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.BIKE)

    assertTrue(model.uiState.value.bikeFilterVisible)
    assertEquals(2, model.uiState.value.visibleJourneys.size)

    model.onBikeFilterChanged(BikeFilter.SHARED)
    assertEquals(listOf("partage"), model.uiState.value.visibleJourneys.map { it.id })

    model.onBikeFilterChanged(BikeFilter.OWN)
    assertEquals(listOf("perso"), model.uiState.value.visibleJourneys.map { it.id })
  }

  // --- Survie à la rotation et à la mort du processus --------------------------------------------

  @Test
  fun `l onglet consulte survit a la mort du processus`() = runTest {
    val savedState = SavedStateHandle()
    val model = viewModel(savedState)
    completeSearch()
    model.onCategorySelected(JourneyCategory.CAR)

    // Le processus meurt : seul `SavedStateHandle` est restitué.
    val restored = viewModel(savedState)

    assertEquals(JourneyCategory.CAR, restored.uiState.value.category)
    // Et les résultats, eux, ne sont pas sauvegardés : ils repartent d'une requête (SPEC.md § 11).
    assertEquals(JourneyCategory.CAR, repository.calls.last().category)
  }

  // --- Fabriques ---------------------------------------------------------------------------------

  private fun location(name: String) = Location(
    id = null,
    name = name,
    description = null,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    kind = PlaceKind.ADDRESS,
  )

  private fun place(time: Instant) = Place(
    name = "arret",
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    stopId = null,
    track = null,
    scheduledTime = time,
    time = time,
  )

  private fun journey(id: String, afterMinutes: Long): Journey = journeyOf(id, afterMinutes, rental = false)

  private fun rentalJourney(id: String, afterMinutes: Long): Journey = journeyOf(id, afterMinutes, rental = true)

  private fun journeyOf(id: String, afterMinutes: Long, rental: Boolean): Journey {
    val start = Instant.parse("2026-09-01T08:00:00Z").plusSeconds(afterMinutes * 60)
    val end = start.plusSeconds(Duration.ofMinutes(20).seconds)
    val leg = if (rental) {
      JourneyLeg.Rental(
        startTime = start,
        endTime = end,
        scheduledStartTime = start,
        scheduledEndTime = end,
        duration = Duration.ofMinutes(20),
        from = place(start),
        to = place(end),
        rental = RentalInfo(
          systemId = "systeme",
          systemName = "Systeme",
          providerId = null,
          color = null,
          url = null,
          fromStationName = null,
          toStationName = null,
          rentalUriAndroid = null,
          formFactor = RentalFormFactor.BICYCLE,
          propulsionType = null,
          returnConstraint = null,
        ),
      )
    } else {
      JourneyLeg.Bike(
        startTime = start,
        endTime = end,
        scheduledStartTime = start,
        scheduledEndTime = end,
        duration = Duration.ofMinutes(20),
        from = place(start),
        to = place(end),
      )
    }
    return Journey(
      id = id,
      startTime = start,
      endTime = end,
      scheduledStartTime = start,
      scheduledEndTime = end,
      duration = Duration.ofMinutes(20),
      transfers = 0,
      legs = listOf(leg),
    )
  }
}
