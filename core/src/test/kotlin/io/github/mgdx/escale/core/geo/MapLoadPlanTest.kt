package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles 3 et 5 de SPEC.md § 5.7, et l'interdiction de requête sous le zoom 11. */
class MapLoadPlanTest {

  private fun box(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) =
    BoundingBox(min = LatLon(minLat, minLon), max = LatLon(maxLat, maxLon))

  private val screen = box(48.80, 2.30, 48.90, 2.40)

  @Test
  fun `sous le zoom 11 aucune requête n'est planifiée`() {
    assertNull(planMapLoad(loaded = null, visibleArea = screen, zoom = 10.9))
  }

  @Test
  fun `la première requête élargit l'emprise de 30 pour cent`() {
    val request = planMapLoad(loaded = null, visibleArea = screen, zoom = 12.0)
    assertNotNull(request)
    assertEquals(screen.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), request!!.area)
    assertEquals(ZoomTier.MAJOR_STATIONS, request.tier)
  }

  @Test
  fun `l'emprise élargie est trois dixièmes de part et d'autre de chaque axe`() {
    val request = planMapLoad(loaded = null, visibleArea = screen, zoom = 12.0)!!
    assertEquals(48.77, request.area.min.lat, 1e-9)
    assertEquals(48.93, request.area.max.lat, 1e-9)
    assertEquals(2.27, request.area.min.lon, 1e-9)
    assertEquals(2.43, request.area.max.lon, 1e-9)
  }

  @Test
  fun `un petit déplacement dans l'emprise déjà chargée n'émet rien`() {
    val loaded = planMapLoad(loaded = null, visibleArea = screen, zoom = 12.0)!!
    val nudged = box(48.81, 2.31, 48.91, 2.41)
    assertTrue(loaded.area.contains(nudged.min) && loaded.area.contains(nudged.max))
    assertNull(planMapLoad(loaded, nudged, zoom = 12.0))
  }

  @Test
  fun `un déplacement hors de l'emprise chargée émet une requête`() {
    val loaded = planMapLoad(loaded = null, visibleArea = screen, zoom = 12.0)!!
    val moved = box(49.50, 3.00, 49.60, 3.10)
    assertNotNull(planMapLoad(loaded, moved, zoom = 12.0))
  }

  @Test
  fun `franchir un seuil de zoom vers le bas n'émet rien`() {
    val loaded = planMapLoad(loaded = null, visibleArea = screen, zoom = 15.0)!!
    assertEquals(ZoomTier.POINTS_OF_INTEREST, loaded.tier)
    // Zoom arrière sur place : l'écran reste couvert par l'emprise élargie, on masque des couches.
    assertNull(planMapLoad(loaded, screen, zoom = 13.5))
    assertNull(planMapLoad(loaded, screen, zoom = 11.5))
  }

  @Test
  fun `franchir un seuil de zoom vers le haut émet une requête avec plus de modes`() {
    val loaded = planMapLoad(loaded = null, visibleArea = screen, zoom = 12.0)!!
    val zoomed = planMapLoad(loaded, screen, zoom = 13.5)
    assertNotNull(zoomed)
    assertEquals(ZoomTier.ALL_STOPS, zoomed!!.tier)
    assertTrue(zoomed.tier.stopModes.size > loaded.tier.stopModes.size)
  }

  @Test
  fun `descendre sous le zoom 11 n'émet rien même hors de l'emprise chargée`() {
    val loaded = planMapLoad(loaded = null, visibleArea = screen, zoom = 14.0)!!
    assertNull(planMapLoad(loaded, box(10.0, 10.0, 20.0, 20.0), zoom = 8.0))
  }
}
