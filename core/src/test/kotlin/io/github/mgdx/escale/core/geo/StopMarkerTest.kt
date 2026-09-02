package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le tableau « contenu affiché selon le zoom » de SPEC.md § 5.7, côté dessin. */
class StopMarkerTest {

  @Test
  fun `les cinq modes du zoom 11 sont ceux de la spec, et eux seuls`() {
    // Le tableau de SPEC.md § 5.7 : RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, SUBURBAN, SUBWAY.
    TransitMode.HEAVY_RAIL_MODES.forEach { mode ->
      assertEquals(ZoomTier.MAJOR_STATIONS, stopTier(listOf(mode)))
    }
    listOf(TransitMode.TRAM, TransitMode.BUS, TransitMode.COACH, TransitMode.FERRY, TransitMode.AERIAL_LIFT)
      .forEach { mode -> assertEquals(ZoomTier.ALL_STOPS, stopTier(listOf(mode))) }
  }

  @Test
  fun `une gare desservie aussi par des bus reste une gare`() {
    val modes = listOf(TransitMode.BUS, TransitMode.SUBWAY)
    assertEquals(ZoomTier.MAJOR_STATIONS, stopTier(modes))
    assertEquals(TransitMode.SUBWAY, principalMode(modes))
  }

  @Test
  fun `un arret sans mode connu se dessine sans se deviner`() {
    assertEquals(TransitMode.OTHER, principalMode(emptyList()))
    assertEquals(ZoomTier.ALL_STOPS, stopTier(emptyList()))
  }

  @Test
  fun `le seuil de regroupement est celui de la regle 6`() {
    assertEquals(200, MapLoadRules.CLUSTER_THRESHOLD)
    assertFalse(shouldClusterStops(MapLoadRules.CLUSTER_THRESHOLD))
    assertTrue(shouldClusterStops(MapLoadRules.CLUSTER_THRESHOLD + 1))
  }

  @Test
  fun `chaque arret rend un marqueur, palier et dessin compris`() {
    val markers = stopMarkers(
      listOf(
        Stop("gare", "Gare centrale", LatLon(48.84, 2.37), listOf(TransitMode.RAIL, TransitMode.BUS)),
        Stop("arret", "Mairie", LatLon(48.85, 2.35), listOf(TransitMode.BUS)),
      ),
    )
    assertEquals(
      listOf(
        StopMarker("gare", "Gare centrale", ZoomTier.MAJOR_STATIONS, TransitMode.RAIL),
        StopMarker("arret", "Mairie", ZoomTier.ALL_STOPS, TransitMode.BUS),
      ),
      markers,
    )
  }
}
