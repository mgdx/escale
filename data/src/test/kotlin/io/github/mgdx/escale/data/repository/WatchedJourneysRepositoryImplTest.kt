package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.WatchDecision
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.address
import io.github.mgdx.escale.data.db.inMemoryDatabase
import io.github.mgdx.escale.data.db.rowCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class WatchedJourneysRepositoryImplTest {

  private lateinit var database: EscaleDatabase
  private lateinit var favorites: FavoritesRepositoryImpl
  private lateinit var repository: WatchedJourneysRepositoryImpl

  @Before
  fun setUp() {
    database = inMemoryDatabase()
    favorites = FavoritesRepositoryImpl(database) { NOW }
    repository = WatchedJourneysRepositoryImpl(database) { NOW }
  }

  @After
  fun tearDown() {
    database.close()
  }

  private suspend fun newFavoriteJourney(name: String): Long =
    (favorites.addJourney(address("Domicile"), address(name), JourneyCategory.TRANSIT, null) as Outcome.Success)
      .value

  @Test
  fun `un trajet surveille se relit avec son heure et ses jours`() = runBlocking {
    val id = newFavoriteJourney("Bureau")
    val horaire = WatchSchedule(
      departureTime = LocalTime.of(8, 10),
      days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
    )

    assertEquals(Outcome.Success(WatchDecision.ACCEPTED), repository.watch(id, horaire))

    val surveille = repository.watched.first().single()
    assertEquals(id, surveille.journeyId)
    assertEquals(horaire, surveille.schedule)
    assertEquals(NOW, surveille.createdAt)
    assertNull(surveille.itineraryId)
    assertNull(surveille.lastViewedAt)
  }

  @Test
  fun `une surveillance a date unique se relit sans jours de la semaine`() = runBlocking {
    val id = newFavoriteJourney("Aéroport")
    val horaire = WatchSchedule(departureTime = LocalTime.of(5, 45), date = LocalDate.of(2026, 4, 12))

    assertEquals(Outcome.Success(WatchDecision.ACCEPTED), repository.watch(id, horaire))
    assertEquals(horaire, repository.watched.first().single().schedule)
  }

  @Test
  fun `une surveillance sans jour ni date est refusee`() = runBlocking {
    val id = newFavoriteJourney("Bureau")
    val horaire = WatchSchedule(departureTime = LocalTime.of(8, 10))

    assertEquals(Outcome.Success(WatchDecision.INVALID_SCHEDULE), repository.watch(id, horaire))
    assertTrue(repository.watched.first().isEmpty())
  }

  @Test
  fun `au dela de cinq trajets surveilles, le sixieme est refuse`() = runBlocking {
    val horaire = WatchSchedule(departureTime = LocalTime.of(8, 10), days = setOf(DayOfWeek.MONDAY))
    repeat(5) { rang ->
      val id = newFavoriteJourney("Trajet $rang")
      assertEquals(Outcome.Success(WatchDecision.ACCEPTED), repository.watch(id, horaire))
    }

    val sixieme = newFavoriteJourney("Trajet de trop")
    assertEquals(Outcome.Success(WatchDecision.LIMIT_REACHED), repository.watch(sixieme, horaire))

    // Le refus n'écrit rien : la base en compte toujours cinq, et le favori lui-même est intact.
    assertEquals(5, database.rowCount("watched_journeys"))
    assertEquals(6, favorites.journeys.first().size)
  }

  @Test
  fun `la limite n empeche pas de reconfigurer un trajet deja surveille`() = runBlocking {
    val horaire = WatchSchedule(departureTime = LocalTime.of(8, 10), days = setOf(DayOfWeek.MONDAY))
    val ids = List(5) { rang -> newFavoriteJourney("Trajet $rang") }
    ids.forEach { repository.watch(it, horaire) }
    repository.rememberItinerary(ids.first(), "itin-42", NOW)

    val nouvelHoraire = horaire.copy(departureTime = LocalTime.of(7, 55))
    assertEquals(Outcome.Success(WatchDecision.ACCEPTED), repository.watch(ids.first(), nouvelHoraire))

    val surveille = repository.watched.first().first { it.journeyId == ids.first() }
    assertEquals(nouvelHoraire, surveille.schedule)
    // Changer l'heure ne fait pas oublier ce qui avait été observé.
    assertEquals("itin-42", surveille.itineraryId)
    assertEquals(5, database.rowCount("watched_journeys"))
  }

  @Test
  fun `l identifiant d itineraire et la derniere consultation se mettent a jour`() = runBlocking {
    val id = newFavoriteJourney("Bureau")
    repository.watch(id, WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.TUESDAY)))

    val capture = NOW.plusSeconds(120)
    assertTrue(repository.rememberItinerary(id, "itin-42", capture) is Outcome.Success)
    assertTrue(repository.markViewed(id, capture) is Outcome.Success)

    val surveille = repository.watched.first().single()
    assertEquals("itin-42", surveille.itineraryId)
    assertEquals(capture, surveille.itineraryCapturedAt)
    assertEquals(capture, surveille.lastViewedAt)

    // L'`id` d'itinéraire est expérimental : le serveur peut le rejeter, et on doit pouvoir
    // l'oublier sans perdre la surveillance (SPEC.md § 5.5.1).
    assertTrue(repository.rememberItinerary(id, null, capture) is Outcome.Success)
    assertNull(repository.watched.first().single().itineraryId)
  }

  @Test
  fun `retirer la surveillance ne supprime pas le favori`() = runBlocking {
    val id = newFavoriteJourney("Bureau")
    repository.watch(id, WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.WEDNESDAY)))

    assertTrue(repository.unwatch(id) is Outcome.Success)

    assertTrue(repository.watched.first().isEmpty())
    assertEquals(1, favorites.journeys.first().size)
  }

  @Test
  fun `supprimer un trajet favori emporte sa surveillance`() = runBlocking {
    val id = newFavoriteJourney("Bureau")
    repository.watch(id, WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.THURSDAY)))

    assertTrue(favorites.removeJourney(id) is Outcome.Success)

    // Sans la cascade, une tâche de fond resterait programmée pour un trajet qui n'existe plus.
    assertTrue(repository.watched.first().isEmpty())
    assertEquals(0, database.rowCount("watched_journeys"))
  }

  @Test
  fun `surveiller un trajet inexistant echoue au lieu de creer une ligne orpheline`() = runBlocking {
    val outcome = repository.watch(404L, WatchSchedule(LocalTime.of(8, 10), days = setOf(DayOfWeek.MONDAY)))

    assertTrue(outcome is Outcome.Failure)
    assertEquals(0, database.rowCount("watched_journeys"))
  }

  private companion object {
    val NOW: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}
