package io.github.mgdx.escale.core.follow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowLimitsTest {

  private val journey = referenceJourney()

  @Test
  fun `le suivi peut demarrer dans l'heure qui precede le depart`() {
    assertTrue(FollowLimits.canStart(journey, now = at(-59)))
    assertFalse(FollowLimits.canStart(journey, now = at(-60)))
    assertFalse(FollowLimits.canStart(journey, now = at(-120)))
  }

  @Test
  fun `le suivi peut demarrer en cours de route, jamais apres l'arrivee`() {
    assertTrue(FollowLimits.canStart(journey, now = at(15)))
    assertTrue(FollowLimits.canStart(journey, now = at(39)))
    assertFalse(FollowLimits.canStart(journey, now = at(40)))
  }

  @Test
  fun `un trajet sans transport en commun ne se suit pas`() {
    val walkOnly = journey(legs = listOf(walk("A", "B", 0, 20)))
    assertFalse(FollowLimits.canStart(walkOnly, now = at(0)))
  }

  @Test
  fun `un trajet avec une portion annulee ne se suit pas`() {
    val cancelled =
      journey(legs = listOf(transit(from = "A", to = "B", startMinute = 0, endMinute = 10, cancelled = true)))
    assertFalse(FollowLimits.canStart(cancelled, now = at(0)))
  }

  @Test
  fun `la borne de fin est trente minutes apres l'arrivee`() {
    assertEquals(at(70), FollowLimits.deadline(FollowPlan.of(journey)))
  }
}
