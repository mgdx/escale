package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * La planification des trajets surveillés (SPEC.md § 5.5.1).
 *
 * Ces cas sont la seule preuve possible qu'une tâche part au bon moment : personne ne regarde
 * l'écran une heure avant un départ.
 */
class WatchPlanningTest {

  private val paris: ZoneId = ZoneId.of("Europe/Paris")

  private val weekdays = WatchSchedule(
    departureTime = LocalTime.of(8, 10),
    days = setOf(
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
    ),
  )

  @Test
  fun `la tache est programmee une heure avant le depart`() {
    // Lundi 7 septembre 2026, 5 h 00 à Paris.
    val now = Instant.parse("2026-09-07T03:00:00Z")

    val run = WatchPlanning.nextRun(weekdays, after = now, now = now, zone = paris)

    assertEquals(Instant.parse("2026-09-07T06:10:00Z"), run?.departure)
    assertEquals(Instant.parse("2026-09-07T05:10:00Z"), run?.runAt)
  }

  @Test
  fun `l'occurrence du jour passee, c'est celle du lendemain ouvre qui est retenue`() {
    // Vendredi 11 septembre 2026, 9 h 00 à Paris : l'occurrence de 8 h 10 est passée.
    val now = Instant.parse("2026-09-11T07:00:00Z")

    val run = WatchPlanning.nextRun(weekdays, after = now, now = now, zone = paris)

    // Le samedi et le dimanche ne sont pas surveillés : c'est lundi.
    assertEquals(Instant.parse("2026-09-14T06:10:00Z"), run?.departure)
  }

  @Test
  fun `apres une execution, la replanification vise l'occurrence suivante et jamais la meme`() {
    val departure = Instant.parse("2026-09-07T06:10:00Z")
    // La tâche s'exécute à T − 60 : sans la borne « strictement après l'occurrence traitée », la
    // replanification retomberait sur ce même départ et la surveillance boucherait.
    val now = departure.minus(WatchPlanning.LEAD_TIME)

    val next = WatchPlanning.nextRun(weekdays, after = departure, now = now, zone = paris)

    assertEquals(Instant.parse("2026-09-08T06:10:00Z"), next?.departure)
  }

  @Test
  fun `une activation a moins d'une heure du depart s'execute aussitot`() {
    val now = Instant.parse("2026-09-07T05:40:00Z")

    val run = WatchPlanning.nextRun(weekdays, after = now, now = now, zone = paris)

    assertEquals(Instant.parse("2026-09-07T06:10:00Z"), run?.departure)
    assertEquals(now, run?.runAt)
  }

  @Test
  fun `l'heure locale ne derive pas au changement d'heure d'ete`() {
    // Le passage à l'heure d'été a lieu le dimanche 29 mars 2026 en Europe.
    val before = Instant.parse("2026-03-27T12:00:00Z")

    val run = WatchPlanning.nextRun(weekdays, after = before, now = before, zone = paris)

    // Lundi 30 mars, 8 h 10 heure de Paris — soit 06:10 UTC et non 07:10 : c'est bien l'heure
    // locale qui est fixe, et non le décalage.
    assertEquals(Instant.parse("2026-03-30T06:10:00Z"), run?.departure)
  }

  @Test
  fun `une surveillance sans recurrence vaut pour sa date, puis plus rien`() {
    val single = WatchSchedule(
      departureTime = LocalTime.of(8, 10),
      date = LocalDate.of(2026, 9, 7),
    )
    val before = Instant.parse("2026-09-06T12:00:00Z")

    assertEquals(Instant.parse("2026-09-07T06:10:00Z"), WatchPlanning.nextRun(single, before, before, paris)?.departure)

    // Une fois l'occurrence passée, il n'y a plus d'exécution : la surveillance se désactive
    // d'elle-même (SPEC.md § 5.5.1).
    val after = Instant.parse("2026-09-07T06:10:00Z")
    assertNull(WatchPlanning.nextRun(single, after, after, paris))
  }

  @Test
  fun `une surveillance sans jour ni date ne programme rien`() {
    val empty = WatchSchedule(departureTime = LocalTime.of(8, 10))
    val now = Instant.parse("2026-09-07T03:00:00Z")

    assertNull(WatchPlanning.nextRun(empty, now, now, paris))
  }

  @Test
  fun `un trajet consulte il y a moins de trente minutes n'est pas verifie`() {
    val now = Instant.parse("2026-09-07T05:10:00Z")

    assertTrue(WatchPlanning.isFresh(now.minus(Duration.ofMinutes(29)), now))
    assertTrue(WatchPlanning.isFresh(now, now))
  }

  @Test
  fun `au-dela de trente minutes, la verification a lieu`() {
    val now = Instant.parse("2026-09-07T05:10:00Z")

    assertFalse(WatchPlanning.isFresh(now.minus(WatchPlanning.FRESHNESS), now))
    assertFalse(WatchPlanning.isFresh(now.minus(Duration.ofHours(2)), now))
  }

  @Test
  fun `un trajet jamais consulte ne dispense de rien`() {
    assertFalse(WatchPlanning.isFresh(lastViewedAt = null, now = Instant.parse("2026-09-07T05:10:00Z")))
  }

  @Test
  fun `il n'y a qu'une seule reprise, puis abandon`() {
    assertTrue(WatchPlanning.shouldRetry(runAttemptCount = 0))
    assertFalse(WatchPlanning.shouldRetry(runAttemptCount = 1))
    assertFalse(WatchPlanning.shouldRetry(runAttemptCount = 2))
  }

  @Test
  fun `la reprise unique a lieu cinq minutes plus tard`() {
    assertEquals(Duration.ofMinutes(5), WatchPlanning.RETRY_DELAY)
    assertEquals(Duration.ofMinutes(60), WatchPlanning.LEAD_TIME)
    assertEquals(Duration.ofMinutes(30), WatchPlanning.FRESHNESS)
  }
}
