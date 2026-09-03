package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** La limite de 5 trajets surveillés de SPEC.md § 5.5.1, éprouvée hors de tout écran. */
class JourneyWatchLimitTest {

  private val recurring = WatchSchedule(
    departureTime = LocalTime.of(8, 10),
    days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
  )

  @Test
  fun `la limite vaut cinq, comme l ecrit la spec`() {
    assertEquals(5, JourneyWatchLimit.MAX_WATCHED)
  }

  @Test
  fun `aucune surveillance en cours, la demande est acceptee`() {
    assertEquals(WatchDecision.ACCEPTED, JourneyWatchLimit.decide(emptyList(), journeyId = 1, recurring))
  }

  @Test
  fun `quatre surveillances en cours, la cinquieme passe`() {
    val enCours = listOf(1L, 2L, 3L, 4L)
    assertEquals(WatchDecision.ACCEPTED, JourneyWatchLimit.decide(enCours, journeyId = 5, recurring))
  }

  @Test
  fun `cinq surveillances en cours, la sixieme est refusee`() {
    val enCours = listOf(1L, 2L, 3L, 4L, 5L)
    assertEquals(WatchDecision.LIMIT_REACHED, JourneyWatchLimit.decide(enCours, journeyId = 6, recurring))
  }

  @Test
  fun `reconfigurer un trajet deja surveille reste possible a la limite`() {
    // Refuser à quelqu'un de corriger son heure de départ parce qu'il a cinq surveillances serait
    // absurde : reconfigurer n'en crée pas une sixième.
    val enCours = listOf(1L, 2L, 3L, 4L, 5L)
    assertEquals(WatchDecision.ACCEPTED, JourneyWatchLimit.decide(enCours, journeyId = 3, recurring))
  }

  @Test
  fun `une surveillance sans jour ni date ne serait jamais programmable`() {
    val horaire = WatchSchedule(departureTime = LocalTime.of(8, 10))
    assertEquals(WatchDecision.INVALID_SCHEDULE, JourneyWatchLimit.decide(emptyList(), journeyId = 1, horaire))
  }

  @Test
  fun `une date unique suffit, sans jour de la semaine`() {
    val horaire = WatchSchedule(departureTime = LocalTime.of(5, 45), date = LocalDate.of(2026, 4, 12))
    assertEquals(WatchDecision.ACCEPTED, JourneyWatchLimit.decide(emptyList(), journeyId = 1, horaire))
    assertFalse(horaire.isRecurring)
    assertTrue(recurring.isRecurring)
  }

  @Test
  fun `un horaire invalide est refuse avant meme la limite`() {
    // L'ordre compte : dire « limite atteinte » à propos d'un horaire qui n'est pas programmable
    // enverrait l'usager supprimer une autre surveillance pour rien.
    val enCours = listOf(1L, 2L, 3L, 4L, 5L)
    val horaire = WatchSchedule(departureTime = LocalTime.of(8, 10))
    assertEquals(WatchDecision.INVALID_SCHEDULE, JourneyWatchLimit.decide(enCours, journeyId = 6, horaire))
  }
}
