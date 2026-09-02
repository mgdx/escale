package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.geo.stopMarkers
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mise en GeoJSON des arrêts (SPEC.md § 5.7, règles 6 et 7).
 *
 * Ce que les couches lisent est vérifié ici, une fois : elles ne sont jamais reconstruites, et une
 * propriété mal nommée se traduirait par une carte muette plutôt que par une erreur.
 */
class MapStopsGeoJsonTest {

  private val json = Json

  @Test
  fun `chaque arret devient un point portant de quoi se dessiner et se toucher`() {
    val geoJson = MapGeoJson.stops(
      stopMarkers(
        listOf(
          Stop("IDFM:71264", "Châtelet", LatLon(48.8583, 2.3485), listOf(TransitMode.SUBWAY, TransitMode.BUS)),
        ),
      ),
    )
    val feature = geoJson.features().single()
    val properties = feature["properties"]!!.jsonObject
    assertEquals("IDFM:71264", properties[MapGeoJson.PROPERTY_STOP_ID]?.jsonPrimitive?.content)
    assertEquals("Châtelet", properties[MapGeoJson.PROPERTY_LABEL]?.jsonPrimitive?.content)
    assertEquals(ZoomTier.MAJOR_STATIONS.name, properties[MapGeoJson.PROPERTY_TIER]?.jsonPrimitive?.content)
    assertEquals(TransitMode.SUBWAY.name, properties[MapGeoJson.PROPERTY_MODE]?.jsonPrimitive?.content)
    assertEquals(StopIcon.SUBWAY.imageId, properties[MapGeoJson.PROPERTY_ICON]?.jsonPrimitive?.content)

    val geometry = feature["geometry"]!!.jsonObject
    assertEquals("Point", geometry["type"]?.jsonPrimitive?.content)
    // GeoJSON ordonne les coordonnées en longitude puis latitude (RFC 7946 § 3.1.1).
    val coordinates = geometry["coordinates"]!!.jsonArray.map { element -> element.jsonPrimitive.double }
    assertEquals(listOf(2.3485, 48.8583), coordinates)
  }

  @Test
  fun `un arret de surface porte le palier du zoom 13`() {
    val geoJson = MapGeoJson.stops(
      stopMarkers(listOf(Stop("a", "Mairie", LatLon(48.85, 2.35), listOf(TransitMode.BUS)))),
    )
    val properties = geoJson.features().single()["properties"]!!.jsonObject
    assertEquals(ZoomTier.ALL_STOPS.name, properties[MapGeoJson.PROPERTY_TIER]?.jsonPrimitive?.content)
    assertEquals(StopIcon.BUS.imageId, properties[MapGeoJson.PROPERTY_ICON]?.jsonPrimitive?.content)
  }

  @Test
  fun `aucun arret produit une collection vide, pas une source demontee`() {
    assertEquals(MapGeoJson.EMPTY, MapGeoJson.stops(emptyList()))
  }

  @Test
  fun `chaque dessin d'arret a son identifiant, et ils sont tous distincts`() {
    val ids = StopIcon.entries.map { it.imageId }
    assertEquals(ids.size, ids.toSet().size)
    assertTrue(ids.all { it.isNotBlank() })
  }

  @Test
  fun `chaque mode de transport a un dessin`() {
    // Une valeur ajoutée par une version plus récente de l'API doit tomber sur le dessin générique
    // plutôt que de faire disparaître le marqueur.
    TransitMode.entries.forEach { mode -> StopIcon.of(mode) }
    assertEquals(StopIcon.TRANSIT, StopIcon.of(TransitMode.OTHER))
    assertEquals(StopIcon.RAIL, StopIcon.of(TransitMode.HIGHSPEED_RAIL))
  }

  private fun String.features(): List<JsonObject> =
    json.parseToJsonElement(this).jsonObject["features"]!!.jsonArray.map { it.jsonObject }
}
