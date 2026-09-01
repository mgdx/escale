package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DelayTest {

  private val scheduled: Instant = Instant.parse("2026-09-01T08:00:00Z")

  private fun delayOf(minutes: Long) =
    Delay.between(scheduled.plus(Duration.ofMinutes(minutes)), scheduled, realTime = true)

  @Test
  fun `sans donnee temps reel aucun retard n est calcule`() {
    // SPEC.md § 5.2 : une portion sans temps réel ne doit pas être annoncée « à l'heure ».
    assertNull(Delay.between(scheduled, scheduled, realTime = false))
  }

  @Test
  fun `moins d une minute d ecart vaut a l heure`() {
    assertEquals(DelayQuality.ON_TIME, delayOf(0)?.quality)
    val almostAMinute = Delay.between(scheduled.plusSeconds(59), scheduled, realTime = true)
    assertEquals(DelayQuality.ON_TIME, almostAMinute?.quality)
  }

  @Test
  fun `entre une et cinq minutes le retard est leger`() {
    assertEquals(DelayQuality.SLIGHT, delayOf(1)?.quality)
    assertEquals(DelayQuality.SLIGHT, delayOf(4)?.quality)
  }

  @Test
  fun `a partir de cinq minutes le retard est important`() {
    assertEquals(DelayQuality.SEVERE, delayOf(5)?.quality)
    assertEquals(DelayQuality.SEVERE, delayOf(42)?.quality)
  }

  @Test
  fun `une avance d au moins une minute est signalee comme telle`() {
    assertEquals(DelayQuality.EARLY, delayOf(-1)?.quality)
    assertEquals(DelayQuality.ON_TIME, delayOf(0)?.quality)
  }

  @Test
  fun `l ecart est rendu en valeur absolue pour l affichage`() {
    val delay = delayOf(-3)
    assertEquals(Duration.ofMinutes(-3), delay?.difference)
    assertEquals(3L, delay?.formatted?.minutes)
    assertEquals(true, delay?.formatted?.negative)
  }
}
