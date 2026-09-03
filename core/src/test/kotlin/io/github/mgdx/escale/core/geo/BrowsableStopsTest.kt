package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le parcours des arrêts affichés (SPEC.md § 9).
 *
 * La liste ne vaut que si elle dit **exactement** ce que la carte montre : un arrêt de plus la
 * rendrait fausse, un arrêt de moins la rendrait inutile.
 */
class BrowsableStopsTest {

  private val area = BoundingBox(min = LatLon(48.85, 2.34), max = LatLon(48.87, 2.36))

  private fun marker(id: String, lat: Double, lon: Double, tier: ZoomTier = ZoomTier.ALL_STOPS) =
    StopMarker(id = id, name = id, point = LatLon(lat, lon), tier = tier, mode = TransitMode.BUS)

  @Test
  fun `un arrêt hors de l'emprise visible n'est pas listé`() {
    // L'emprise demandée au serveur est celle de l'écran élargie de 30 % (SPEC.md § 5.7, règle 3) :
    // ce tiers hors champ n'est pas « à l'écran », et la liste ne doit pas le prétendre.
    val stops = browsableStops(
      markers = listOf(marker("dedans", 48.86, 2.35), marker("dehors", 48.90, 2.35)),
      visibleArea = area,
      zoom = 14.0,
    )

    assertEquals(listOf("dedans"), stops.map { it.id })
  }

  @Test
  fun `un arrêt dont la couche est éteinte à ce zoom n'est pas listé`() {
    // Au zoom 11, seules les gares se voient : lister les arrêts de bus ferait mentir la liste.
    val stops = browsableStops(
      markers = listOf(
        marker("gare", 48.86, 2.35, tier = ZoomTier.MAJOR_STATIONS),
        marker("bus", 48.861, 2.351, tier = ZoomTier.ALL_STOPS),
      ),
      visibleArea = area,
      zoom = 11.5,
    )

    assertEquals(listOf("gare"), stops.map { it.id })
  }

  @Test
  fun `les deux paliers cohabitent dès que le zoom les allume tous les deux`() {
    val stops = browsableStops(
      markers = listOf(
        marker("gare", 48.86, 2.35, tier = ZoomTier.MAJOR_STATIONS),
        marker("bus", 48.861, 2.351, tier = ZoomTier.ALL_STOPS),
      ),
      visibleArea = area,
      zoom = 14.0,
    )

    assertEquals(2, stops.size)
  }

  @Test
  fun `le plus proche du centre est annoncé le premier`() {
    // Qui ne voit pas la carte ne peut pas viser : l'ordre utile est celui de la proximité au point
    // que la caméra regarde, pas celui de la réponse du serveur.
    val stops = browsableStops(
      markers = listOf(
        marker("loin", 48.869, 2.359),
        marker("centre", 48.860, 2.350),
        marker("moyen", 48.864, 2.353),
      ),
      visibleArea = area,
      zoom = 14.0,
    )

    assertEquals(listOf("centre", "moyen", "loin"), stops.map { it.id })
  }

  @Test
  fun `la liste est plafonnée`() {
    val markers = (0 until 100).map { marker("arret$it", 48.851 + it * 0.0001, 2.351) }

    val stops = browsableStops(markers = markers, visibleArea = area, zoom = 14.0, limit = 5)

    assertEquals(5, stops.size)
    // Le plafond retient les plus proches du centre, pas les cinq premiers de la réponse.
    assertTrue(stops.none { it.id == "arret0" })
  }

  @Test
  fun `une carte sans arrêt rend une liste vide, et non une erreur`() {
    assertTrue(browsableStops(markers = emptyList(), visibleArea = area, zoom = 14.0).isEmpty())
  }
}
