package io.github.mgdx.escale.core.geo

import app.cash.turbine.test
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les règles 1, 2 et 3 de SPEC.md § 5.7, en temps virtuel. */
class MapDataStreamTest {

  private fun box(minLat: Double, minLon: Double) =
    BoundingBox(min = LatLon(minLat, minLon), max = LatLon(minLat + 0.1, minLon + 0.1))

  private val debounce = MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS

  @Test
  fun `l'anti-rebond de 300 ms est celui de la spec`() {
    assertEquals(300L, MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS)
  }

  @Test
  fun `un déplacement suivi d'un autre n'émet qu'une requête, la dernière`() = runTest {
    val viewports = MutableSharedFlow<MapViewport>(extraBufferCapacity = 8)
    mapDataRequests(viewports).test {
      viewports.emit(MapViewport(box(48.0, 2.0), zoom = 12.0))
      // Le second arrêt de caméra arrive avant la fin de l'anti-rebond : le premier est abandonné.
      viewports.emit(MapViewport(box(49.0, 3.0), zoom = 12.0))
      expectNoEvents()

      val request = awaitItem()
      assertEquals(box(49.0, 3.0).expandBy(MapLoadRules.AREA_EXPANSION_RATIO), request.area)
      assertEquals(ZoomTier.MAJOR_STATIONS, request.tier)
      expectNoEvents()
    }
  }

  @Test
  fun `deux arrêts identiques n'émettent qu'une requête`() = runTest {
    val viewports = MutableSharedFlow<MapViewport>(extraBufferCapacity = 8)
    mapDataRequests(viewports).test {
      val viewport = MapViewport(box(48.0, 2.0), zoom = 12.0)
      viewports.emit(viewport)
      awaitItem()
      viewports.emit(viewport)
      advanceTimeByDebounce()
      expectNoEvents()
    }
  }

  @Test
  fun `un petit déplacement ne redemande rien, un grand si`() = runTest {
    val viewports = MutableSharedFlow<MapViewport>(extraBufferCapacity = 8)
    mapDataRequests(viewports).test {
      viewports.emit(MapViewport(box(48.0, 2.0), zoom = 12.0))
      awaitItem()

      // Toujours dans l'emprise élargie de 30 % : rien ne part.
      viewports.emit(MapViewport(box(48.01, 2.01), zoom = 12.0))
      advanceTimeByDebounce()
      expectNoEvents()

      // Hors de l'emprise élargie : une requête part.
      viewports.emit(MapViewport(box(50.0, 5.0), zoom = 12.0))
      assertEquals(box(50.0, 5.0).expandBy(MapLoadRules.AREA_EXPANSION_RATIO), awaitItem().area)
    }
  }

  @Test
  fun `sous le zoom 11 aucun arrêt de caméra n'émet quoi que ce soit`() = runTest {
    val viewports = MutableSharedFlow<MapViewport>(extraBufferCapacity = 8)
    mapDataRequests(viewports).test {
      viewports.emit(MapViewport(box(48.0, 2.0), zoom = 6.0))
      advanceTimeByDebounce()
      viewports.emit(MapViewport(box(10.0, 10.0), zoom = 10.9))
      advanceTimeByDebounce()
      expectNoEvents()
    }
  }

  @Test
  fun `l'emprise demandée est bien celle de l'écran élargie de 30 pour cent`() {
    val screen = box(48.0, 2.0)
    assertEquals(screen.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), screen.asRequestArea())
  }

  private suspend fun advanceTimeByDebounce() {
    delay(debounce * 2)
  }
}
