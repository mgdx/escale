package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundsTest {

  @Test
  fun `une liste vide n a pas d emprise`() {
    assertNull(boundingBoxOf(emptyList()))
  }

  @Test
  fun `l emprise d un point unique est degeneree`() {
    val box = boundingBoxOf(listOf(LatLon(48.85, 2.35)))
    assertEquals(BoundingBox(LatLon(48.85, 2.35), LatLon(48.85, 2.35)), box)
  }

  @Test
  fun `l emprise englobe tous les points`() {
    val box = boundingBoxOf(
      listOf(
        LatLon(48.85, 2.35),
        LatLon(48.90, 2.20),
        LatLon(48.80, 2.50),
      ),
    )
    assertEquals(BoundingBox(LatLon(48.80, 2.20), LatLon(48.90, 2.50)), box)
  }

  @Test
  fun `expandBy elargit de trente pour cent de part et d autre`() {
    val box = BoundingBox(LatLon(48.0, 2.0), LatLon(49.0, 3.0))
    val enlarged = box.expandBy(0.3)
    assertEquals(47.7, enlarged.min.lat, 1e-9)
    assertEquals(1.7, enlarged.min.lon, 1e-9)
    assertEquals(49.3, enlarged.max.lat, 1e-9)
    assertEquals(3.3, enlarged.max.lon, 1e-9)
  }

  @Test
  fun `expandBy ne franchit pas les poles`() {
    val box = BoundingBox(LatLon(-89.0, -179.0), LatLon(89.0, 179.0))
    val enlarged = box.expandBy(1.0)
    assertEquals(-90.0, enlarged.min.lat, 1e-9)
    assertEquals(90.0, enlarged.max.lat, 1e-9)
    assertEquals(-180.0, enlarged.min.lon, 1e-9)
    assertEquals(180.0, enlarged.max.lon, 1e-9)
  }

  @Test
  fun `contains repond sur les bords`() {
    val box = BoundingBox(LatLon(48.0, 2.0), LatLon(49.0, 3.0))
    assertTrue(box.contains(LatLon(48.0, 2.0)))
    assertTrue(box.contains(LatLon(48.5, 2.5)))
    assertFalse(box.contains(LatLon(47.9, 2.5)))
  }
}
