package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.ui.map.MapPick
import io.github.mgdx.escale.ui.map.MapPickPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le point choisi par appui long sur la carte, tel qu'il entre dans la recherche. */
class MapPickLocationTest {

  private val point = LatLon(48.844314, 2.373702)

  @Test
  fun `le libelle du geocodage inverse est repris intact`() {
    val labelled = stop("de:0800:1234", "Gare de Lyon")

    val location = MapPick(MapPickPurpose.DEPARTURE, point, labelled).toLocation()

    // docs/architecture.md § 11.3 : l'identifiant d'arret et le type font tout, ils ne se
    // reconstruisent pas a partir des coordonnees affichees.
    assertEquals(labelled, location)
    assertEquals("de:0800:1234", location.id)
    assertEquals(PlaceKind.STOP, location.kind)
    assertEquals(labelled.servedModes, location.servedModes)
  }

  @Test
  fun `sans libelle, le point garde ses coordonnees pour nom`() {
    val location = MapPick(MapPickPurpose.DESTINATION, point).toLocation()

    assertEquals("48.84431, 2.37370", location.name)
    assertEquals(point, location.coordinates)
    assertEquals(PlaceKind.ADDRESS, location.kind)
    assertNull(location.id)
  }
}
