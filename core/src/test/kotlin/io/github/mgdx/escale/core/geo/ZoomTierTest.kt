package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les paliers du tableau de SPEC.md § 5.7, seuil par seuil. */
class ZoomTierTest {

  @Test
  fun `sous le zoom 11 rien n'est demandé`() {
    assertEquals(ZoomTier.BASE_MAP_ONLY, ZoomTier.forZoom(0.0))
    assertEquals(ZoomTier.BASE_MAP_ONLY, ZoomTier.forZoom(10.999))
    assertFalse(ZoomTier.forZoom(10.999).requestsStops)
    assertTrue(ZoomTier.forZoom(10.999).stopModes.isEmpty())
  }

  @Test
  fun `le seuil de chaque palier lui appartient`() {
    assertEquals(ZoomTier.MAJOR_STATIONS, ZoomTier.forZoom(11.0))
    assertEquals(ZoomTier.ALL_STOPS, ZoomTier.forZoom(13.0))
    assertEquals(ZoomTier.POINTS_OF_INTEREST, ZoomTier.forZoom(15.0))
    assertEquals(ZoomTier.STREET_DETAIL, ZoomTier.forZoom(17.0))
  }

  @Test
  fun `entre deux seuils on reste au palier inférieur`() {
    assertEquals(ZoomTier.MAJOR_STATIONS, ZoomTier.forZoom(12.9))
    assertEquals(ZoomTier.ALL_STOPS, ZoomTier.forZoom(14.9))
    assertEquals(ZoomTier.POINTS_OF_INTEREST, ZoomTier.forZoom(16.9))
    assertEquals(ZoomTier.STREET_DETAIL, ZoomTier.forZoom(22.0))
  }

  @Test
  fun `le premier palier ne demande que les modes ferrés lourds`() {
    assertEquals(TransitMode.HEAVY_RAIL_MODES, ZoomTier.MAJOR_STATIONS.stopModes)
  }

  @Test
  fun `au zoom 13 le bus et le tram s'ajoutent sans rien retirer`() {
    val modes = ZoomTier.ALL_STOPS.stopModes
    assertTrue(modes.containsAll(TransitMode.HEAVY_RAIL_MODES))
    assertTrue(modes.contains(TransitMode.BUS))
    assertTrue(modes.contains(TransitMode.TRAM))
  }

  @Test
  fun `les paliers au-dessus de 13 demandent les mêmes modes`() {
    assertEquals(ZoomTier.ALL_STOPS.stopModes, ZoomTier.POINTS_OF_INTEREST.stopModes)
    assertEquals(ZoomTier.ALL_STOPS.stopModes, ZoomTier.STREET_DETAIL.stopModes)
  }

  @Test
  fun `ni le mode fourre-tout ni les modes de rue ne sont demandés au serveur`() {
    // `RAIL` est bien demandé : SPEC.md § 5.7 le nomme au palier 11 -> 13. `TRANSIT` et `OTHER`,
    // eux, n'ont aucun sens dans une requête d'arrêts.
    for (tier in ZoomTier.entries) {
      assertFalse(tier.stopModes.contains(TransitMode.TRANSIT))
      assertFalse(tier.stopModes.contains(TransitMode.OTHER))
      assertTrue(tier.stopModes.none { it.isStreet })
    }
  }

  @Test
  fun `les paliers sont ordonnés du moins dense au plus dense`() {
    assertTrue(ZoomTier.BASE_MAP_ONLY < ZoomTier.MAJOR_STATIONS)
    assertTrue(ZoomTier.MAJOR_STATIONS < ZoomTier.ALL_STOPS)
    assertTrue(ZoomTier.ALL_STOPS < ZoomTier.POINTS_OF_INTEREST)
    assertTrue(ZoomTier.POINTS_OF_INTEREST < ZoomTier.STREET_DETAIL)
  }
}
