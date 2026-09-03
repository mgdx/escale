package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les paramètres de `GET /api/v1/rentals`, vérifiés un à un contre `docs/motis-openapi.yaml`.
 *
 * Le point d'entrée ne rend **aucune** station tant qu'aucun filtre n'est posé : oublier `point` ou
 * `min`/`max` ne produit pas une erreur mais une réponse vide et parfaitement valide, celle des
 * seuls exploitants. C'est le genre de panne qu'on met une heure à voir.
 */
class RentalsQueryBuilderTest {

  private val station = LatLon(52.52369, 13.37076)

  @Test
  fun `autour d'un point, le filtre et le rayon partent tous les deux`() {
    val parameters = RentalsQueryBuilder.around(station, radiusMeters = 50)
    assertEquals("52.52369,13.37076", parameters["point"])
    assertEquals("50", parameters["radius"])
  }

  @Test
  fun `le rayon de la portion de trajet reste faible, comme la spec le demande`() {
    // SPEC.md § 5.3 : « `radius` faible ». Cinquante mètres autour du parvis de la gare de Berlin
    // rendent déjà trois exploitants ; deux cents en rendraient trente.
    assertEquals(50, io.github.mgdx.escale.core.repository.RentalsRepository.DEFAULT_RADIUS_METERS)
  }

  @Test
  fun `les vehicules isoles ne sont pas demandes autour d'une station`() {
    // Un véhicule en free-floating n'a ni disponibilité ni place libre à annoncer, et il pèse
    // l'essentiel de la réponse : trente-trois véhicules pour trois stations sur la même place.
    assertEquals("false", RentalsQueryBuilder.around(station, radiusMeters = 50)["withVehicles"])
  }

  @Test
  fun `les vehicules isoles sont demandes sur la carte, eux`() {
    // Ils sont le contenu même du palier de zoom ≥ 15 (SPEC.md § 5.7).
    assertNull(RentalsQueryBuilder.within(area())["withVehicles"])
  }

  @Test
  fun `les zones de georeperage ne sont jamais demandees`() {
    // Des multipolygones encodés qu'Escale ne dessine pas en v1 : les demander alourdirait chaque
    // réponse sans rien afficher de plus (SPEC.md § 7).
    assertEquals("false", RentalsQueryBuilder.around(station, radiusMeters = 50)["withZones"])
    assertEquals("false", RentalsQueryBuilder.within(area())["withZones"])
  }

  @Test
  fun `les exploitants restent demandes, eux, faute de quoi les types seraient illisibles`() {
    // `withProviders=false` supprimerait la table `vehicleTypes`, seule à traduire `446` en « vélo à
    // assistance électrique ». Le paramètre n'est donc pas envoyé : le défaut du serveur convient.
    assertFalse(RentalsQueryBuilder.around(station, radiusMeters = 50).containsKey("withProviders"))
    assertFalse(RentalsQueryBuilder.within(area()).containsKey("withProviders"))
  }

  @Test
  fun `l'emprise part en coin sud-ouest puis coin nord-est`() {
    val parameters = RentalsQueryBuilder.within(area())
    assertEquals("48.8534,2.335", parameters["min"])
    assertEquals("48.862,2.35", parameters["max"])
  }

  @Test
  fun `aucune coordonnee ne part en notation scientifique`() {
    // `0.0000123` s'écrirait `1.23E-5`, que le serveur ne sait pas lire.
    val parameters = RentalsQueryBuilder.around(LatLon(0.0000123, -0.0000456), radiusMeters = 50)
    assertEquals("0.0000123,-0.0000456", parameters["point"])
  }

  private fun area() = BoundingBox(min = LatLon(48.8534, 2.335), max = LatLon(48.862, 2.35))
}
