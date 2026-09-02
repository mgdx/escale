package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les paramètres de `/api/v6/map/stops` et `/api/v6/stop` (SPEC.md § 5.7). */
class StopsQueryBuilderTest {

  private val area = BoundingBox(min = LatLon(48.8534, 2.335), max = LatLon(48.862, 2.35))

  @Test
  fun `l'emprise part en deux couples latitude virgule longitude`() {
    val parameters = StopsQueryBuilder.mapStops(area, ZoomTier.MAJOR_STATIONS.stopModes, grouped = true)
    assertEquals("48.8534,2.335", parameters["min"])
    assertEquals("48.862,2.35", parameters["max"])
  }

  @Test
  fun `les quais d'une meme gare sont regroupes par le serveur`() {
    // SPEC.md § 5.7 : « grouped=true pour que le serveur regroupe lui-même les quais d'une même gare ».
    assertEquals("true", StopsQueryBuilder.mapStops(area, emptySet(), grouped = true)["grouped"])
    assertFalse(StopsQueryBuilder.mapStops(area, emptySet(), grouped = false).containsKey("grouped"))
  }

  @Test
  fun `le palier du zoom 11 ne demande que les modes ferres lourds`() {
    val modes = checkNotNull(StopsQueryBuilder.mapStops(area, ZoomTier.MAJOR_STATIONS.stopModes, true)["modes"])
      .split(",")
      .toSet()
    assertEquals(TransitMode.HEAVY_RAIL_MODES.map { it.name }.toSet(), modes)
  }

  @Test
  fun `le palier du zoom 13 ajoute les modes de surface sans rien retirer`() {
    val major = ZoomTier.MAJOR_STATIONS.stopModes
    val all = ZoomTier.ALL_STOPS.stopModes
    // « Chaque palier conserve les couches des paliers inférieurs : on ajoute, on ne remplace pas. »
    assertTrue(all.containsAll(major))
    assertTrue(all.contains(TransitMode.BUS))
    assertTrue(all.contains(TransitMode.TRAM))
  }

  @Test
  fun `un ensemble de modes vide est omis, jamais envoye vide`() {
    // Vide signifierait « aucun mode » sur la requête, là où l'API entend « tous les modes ».
    assertFalse(StopsQueryBuilder.mapStops(area, emptySet(), grouped = true).containsKey("modes"))
  }

  @Test
  fun `le detail d'un arret n'a besoin que de son identifiant`() {
    assertEquals(mapOf("stopId" to "IDFM:71264"), StopsQueryBuilder.stopInfo("IDFM:71264"))
  }
}
