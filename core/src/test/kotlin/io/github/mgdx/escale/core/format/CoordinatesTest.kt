package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CoordinatesTest {

  @Test
  fun `un point s'ecrit avec cinq decimales`() {
    assertEquals("48.85661, 2.35222", formatCoordinates(LatLon(48.856614, 2.352222)))
  }

  @Test
  fun `un point negatif garde son signe`() {
    assertEquals("-33.86880, 151.20930", formatCoordinates(LatLon(-33.8688, 151.2093)))
  }

  @Test
  fun `le separateur decimal ne suit pas la langue de l'appareil`() {
    val previous = Locale.getDefault()
    try {
      Locale.setDefault(Locale.FRENCH)
      assertEquals("48.85661, 2.35222", formatCoordinates(LatLon(48.856614, 2.352222)))
    } finally {
      Locale.setDefault(previous)
    }
  }
}
