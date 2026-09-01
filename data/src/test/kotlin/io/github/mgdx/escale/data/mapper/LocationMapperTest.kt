package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.data.dto.GeocodeMatchDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapping `Match` -> `Location` sur des réponses réelles d'`api.transitous.org`
 * (docs/architecture.md § 10).
 */
class LocationMapperTest {

  private val json = Json { ignoreUnknownKeys = true }

  private fun matches(name: String): List<GeocodeMatchDto> {
    val body = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()
    return json.decodeFromString(ListSerializer(GeocodeMatchDto.serializer()), body)
  }

  @Test
  fun `un arret porte son identifiant et les modes qu il dessert`() {
    val stop = matches("geocode_gare_de_lyon.json").toDomain().first()
    assertEquals(PlaceKind.STOP, stop.kind)
    assertEquals("Gare de Lyon", stop.name)
    assertEquals(
      "fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:73626",
      stop.id,
    )
    // SPEC.md § 5.1 : c'est cette liste qui permet d'afficher les pictogrammes de mode.
    assertEquals(
      listOf(TransitMode.REGIONAL_RAIL, TransitMode.SUBWAY, TransitMode.BUS),
      stop.servedModes,
    )
  }

  @Test
  fun `une adresse n a ni identifiant ni mode`() {
    val address = matches("geocode_rue_de_rivoli.json").toDomain().first()
    assertEquals(PlaceKind.ADDRESS, address.kind)
    assertNull(address.id)
    assertEquals(emptyList<TransitMode>(), address.servedModes)
  }

  @Test
  fun `le complement de localisation va du plus precis au plus large`() {
    val addresses = matches("geocode_rue_de_rivoli.json").toDomain()
    // Deux « Rue de Rivoli » parisiennes : seul l'arrondissement les distingue.
    assertEquals("Paris 4e Arrondissement, Paris", addresses[0].description)
    assertEquals("Paris 1er Arrondissement, Paris", addresses[1].description)
    // Ailleurs, la commune suffit et n'est pas répétée.
    assertEquals("Épinay-sur-Orge", addresses[2].description)
  }

  @Test
  fun `les coordonnees sont reprises telles quelles, notation scientifique comprise`() {
    val stop = matches("geocode_gare_de_lyon.json").toDomain().first()
    assertEquals(48.84457, stop.coordinates.lat, 1e-9)
    assertEquals(2.3751456999999996, stop.coordinates.lon, 1e-9)
  }

  @Test
  fun `un lieu remarquable est un PLACE sans identifiant`() {
    val place = matches("geocode_gare_de_lyon.json").toDomain().first { it.name == "Fnac Paris - Gare de Lyon" }
    assertEquals(PlaceKind.PLACE, place.kind)
    assertNull(place.id)
  }

  @Test
  fun `une valeur d API inconnue ne fait perdre ni le resultat ni la reponse`() {
    val locations = matches("geocode_unknown_fields.json").toDomain()
    assertEquals(2, locations.size)
    // Un mode que l'application ne connaît pas se replie sur OTHER.
    assertEquals(listOf(TransitMode.SUBWAY, TransitMode.OTHER), locations[0].servedModes)
    // Un type inconnu se replie sur la catégorie la plus neutre.
    assertEquals(PlaceKind.PLACE, locations[1].kind)
    assertNull(locations[1].description)
  }

  @Test
  fun `un geocodage inverse rend des lieux et des adresses`() {
    val locations = matches("reverse_geocode_bastille.json").toDomain()
    assertEquals(PlaceKind.PLACE, locations.first().kind)
    assertEquals("Colonne de Juillet", locations.first().name)
    assertEquals(PlaceKind.ADDRESS, locations.last().kind)
    assertEquals("Paris", locations.first().description)
  }
}
