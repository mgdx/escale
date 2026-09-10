package io.github.mgdx.escale.ui.detail

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.favorites.FakeFavoritesRepository
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

  private val rentals = RecordingRentalsRepository()

  private val selection = SelectedJourneyStore()

  private val savedState = SavedStateHandle()

  private val fixedNow = Instant.parse("2026-09-01T09:30:00Z")

  /** L'horloge du ViewModel, avancée à la main pour éprouver la règle de fraîcheur (§ 7.4). */
  private var clock = fixedNow

  @Test
  fun `sans trajet choisi, l'ecran se referme et n'emet aucune requete`() {
    val viewModel = viewModel()

    assertTrue(viewModel.uiState.value.closed)
    assertTrue(repository.refreshCalls.isEmpty())
    assertTrue(repository.planCalls.isEmpty())
  }

  // --- Retour après la mort du processus ---------------------------------------------------------

  @Test
  fun `apres la mort du processus, la fiche se rouvre sur le meme trajet, en une requete`() {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1"))
    viewModel()

    // Le processus meurt : le magasin en mémoire s'en va, l'état sauvegardé de l'entrée de
    // navigation reste. C'est exactement ce que fait `am kill` suivi d'un retour à l'application.
    selection.select(null)
    repository.refreshCalls.clear()
    val detailed = journeyOf("id-1", legs = listOf(walkLeg(0, 6), transitLeg(6, 20)))
    repository.refreshAnswer = Outcome.Success(detailed)

    val restored = viewModel()

    assertFalse(restored.uiState.value.closed)
    assertEquals(detailed, restored.uiState.value.journey)
    assertTrue(restored.uiState.value.detailed)
    // Une seule requête, et c'est celle de l'écran consulté (SPEC.md § 7).
    assertEquals(listOf("id-1" to true), repository.refreshCalls)
    assertTrue(repository.planCalls.isEmpty())
    // Le trajet est republié : la carte retrouve son tracé sans rien demander.
    assertEquals(detailed, selection.selected.value)
  }

  @Test
  fun `le trajet restitue attend sa reponse plutot que de montrer une page blanche`() {
    selection.select(journeyOf("id-1"))
    viewModel()
    selection.select(null)
    repository.refreshAnswer = Outcome.Failure(EscaleError.Timeout)

    val restored = viewModel()

    // La requête a échoué : l'écran reste ouvert, sur le bandeau et son bouton « Réessayer ».
    assertFalse(restored.uiState.value.closed)
    assertNull(restored.uiState.value.journey)
    assertEquals(EscaleError.Timeout, restored.uiState.value.error)
    assertFalse(restored.uiState.value.loading)
  }

  @Test
  fun `un identifiant perime, sans recherche a rejouer, referme la fiche restituee`() {
    selection.select(journeyOf("id-perime"))
    viewModel()
    selection.select(null)
    repository.refreshAnswer = Outcome.Failure(EscaleError.BadRequest(serverMessage = null))

    // Le trajet a disparu avec le processus : le repli de SPEC.md § 5.5.1 retient le plus proche en
    // heure de départ, et cette heure-là n'existe plus. Aucun repli n'est donc possible.
    val restored = viewModel(session = SearchSession())

    assertTrue(restored.uiState.value.closed)
    assertNull(restored.uiState.value.error)
    assertTrue(repository.planCalls.isEmpty())
  }

  @Test
  fun `un trajet sans identifiant ne se restitue pas, et l'ecran se referme comme avant`() {
    selection.select(journeyOf(null))
    viewModel()
    selection.select(null)
    repository.refreshCalls.clear()

    val restored = viewModel()

    assertTrue(restored.uiState.value.closed)
    assertTrue(repository.refreshCalls.isEmpty())
  }

  @Test
  fun `le depliage restitue designe bien les portions du trajet redemande`() {
    selection.select(journeyOf("id-1"))
    val viewModel = viewModel()
    viewModel.onLegToggled(1)
    selection.select(null)
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1"))

    val restored = viewModel()

    assertEquals(setOf(1), restored.uiState.value.expandedLegs)
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
  fun `un point que le serveur ne nomme pas prend le libelle saisi par l'usager`() {
    selection.select(journeyOf("id-1", legs = listOf(anonymousWalkLeg(0, 20))))
    repository.refreshAnswer = Outcome.Failure(EscaleError.Timeout)

    val viewModel = viewModel()

    val legs = viewModel.uiState.value.journey?.legs.orEmpty()
    assertEquals("Bercy", legs.first().from.name)
    assertEquals("Nation", legs.last().to.name)
  }

  @Test
  fun `le trajet republie pour la carte porte lui aussi ces noms`() {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1", legs = listOf(anonymousWalkLeg(0, 20))))

    viewModel()

    assertEquals("Bercy", selection.selected.value?.legs?.first()?.from?.name)
    assertEquals("Nation", selection.selected.value?.legs?.last()?.to?.name)
  }

  @Test
  fun `sans recherche en cours, le point reste anonyme plutot que de recevoir un nom faux`() {
    selection.select(journeyOf("id-1", legs = listOf(anonymousWalkLeg(0, 20))))
    repository.refreshAnswer = Outcome.Failure(EscaleError.Timeout)

    val viewModel = viewModel(session = SearchSession())

    assertEquals("", viewModel.uiState.value.journey?.legs?.first()?.from?.name)
  }

  @Test
  fun `apres la mort du processus, l'arrivee garde le nom saisi plutot qu'un libelle generique`() {
    selection.select(journeyOf("id-1", legs = listOf(anonymousWalkLeg(0, 20))))
    viewModel()

    // Le processus meurt : `SearchSession` vit dans l'`AppContainer` et s'en va avec lui. Seul
    // l'état sauvegardé de l'entrée de navigation subsiste, d'où le `SavedStateHandle` conservé.
    selection.select(null)
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1", legs = listOf(anonymousWalkLeg(0, 20))))

    val restored = viewModel(session = SearchSession())

    val legs = restored.uiState.value.journey?.legs.orEmpty()
    assertEquals("Bercy", legs.first().from.name)
    // C'est le défaut relevé à la recette : cette ligne affichait « Arrivée » là où elle affichait
    // « Nation » un instant plus tôt.
    assertEquals("Nation", legs.last().to.name)
    // Le trajet republié pour la carte porte les mêmes noms.
    assertEquals("Nation", selection.selected.value?.legs?.last()?.to?.name)
  }

  @Test
  fun `apres la mort du processus, l'etoile enregistre le couple cherche au lieu d'echouer`() = runBlocking {
    selection.select(journeyOf("id-1"))
    viewModel()

    selection.select(null)
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1"))
    val restored = viewModel(session = SearchSession())

    restored.onToggleFavorite()

    val favori = favorites.journeys.first().single()
    assertEquals("Bercy", favori.from.name)
    assertEquals("Nation", favori.to.name)
    assertEquals(DetailMessage.FAVORITE_ADDED, restored.uiState.value.message)
    assertEquals(favori.id, restored.uiState.value.favoriteId)
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

  // --- Retour au premier plan, SPEC.md § 7.4 ---------------------------------------------------

  @Test
  fun `au retour au premier plan, un detail de moins de 60 secondes n'est pas rafraichi`() {
    selection.select(journeyOf("id-1"))
    val viewModel = viewModel()
    assertEquals(1, repository.refreshCalls.size)

    clock = clock.plusSeconds(59)
    viewModel.onForeground()

    assertEquals(1, repository.refreshCalls.size)
  }

  @Test
  fun `au retour au premier plan, un detail de plus de 60 secondes est rafraichi`() {
    selection.select(journeyOf("id-1"))
    val viewModel = viewModel()

    clock = clock.plusSeconds(61)
    viewModel.onForeground()

    // Un seul rafraîchissement de plus : la règle est arithmétique, elle ne boucle pas.
    assertEquals(2, repository.refreshCalls.size)
    viewModel.onForeground()
    assertEquals(2, repository.refreshCalls.size)
  }

  // --- Libre-service (SPEC.md § 5.3) -------------------------------------------------------------

  @Test
  fun `tant que la portion partagee n'est pas depliee, aucune disponibilite n'est demandee`() {
    openRentalJourney()

    assertTrue("SPEC.md § 7 : pas de requête dont on n'affiche pas encore la réponse", rentals.calls.isEmpty())
  }

  @Test
  fun `deplier une portion partagee interroge la station de prise et celle de retour`() {
    val viewModel = openRentalJourney()

    viewModel.onLegToggled(0)

    // Deux stations, deux questions : « combien de véhicules » d'un côté, « combien de places
    // libres » de l'autre. Les confondre annoncerait les vélos de la station d'arrivée.
    assertEquals(listOf(PICKUP_POINT, DROPOFF_POINT), rentals.calls)
  }

  @Test
  fun `la station retenue est celle que la portion nomme, pas la plus proche`() {
    val viewModel = openRentalJourney(
      stations = listOf(
        availability("nextbike Hauptbahnhof", PICKUP_POINT, vehicles = 3, retrievedAt = fixedNow),
        availability(PICKUP_STATION, PICKUP_POINT, vehicles = 13, retrievedAt = fixedNow),
      ),
    )

    viewModel.onLegToggled(0)

    assertEquals(13, viewModel.uiState.value.rentals[0]?.pickup?.numVehiclesAvailable)
  }

  @Test
  fun `un vehicule en free-floating n'emet aucune requete`() {
    val leg = rentalLeg(0, 12, pickupName = null, dropoffName = null)
    val viewModel = openRentalJourney(leg = leg)

    viewModel.onLegToggled(0)

    assertTrue("sans station, il n'y a pas de disponibilité à demander", rentals.calls.isEmpty())
    assertNull(viewModel.uiState.value.rentals[0])
  }

  @Test
  fun `replier une portion ne redemande rien`() {
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    val afterOpening = rentals.calls.size

    viewModel.onLegToggled(0)

    assertEquals(afterOpening, rentals.calls.size)
  }

  @Test
  fun `le bouton de rafraichissement de la portion redemande la disponibilite`() {
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    val afterOpening = rentals.calls.size

    viewModel.onRentalRefresh(0)

    assertEquals(afterOpening * 2, rentals.calls.size)
  }

  @Test
  fun `deplier une portion se contente de la reponse en cache`() {
    val viewModel = openRentalJourney()

    viewModel.onLegToggled(0)

    assertEquals(listOf(false, false), rentals.freshCalls)
  }

  @Test
  fun `le bouton de rafraichissement, lui, contourne le cache`() {
    // Un bouton qui ne fait rien pendant une minute, sans le dire, laisse croire à l'usager qu'il a
    // redemandé. Le cache retient les requêtes automatiques, pas un geste délibéré (SPEC.md § 7.4).
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    rentals.freshCalls.clear()

    viewModel.onRentalRefresh(0)

    assertEquals(listOf(true, true), rentals.freshCalls)
  }

  @Test
  fun `rafraichir le trajet contourne le cache lui aussi`() {
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    rentals.freshCalls.clear()

    viewModel.onRefresh()

    assertTrue(rentals.freshCalls.isNotEmpty() && rentals.freshCalls.all { it })
  }

  @Test
  fun `rafraichir le trajet rafraichit aussi les disponibilites affichees`() {
    // Elles sont plus périssables que les horaires : les laisser telles quelles pendant que le
    // reste de l'écran se met à jour serait exactement le mensonge que l'heure de relevé évite.
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    val afterOpening = rentals.calls.size

    viewModel.onRefresh()

    assertEquals(afterOpening * 2, rentals.calls.size)
  }

  @Test
  fun `un echec laisse en place la derniere disponibilite connue`() {
    val viewModel = openRentalJourney()
    viewModel.onLegToggled(0)
    assertEquals(13, viewModel.uiState.value.rentals[0]?.pickup?.numVehiclesAvailable)

    rentals.answer = Outcome.Failure(EscaleError.NoNetwork)
    viewModel.onRentalRefresh(0)

    val state = checkNotNull(viewModel.uiState.value.rentals[0])
    assertEquals(EscaleError.NoNetwork, state.error)
    assertEquals(13, state.pickup?.numVehiclesAvailable)
    assertFalse(state.loading)
  }

  @Test
  fun `une portion en transport en commun n'interroge jamais le libre-service`() {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1"))
    val viewModel = viewModel()

    viewModel.onLegToggled(1)

    assertTrue(rentals.calls.isEmpty())
  }

  /**
   * Un écran de détail ouvert sur un trajet fait d'une seule portion en libre-service.
   *
   * La réponse du rafraîchissement porte le **même** trajet : un trajet de forme différente
   * remettrait à zéro les dépliages comme les disponibilités, et le test ne prouverait plus rien.
   */
  private fun openRentalJourney(
    leg: JourneyLeg = rentalLeg(0, 12),
    stations: List<RentalAvailability> = listOf(
      availability(PICKUP_STATION, PICKUP_POINT, vehicles = 13, retrievedAt = fixedNow),
      availability(DROPOFF_STATION, DROPOFF_POINT, vehicles = 4, retrievedAt = fixedNow),
    ),
  ): DetailViewModel {
    val journey = journeyOf("id-1", legs = listOf(leg))
    selection.select(journey)
    repository.refreshAnswer = Outcome.Success(journey)
    rentals.answer = Outcome.Success(stations)
    return viewModel()
  }

  @Test
  fun `l etoile est vide tant que le trajet n est pas en favori`() = runBlocking {
    selection.select(journeyOf("id-1"))
    repository.refreshAnswer = Outcome.Success(journeyOf("id-1"))

    val viewModel = viewModel()

    assertNull(viewModel.uiState.value.favoriteId)
  }

  @Test
  fun `mettre un trajet en favori enregistre le couple cherche, avec sa categorie`() = runBlocking {
    val journey = journeyOf("id-1")
    selection.select(journey)
    repository.refreshAnswer = Outcome.Success(journey)
    val viewModel = viewModel()

    viewModel.onToggleFavorite()

    val favori = favorites.journeys.first().single()
    // Ce sont les deux points de la recherche qui sont enregistres, avec leur identifiant d'arret
    // quand ils en ont un (docs/architecture.md § 11.3), et non les extremites du trajet affiche.
    assertEquals("Bercy", favori.from.name)
    assertEquals("Nation", favori.to.name)
    // Un rabattement a pied vers un train reste un trajet en transport en commun.
    assertEquals(JourneyCategory.TRANSIT, favori.category)
    // L'etoile porte desormais l'etat reel : elle est pleine.
    assertEquals(favori.id, viewModel.uiState.value.favoriteId)
    assertEquals(DetailMessage.FAVORITE_ADDED, viewModel.uiState.value.message)

    viewModel.onMessageShown()
    assertNull(viewModel.uiState.value.message)
  }

  @Test
  fun `l appui bascule, et trois appuis ne font pas trois favoris`() = runBlocking {
    val journey = journeyOf("id-1")
    selection.select(journey)
    repository.refreshAnswer = Outcome.Success(journey)
    val viewModel = viewModel()

    viewModel.onToggleFavorite()
    assertEquals(1, favorites.journeys.first().size)

    // Deuxieme appui : le favori est retire, pas duplique — c'est le defaut releve a l'ecran.
    viewModel.onToggleFavorite()
    assertTrue(favorites.journeys.first().isEmpty())
    assertNull(viewModel.uiState.value.favoriteId)
    assertEquals(DetailMessage.FAVORITE_REMOVED, viewModel.uiState.value.message)

    // Troisieme appui : il revient, et il n'y en a toujours qu'un.
    viewModel.onToggleFavorite()
    assertEquals(1, favorites.journeys.first().size)
    assertEquals(DetailMessage.FAVORITE_ADDED, viewModel.uiState.value.message)
  }

  @Test
  fun `l etoile refletent un favori mis ailleurs, sans passer par cet ecran`() = runBlocking {
    val journey = journeyOf("id-1")
    selection.select(journey)
    repository.refreshAnswer = Outcome.Success(journey)
    val viewModel = viewModel()

    // Le meme trajet mis en favori depuis un autre ecran : l'etoile suit, elle observe le depot.
    val id = favorites.addJourney(
      from = Location(null, "Bercy", null, LatLon(48.84, 2.38), PlaceKind.ADDRESS),
      to = Location(null, "Nation", null, LatLon(48.85, 2.39), PlaceKind.ADDRESS),
      category = JourneyCategory.TRANSIT,
      label = null,
    )

    assertEquals((id as Outcome.Success).value, viewModel.uiState.value.favoriteId)
  }

  @Test
  fun `un echec d enregistrement se dit, sans laisser croire que c est fait`() = runBlocking {
    val journey = journeyOf("id-1")
    selection.select(journey)
    repository.refreshAnswer = Outcome.Success(journey)
    favorites.failing = true
    val viewModel = viewModel()

    viewModel.onToggleFavorite()

    assertEquals(DetailMessage.FAVORITE_FAILED, viewModel.uiState.value.message)
    assertTrue(favorites.journeys.first().isEmpty())
    assertNull(viewModel.uiState.value.favoriteId)
  }

  private val favorites = FakeFavoritesRepository()

  private fun viewModel(session: SearchSession = sessionWithSearch()) = DetailViewModel(
    selection = selection,
    session = session,
    planRepository = repository,
    rentalsRepository = rentals,
    favoritesRepository = favorites,
    savedState = savedState,
    now = { clock },
  )
}
