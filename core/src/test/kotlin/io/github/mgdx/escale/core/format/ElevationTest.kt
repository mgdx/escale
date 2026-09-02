package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.StepDirection
import io.github.mgdx.escale.core.model.TravelStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationTest {

  @Test
  fun `un cheminement sans donnee de terrain ne rend aucun denivele`() {
    assertNull(elevationOf(listOf(step(up = null, down = null), step(up = null, down = null))))
  }

  @Test
  fun `une liste vide ne rend aucun denivele`() {
    assertNull(elevationOf(emptyList()))
  }

  @Test
  fun `les montees et les descentes se cumulent separement`() {
    val elevation = elevationOf(listOf(step(up = 12, down = 3), step(up = 8, down = 25)))

    assertEquals(20, elevation?.upMeters)
    assertEquals(28, elevation?.downMeters)
    assertTrue(elevation?.hasRelief == true)
  }

  @Test
  fun `un serveur qui annonce un terrain plat rend un denivele nul et non une absence`() {
    val elevation = elevationOf(listOf(step(up = 0, down = 0)))

    assertEquals(Elevation(upMeters = 0, downMeters = 0), elevation)
    assertFalse(elevation!!.hasRelief)
  }

  @Test
  fun `une manoeuvre sans donnee n'empeche pas de cumuler les autres`() {
    val elevation = elevationOf(listOf(step(up = 5, down = null), step(up = null, down = null)))

    assertEquals(Elevation(upMeters = 5, downMeters = 0), elevation)
  }

  private fun step(up: Int?, down: Int?) = TravelStep(
    direction = StepDirection.CONTINUE,
    streetName = "",
    distanceMeters = 10.0,
    elevationUpMeters = up,
    elevationDownMeters = down,
  )
}
