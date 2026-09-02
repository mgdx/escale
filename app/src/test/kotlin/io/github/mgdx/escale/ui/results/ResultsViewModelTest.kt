package io.github.mgdx.escale.ui.results

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.core.query.PlanQueryBuilder
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

  /** Les réglages de SPEC.md § 5.6, tels que le dépôt les émettrait. */
  private val preferences = MutableStateFlow(SearchPreferences())

  private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
    ResultsViewModel(session, repository, preferences, selection, savedState)

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

  // --- Mise en évidence d'un trajet et ouverture du détail (SPEC.md § 5.1 et § 5.3) -------------

  @Test
  fun `des resultats mettent en evidence leur premier trajet sans aucun appui`() = runTest {
    val first = journey("a", 0)
    val second = journey("b", 10)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(first, second)))
    val model = viewModel()

    completeSearch()

    // SPEC.md § 5.1 : « la carte reste visible en haut et cadre le trajet sélectionné ». Sans
    // sélection d'office, elle ne cadrerait jamais rien tant que personne n'a appuyé.
    assertEquals(first, selection.selected.value)
    assertEquals(first.stableKey(), model.uiState.value.selectedKey)
  }

  @Test
  fun `un appui met le trajet en evidence et demande l ouverture du detail`() = runTest {
    val first = journey("a", 0)
    val second = journey("b", 10)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(first, second)))
    val model = viewModel()
    val openings = openingsOf(model)
    completeSearch()

    model.onJourneySelected(second)

    assertEquals(second, selection.selected.value)
    assertEquals(second.stableKey(), model.uiState.value.selectedKey)
    assertEquals(1, openings.size)
  }

  @Test
  fun `le retour depuis le detail conserve le trajet mis en evidence`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    completeSearch()
    model.onJourneySelected(trip)

    // Quitter l'écran de détail ne passe par aucun chemin de ce ViewModel : c'est précisément ce
    // qui fait survivre le tracé au retour en arrière (anomalie A11).
    assertEquals(trip, selection.selected.value)
    assertEquals(trip.stableKey(), model.uiState.value.selectedKey)
  }

  @Test
  fun `reappuyer sur la meme carte redemande l ouverture du detail`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    val openings = openingsOf(model)
    completeSearch()

    model.onJourneySelected(trip)
    model.onJourneySelected(trip)

    // Le trajet ne change pas : seule une `StateFlow` s'en tairait. L'événement, lui, repart.
    assertEquals(2, openings.size)
  }

  @Test
  fun `changer d onglet met en evidence le premier trajet du nouveau jeu`() = runTest {
    val transit = journey("transit", 0)
    val walk = journey("walk", 5)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(transit)))
    repository.answers[JourneyCategory.WALK] = Outcome.Success(JourneyPage(journeys = listOf(walk)))
    val model = viewModel()
    completeSearch()
    assertEquals(transit, selection.selected.value)

    model.onCategorySelected(JourneyCategory.WALK)

    assertEquals(walk, selection.selected.value)
    assertEquals(walk.stableKey(), model.uiState.value.selectedKey)
  }

  @Test
  fun `une nouvelle recherche met en evidence le premier trajet de ses resultats`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    completeSearch()
    model.onJourneySelected(trip)

    val other = journey("b", 45)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(other)))
    session.setTime(TimeChoice.DepartAt(Instant.parse("2026-09-01T09:00:00Z")))

    assertEquals(other, selection.selected.value)
    assertEquals(other.stableKey(), model.uiState.value.selectedKey)
  }

  @Test
  fun `une recherche sans resultat n en met aucun en evidence`() = runTest {
    val trip = journey("a", 0)
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(trip)))
    val model = viewModel()
    completeSearch()
    assertEquals(trip, selection.selected.value)

    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = emptyList()))
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

  /**
   * Les demandes d'ouverture reçues jusqu'ici, collectées **au fil de l'eau**.
   *
   * Le répartiteur non confiné est celui de l'application réelle, où un `LaunchedEffect` collecte
   * en continu : sans lui, deux appuis se retrouveraient conflatés en un seul dans le canal, ce
   * qui ne se produit jamais à l'écran.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun TestScope.openingsOf(model: ResultsViewModel): List<Unit> {
    val received = mutableListOf<Unit>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openDetail.toList(received) }
    return received
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

  // --- Les réglages de recherche de SPEC.md § 5.6 ----------------------------------------------

  @Test
  fun `un reglage de recherche part avec la requete emise`() = runTest {
    preferences.value = SearchPreferences(pedestrianProfile = PedestrianProfile.WHEELCHAIR)
    viewModel()

    completeSearch()

    // Vérifié sur les paramètres réellement construits, et non sur l'objet transporté : c'est ce
    // que le serveur recevra.
    val parameters = PlanQueryBuilder.build(repository.queries.last())
    assertEquals("WHEELCHAIR", parameters["pedestrianProfile"])
  }

  @Test
  fun `changer un reglage relance le seul onglet consulte`() = runTest {
    val model = viewModel()
    completeSearch()
    assertEquals(listOf(JourneyCategory.TRANSIT), repository.categories())

    preferences.value = SearchPreferences(pedestrianProfile = PedestrianProfile.WHEELCHAIR)

    // L'onglet consulté est rechargé avec les nouveaux réglages ; les trois autres, jamais
    // ouverts, restent muets (SPEC.md § 7.3).
    assertEquals(listOf(JourneyCategory.TRANSIT, JourneyCategory.TRANSIT), repository.categories())
    assertEquals("WHEELCHAIR", PlanQueryBuilder.build(repository.queries.last())["pedestrianProfile"])
    assertEquals(1, model.uiState.value.tabs.size)
  }

  @Test
  fun `changer un reglage perime les onglets deja charges`() = runTest {
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.BIKE)
    assertEquals(2, model.uiState.value.tabs.size)

    preferences.value = SearchPreferences(allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE))

    // Les trajets de l'onglet Vélo avaient été calculés avec les trottinettes : ils ne peuvent
    // pas rester affichés. Seul l'onglet consulté est rechargé.
    assertEquals(setOf(JourneyCategory.BIKE), model.uiState.value.tabs.keys)
    assertEquals(
      listOf(JourneyCategory.TRANSIT, JourneyCategory.BIKE, JourneyCategory.BIKE),
      repository.categories(),
    )
  }

  @Test
  fun `reemettre les memes reglages ne relance aucune requete`() = runTest {
    viewModel()
    completeSearch()

    preferences.value = SearchPreferences()

    assertEquals(listOf(JourneyCategory.TRANSIT), repository.categories())
  }

  @Test
  fun `sans recherche en cours, changer un reglage n emet rien`() = runTest {
    val model = viewModel()

    preferences.value = SearchPreferences(requireBikeTransport = true)

    assertTrue(repository.calls.isEmpty())
    assertFalse(model.uiState.value.open)
  }
}
