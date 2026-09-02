package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.ORIGIN
import io.github.mgdx.escale.core.format.transit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class LegStepsTest {

  private val step = TravelStep(direction = StepDirection.LEFT, streetName = "Rue de Rivoli", distanceMeters = 40.0)

  private val end = ORIGIN.plus(Duration.ofMinutes(10))

  private val from = Place("A", LatLon(48.8, 2.3), null, null, ORIGIN, ORIGIN)

  private val to = Place("B", LatLon(48.9, 2.4), null, null, end, end)

  @Test
  fun `une portion en transport en commun n'a pas de manoeuvre`() {
    assertTrue(transit(afterMinutes = 0, minutes = 10).travelSteps.isEmpty())
  }

  @Test
  fun `les quatre portions de rue rendent leurs manoeuvres`() {
    val steps = listOf(step)
    val legs: List<JourneyLeg> = listOf(
      JourneyLeg.Walk(ORIGIN, end, ORIGIN, end, Duration.ofMinutes(10), from, to, steps = steps),
      JourneyLeg.Bike(ORIGIN, end, ORIGIN, end, Duration.ofMinutes(10), from, to, steps = steps),
      JourneyLeg.Car(ORIGIN, end, ORIGIN, end, Duration.ofMinutes(10), from, to, steps = steps),
      JourneyLeg.Rental(ORIGIN, end, ORIGIN, end, Duration.ofMinutes(10), from, to, steps = steps),
    )

    assertEquals(listOf(1, 1, 1, 1), legs.map { it.travelSteps.size })
  }
}
