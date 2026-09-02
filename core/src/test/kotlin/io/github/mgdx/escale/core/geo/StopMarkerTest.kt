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
  fun `les modes du zoom 11 sont ceux de la spec, parapluie developpe`() {
    // Le tableau de SPEC.md § 5.7 écrit « RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, SUBURBAN, SUBWAY ».
    // `RAIL` est un parapluie (docs/motis-openapi.yaml, ligne 3845) : il apporte avec lui
    // NIGHT_RAIL et REGIONAL_RAIL, qui relèvent donc du zoom 11 eux aussi.
    TransitMode.HEAVY_RAIL_MODES.forEach { mode ->
      assertEquals(ZoomTier.MAJOR_STATIONS, stopTier(listOf(mode)))
    }
    listOf(TransitMode.TRAM, TransitMode.BUS, TransitMode.COACH, TransitMode.FERRY, TransitMode.AERIAL_LIFT)
      .forEach { mode -> assertEquals(ZoomTier.ALL_STOPS, stopTier(listOf(mode))) }
  }

  @Test
  fun `une gare annoncee en REGIONAL_RAIL seul releve du zoom 11, un arret de bus du zoom 13`() {
    // Cas réel : MOTIS rend « Châtelet - Les Halles » — la plus grande gare souterraine d'Europe —
    // avec le seul mode REGIONAL_RAIL. Le confondre avec un arrêt de surface la faisait disparaître
    // sous le zoom 13. Voir la fixture map_stops_rail_only.json.
    assertEquals(ZoomTier.MAJOR_STATIONS, stopTier(listOf(TransitMode.REGIONAL_RAIL)))
    assertEquals(ZoomTier.MAJOR_STATIONS, stopTier(listOf(TransitMode.NIGHT_RAIL)))
    assertEquals(ZoomTier.ALL_STOPS, stopTier(listOf(TransitMode.BUS)))
  }

  @Test
  fun `le parapluie RAIL est developpe, jamais traite comme une feuille`() {
    // Si quelqu'un abrège HEAVY_RAIL_MODES pour coller mot pour mot au § 5.7, ce cas le rattrape.
    assertTrue(TransitMode.HEAVY_RAIL_MODES.containsAll(railUmbrella))
    // Et le parapluie TRANSIT n'a rien à faire dans un filtre d'affichage.
    assertFalse(TransitMode.HEAVY_RAIL_MODES.contains(TransitMode.TRANSIT))
  }

  /**
   * Ce que `RAIL` désigne, d'après `docs/motis-openapi.yaml` ligne 3845 :
   * « `RAIL`: translates to `HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_RAIL,SUBURBAN,SUBWAY` ».
   */
  private val railUmbrella = setOf(
    TransitMode.HIGHSPEED_RAIL,
    TransitMode.LONG_DISTANCE,
    TransitMode.NIGHT_RAIL,
    TransitMode.REGIONAL_RAIL,
    TransitMode.SUBURBAN,
    TransitMode.SUBWAY,
  )

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
    val station = LatLon(48.84, 2.37)
    val townHall = LatLon(48.85, 2.35)
    val markers = stopMarkers(
      listOf(
        Stop("gare", "Gare centrale", station, listOf(TransitMode.RAIL, TransitMode.BUS)),
        Stop("arret", "Mairie", townHall, listOf(TransitMode.BUS)),
      ),
    )
    assertEquals(
      listOf(
        StopMarker("gare", "Gare centrale", station, ZoomTier.MAJOR_STATIONS, TransitMode.RAIL),
        StopMarker("arret", "Mairie", townHall, ZoomTier.ALL_STOPS, TransitMode.BUS),
      ),
      markers,
    )
  }
}
