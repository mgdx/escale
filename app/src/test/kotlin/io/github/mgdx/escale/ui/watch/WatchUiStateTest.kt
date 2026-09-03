package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.core.model.JourneyWatchLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** La configuration en cours d'édition, telle qu'elle sera enregistrée (SPEC.md § 5.5.1). */
class WatchUiStateTest {

  private val date: LocalDate = LocalDate.of(2026, 9, 7)

  @Test
  fun `sans jour coche, la configuration vaut pour une date unique`() {
    val state = WatchUiState(time = LocalTime.of(8, 10), singleDate = date)

    assertFalse(state.schedule.isRecurring)
    assertEquals(date, state.schedule.date)
    assertEquals(LocalTime.of(8, 10), state.schedule.departureTime)
  }

  @Test
  fun `avec des jours coches, la configuration est recurrente et n'a pas de date`() {
    val state = WatchUiState(
      time = LocalTime.of(8, 10),
      days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
      singleDate = date,
    )

    assertTrue(state.schedule.isRecurring)
    assertNull(state.schedule.date)
  }

  @Test
  fun `la limite de cinq bloque un sixieme trajet, mais pas la reconfiguration d'un surveille`() {
    val full = WatchUiState(watchedCount = JourneyWatchLimit.MAX_WATCHED)

    assertTrue(full.limitBlocking)
    assertFalse(full.copy(watched = true).limitBlocking)
    assertFalse(full.copy(watchedCount = JourneyWatchLimit.MAX_WATCHED - 1).limitBlocking)
  }
}
