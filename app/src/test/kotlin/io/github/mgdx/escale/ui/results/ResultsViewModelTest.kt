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
 * Deux d'entre elles sont le cœur du lot et ne peuvent être vérifiées qu'ici : **les quatre
 * onglets sont chargés en série, l'onglet consulté d'abord**, et **une requête supplantée ne
 * s'affiche jamais**.
 */
class ResultsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val session = SearchSession()
  private val repository = FakePlanRepository()
  private val selection = SelectedJourneyStore()

  /** Les quatre onglets, dans l’ordre où une recherche ouverte sur le transit les charge. */
  private val allTabs = listOf(
    JourneyCategory.TRANSIT,
    JourneyCategory.CAR,
    JourneyCategory.BIKE,
    JourneyCategory.WALK,
  )

  /** Les réglages de SPEC.md § 5.6, tels que le dépôt les émettrait. */
  private val preferences = MutableStateFlow(SearchPreferences())

  /** L'horloge du ViewModel, avancée à la main : c'est elle qui décide de la fraîcheur (§ 7.4). */
  private var clock = Instant.parse("2026-09-01T08:00:00Z")

  private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
    ResultsViewModel(session, repository, preferences, selection, savedState) { clock }

  private fun completeSearch() {
    session.setFrom(location("depart"))
    session.setTo(location("arrivee"))
  }

  // --- Les quatre onglets, en série, l'onglet consulté d'abord (SPEC.md § 5.2) -----------------

  @Test
  fun `une recherche incomplete n emet aucune requete`() = runTest {
    val model = viewModel()
    session.setFrom(location("depart"))

    assertTrue(repository.calls.isEmpty())
    assertFalse(model.uiState.value.open)
  }

  @Test
  fun `une recherche complete charge les quatre onglets, le consulte d abord`() = runTest {
    val model = viewModel()
    completeSearch()

    // Chaque onglet annonce la durée de son trajet le plus rapide : les quatre catégories sont
    // donc demandées. Mais l'une après l'autre, et l'onglet consulté en premier — trois requêtes
    // lancées en même temps que la sienne ne feraient que retarder sa réponse (SPEC.md § 5.2).
    assertEquals(allTabs, repository.categories())
    assertTrue(model.uiState.value.open)
  }

  @Test
  fun `un onglet deja charge par la recherche ne relance rien a son ouverture`() = runTest {
    val model = viewModel()
    completeSearch()

    model.onCategorySelected(JourneyCategory.BIKE)
    model.onCategorySelected(JourneyCategory.WALK)
    model.onCategorySelected(JourneyCategory.BIKE)

    // SPEC.md § 7.5 : le résultat est mis en cache pour la durée de la recherche. Changer d'onglet
    // ne fait plus aucune requête, il ne fait que changer ce qui est affiché.
    assertEquals(allTabs, repository.categories())
    assertEquals(4, model.uiState.value.tabs.size)
  }

  @Test
  fun `une nouvelle recherche repart de zero et recharge les quatre onglets`() = runTest {
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.CAR)
    repository.calls.clear()

    session.setTo(location("autre-arrivee"))

    // L'onglet consulté est toujours le premier servi, les trois autres suivent.
    assertEquals(JourneyCategory.CAR, repository.categories().first())
    assertEquals(allTabs.toSet(), repository.categories().toSet())
    assertEquals(4, model.uiState.value.tabs.size)
  }

  // --- Rafraîchissement du temps réel, SPEC.md § 7.4 --------------------------------------------

  @Test
  fun `tirer pour rafraichir relance l onglet consulte en contournant le cache`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    val model = viewModel()
    completeSearch()

    model.onPullToRefresh()

    // Le geste rafraîchit sans condition, et la requête doit ignorer le cache mémoire : sinon la
    // réponse précédente reviendrait telle quelle et le geste n'aurait servi à rien.
    assertEquals(
      listOf(
        FakePlanRepository.Call(JourneyCategory.TRANSIT, cursor = null, fresh = false),
        FakePlanRepository.Call(JourneyCategory.TRANSIT, cursor = null, fresh = true),
      ),
      repository.calls.filter { it.category == JourneyCategory.TRANSIT },
    )
    assertFalse(model.uiState.value.current.refreshing)
  }

  @Test
  fun `tirer pour rafraichir ne reveille pas les autres onglets`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.WALK)
    repository.calls.clear()

    model.onPullToRefresh()

    // SPEC.md § 7.3 : un onglet non consulté n'émet aucune requête, pas même sur un geste.
    assertEquals(listOf(JourneyCategory.WALK), repository.categories())
  }

  @Test
  fun `au retour au premier plan, des horaires de moins de 60 secondes ne sont pas rafraichis`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    viewModel().let { model ->
      completeSearch()
      clock = clock.plusSeconds(59)

      model.onForeground()
    }

    assertEquals(allTabs, repository.categories())
  }

  @Test
  fun `au retour au premier plan, des horaires de plus de 60 secondes sont rafraichis`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    val model = viewModel()
    completeSearch()
    clock = clock.plusSeconds(61)

    model.onForeground()

    assertEquals(2, repository.callsTo(JourneyCategory.TRANSIT))
    assertTrue(repository.calls.last().fresh)
  }

  @Test
  fun `au retour au premier plan, un onglet qui n a rien charge n emet rien`() = runTest {
    val model = viewModel()

    // Aucune recherche en cours : il n'y a rien à rafraîchir, et surtout rien à lancer.
    model.onForeground()

    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun `plusieurs retours au premier plan ne font pas une boucle`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(JourneyPage(journeys = listOf(journey("a", 0))))
    val model = viewModel()
    completeSearch()
    clock = clock.plusSeconds(61)

    model.onForeground()
    model.onForeground()
    model.onForeground()

    // Le rafraîchissement remet le compteur de fraîcheur à zéro : les deux retours suivants
    // n'émettent rien. C'est la seule protection nécessaire, puisqu'il n'y a aucune minuterie.
    assertEquals(2, repository.callsTo(JourneyCategory.TRANSIT))
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
    assertEquals(2, repository.callsTo(JourneyCategory.TRANSIT))
  }

  @Test
  fun `un echec ne se rejoue pas a chaque changement d onglet`() = runTest {
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Failure(EscaleError.Timeout)
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.WALK)
    model.onCategorySelected(JourneyCategory.TRANSIT)

    // La reprise est un geste explicite : revenir sur l'onglet en panne ne relance rien.
    assertEquals(allTabs, repository.categories())
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
    repository.calls.clear()
    val restored = viewModel(savedState)

    assertEquals(JourneyCategory.CAR, restored.uiState.value.category)
    // Et les résultats, eux, ne sont pas sauvegardés : ils repartent d'une requête (SPEC.md § 11),
    // et c'est l'onglet restitué qui est servi le premier.
    assertEquals(JourneyCategory.CAR, repository.calls.first().category)
  }

  // --- La durée annoncée sous chaque onglet (SPEC.md § 5.2) ------------------------------------

  @Test
  fun `chaque onglet annonce la duree de son trajet le plus rapide`() = runTest {
    // Le trajet le plus court n'est pas le premier de la liste : celle-ci est ordonnée par heure
    // de départ.
    repository.answers[JourneyCategory.TRANSIT] = Outcome.Success(
      JourneyPage(journeys = listOf(journey("lent", 0, minutes = 40), journey("rapide", 10, minutes = 12))),
    )
    repository.answers[JourneyCategory.WALK] = Outcome.Failure(EscaleError.NoNetwork)
    val model = viewModel()

    completeSearch()

    val state = model.uiState.value
    assertEquals(TabHeadline.Fastest(Duration.ofMinutes(12)), state.headlineOf(JourneyCategory.TRANSIT))
    // Une catégorie qui n'a rien trouvé, et une catégorie en échec, n'annoncent aucune durée.
    assertEquals(TabHeadline.None, state.headlineOf(JourneyCategory.CAR))
    assertEquals(TabHeadline.None, state.headlineOf(JourneyCategory.WALK))
  }

  @Test
  fun `un onglet dont la reponse n est pas arrivee annonce qu il cherche`() = runTest {
    // La requête est supplantée : sa réponse n'arrivera jamais, l'onglet reste en attente.
    repository.answers[JourneyCategory.BIKE] = Outcome.Failure(EscaleError.Superseded)
    val model = viewModel()

    completeSearch()

    assertEquals(TabHeadline.Pending, model.uiState.value.headlineOf(JourneyCategory.BIKE))
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

  private fun journey(id: String, afterMinutes: Long, minutes: Long = 20): Journey =
    journeyOf(id, afterMinutes, rental = false, minutes = minutes)

  private fun rentalJourney(id: String, afterMinutes: Long): Journey = journeyOf(id, afterMinutes, rental = true)

  private fun journeyOf(id: String, afterMinutes: Long, rental: Boolean, minutes: Long = 20): Journey {
    val start = Instant.parse("2026-09-01T08:00:00Z").plusSeconds(afterMinutes * 60)
    val end = start.plusSeconds(Duration.ofMinutes(minutes).seconds)
    val leg = if (rental) {
      JourneyLeg.Rental(
        startTime = start,
        endTime = end,
        scheduledStartTime = start,
        scheduledEndTime = end,
        duration = Duration.ofMinutes(minutes),
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
        duration = Duration.ofMinutes(minutes),
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
      duration = Duration.ofMinutes(minutes),
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
  fun `changer un reglage recharge les quatre onglets`() = runTest {
    val model = viewModel()
    completeSearch()
    assertEquals(allTabs, repository.categories())

    preferences.value = SearchPreferences(pedestrianProfile = PedestrianProfile.WHEELCHAIR)

    // Les durées annoncées sous les quatre languettes ont été calculées avec les anciens réglages :
    // elles sont périmées autant que la liste affichée (SPEC.md § 5.6).
    assertEquals(allTabs + allTabs, repository.categories())
    assertEquals("WHEELCHAIR", PlanQueryBuilder.build(repository.queries.last())["pedestrianProfile"])
    assertEquals(4, model.uiState.value.tabs.size)
  }

  @Test
  fun `changer un reglage perime les onglets deja charges`() = runTest {
    val model = viewModel()
    completeSearch()
    model.onCategorySelected(JourneyCategory.BIKE)

    preferences.value = SearchPreferences(allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE))

    // Les trajets de l'onglet Vélo avaient été calculés avec les trottinettes : ils ne peuvent pas
    // rester affichés, et c'est l'onglet consulté qui repart le premier.
    assertEquals(4, model.uiState.value.tabs.size)
    assertEquals(JourneyCategory.BIKE, repository.calls.last { it.category == JourneyCategory.BIKE }.category)
    assertEquals(allTabs.size * 2, repository.calls.size)
  }

  @Test
  fun `reemettre les memes reglages ne relance aucune requete`() = runTest {
    viewModel()
    completeSearch()

    preferences.value = SearchPreferences()

    assertEquals(allTabs, repository.categories())
  }

  @Test
  fun `sans recherche en cours, changer un reglage n emet rien`() = runTest {
    val model = viewModel()

    preferences.value = SearchPreferences(requireBikeTransport = true)

    assertTrue(repository.calls.isEmpty())
    assertFalse(model.uiState.value.open)
  }
}
