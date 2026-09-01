package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class FormattedDurationTest {

  @Test
  fun `une duree nulle est signalee comme telle`() {
    val formatted = FormattedDuration.of(Duration.ZERO)
    assertEquals(0L, formatted.hours)
    assertEquals(0L, formatted.minutes)
    assertTrue(formatted.isZero)
  }

  @Test
  fun `les secondes sont arrondies a la minute la plus proche`() {
    assertEquals(1L, FormattedDuration.of(Duration.ofSeconds(31)).minutes)
    assertEquals(0L, FormattedDuration.of(Duration.ofSeconds(29)).minutes)
    assertEquals(2L, FormattedDuration.of(Duration.ofSeconds(90)).minutes)
  }

  @Test
  fun `une duree superieure a une heure est decoupee`() {
    val formatted = FormattedDuration.of(Duration.ofMinutes(83))
    assertEquals(1L, formatted.hours)
    assertEquals(23L, formatted.minutes)
    assertFalse(formatted.isZero)
  }

  @Test
  fun `une duree de plus d un jour reste comptee en heures`() {
    val formatted = FormattedDuration.of(Duration.ofHours(26).plusMinutes(5))
    assertEquals(26L, formatted.hours)
    assertEquals(5L, formatted.minutes)
  }

  @Test
  fun `une duree negative est signalee et rendue en valeur absolue`() {
    val formatted = FormattedDuration.of(Duration.ofMinutes(-7))
    assertTrue(formatted.negative)
    assertEquals(0L, formatted.hours)
    assertEquals(7L, formatted.minutes)
  }
}
