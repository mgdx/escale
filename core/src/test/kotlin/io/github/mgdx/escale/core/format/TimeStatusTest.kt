package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le piège de SPEC.md § 5.2 : « une portion sans donnée temps réel est affichée sans coloration ni
 * "à l'heure" ». C'est ce que ces tests verrouillent.
 */
class TimeStatusTest {

  @Test
  fun `une portion sans temps reel n a ni retard ni mention a l heure`() {
    val status = TimeStatus.of(transit(afterMinutes = 0, minutes = 30, realTime = false))
    assertNull(status.departure)
    assertNull(status.arrival)
    assertFalse(status.hasRealTime)
  }

  @Test
  fun `une portion suivie en temps reel et ponctuelle est a l heure`() {
    val status = TimeStatus.of(transit(afterMinutes = 0, minutes = 30, realTime = true))
    assertEquals(DelayQuality.ON_TIME, status.departure?.quality)
    assertTrue(status.hasRealTime)
  }

  @Test
  fun `le retard d une portion est qualifie au depart comme a l arrivee`() {
    val status = TimeStatus.of(transit(afterMinutes = 0, minutes = 30, realTime = true, delayMinutes = 7))
    assertEquals(DelayQuality.SEVERE, status.departure?.quality)
    assertEquals(DelayQuality.SEVERE, status.arrival?.quality)
  }

  @Test
  fun `une portion annulee est signalee comme telle`() {
    val status = TimeStatus.of(transit(afterMinutes = 0, minutes = 30, realTime = true, cancelled = true))
    assertTrue(status.cancelled)
  }

  @Test
  fun `un trajet suit le temps reel de sa premiere et de sa derniere portion`() {
    // Marche sans temps réel au départ, bus suivi à l'arrivée : le départ ne s'affiche pas.
    val trip = journey(legs = listOf(walk(0, 5), transit(5, 25, realTime = true, delayMinutes = 3)))
    val status = TimeStatus.of(trip)
    assertNull(status.departure)
    assertEquals(DelayQuality.SLIGHT, status.arrival?.quality)
    assertTrue(status.hasRealTime)
  }

  @Test
  fun `un trajet dont une portion est annulee est annule`() {
    val trip = journey(legs = listOf(walk(0, 5), transit(5, 25, realTime = true, cancelled = true)))
    assertTrue(TimeStatus.of(trip).cancelled)
  }
}
