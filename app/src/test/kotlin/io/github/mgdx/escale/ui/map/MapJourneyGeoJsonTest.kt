package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.TraceKind
import io.github.mgdx.escale.core.geo.TraceMarker
import io.github.mgdx.escale.core.geo.TraceMarkerKind
import io.github.mgdx.escale.core.geo.TraceSegment
import io.github.mgdx.escale.core.geo.TraceStroke
import io.github.mgdx.escale.core.model.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le GeoJSON du tracé (SPEC.md § 5.7, règles 6 et 7).
 *
 * Ce que la carte affichera n'est pas vérifiable en JVM ; ce qu'elle recevra l'est entièrement, et
 * c'est là que se jouent la couleur, la forme du trait et le libellé de chaque portion.
 */
class MapJourneyGeoJsonTest {

  private val paris = LatLon(48.8566, 2.3522)
  private val lyon = LatLon(45.7640, 4.8357)

  @Test
  fun `une collection vide efface le tracé sans démonter la moindre couche`() {
    // C'est tout le nettoyage de la règle 8 : la source reçoit une collection vide, les couches
    // restent en place, et rien ne s'empile d'un trajet au suivant.
    assertEquals(MapGeoJson.EMPTY, MapGeoJson.journeyLines(emptyList()))
    assertEquals(MapGeoJson.EMPTY, MapGeoJson.journeyMarkers(emptyList()))
    assertTrue(features(MapGeoJson.EMPTY).isEmpty())
  }

  @Test
  fun `une portion devient une LineString en longitude puis latitude`() {
    val json = MapGeoJson.journeyLines(listOf(segment(points = listOf(paris, lyon))))
    val geometry = features(json).single().jsonObject.getValue("geometry").jsonObject
    assertEquals("LineString", geometry.getValue("type").jsonPrimitive.content)
    val coordinates = geometry.getValue("coordinates").jsonArray
    assertEquals(2, coordinates.size)
    // RFC 7946 § 3.1.1 : longitude d'abord.
    assertEquals(paris.lon, coordinates[0].jsonArray[0].jsonPrimitive.content.toDouble(), 0.0)
    assertEquals(paris.lat, coordinates[0].jsonArray[1].jsonPrimitive.content.toDouble(), 0.0)
  }

  @Test
  fun `la couleur de la ligne est reprise telle quelle`() {
    val json = MapGeoJson.journeyLines(listOf(segment(color = "#4DBD38", textColor = "#000000")))
    val properties = properties(json).single()
    assertEquals("#4DBD38", properties.getValue(MapGeoJson.PROPERTY_COLOR).jsonPrimitive.content)
    assertEquals("#000000", properties.getValue(MapGeoJson.PROPERTY_TEXT_COLOR).jsonPrimitive.content)
  }

  @Test
  fun `sans couleur de réseau, celle du mode prend le relais`() {
    val json = MapGeoJson.journeyLines(listOf(segment(kind = TraceKind.SUBWAY)))
    val properties = properties(json).single()
    assertEquals(
      TracePalette.colorOf(TraceKind.SUBWAY),
      properties.getValue(MapGeoJson.PROPERTY_COLOR).jsonPrimitive.content,
    )
    // Aucune couleur de texte : la couche retombera sur celle du thème.
    assertNull(properties[MapGeoJson.PROPERTY_TEXT_COLOR])
  }

  @Test
  fun `chaque famille de mode a sa couleur, et deux familles n'en partagent pas`() {
    val colors = TraceKind.entries.map { TracePalette.colorOf(it) }
    assertEquals(colors.size, colors.toSet().size)
    assertTrue(colors.all { it.matches(Regex("^#[0-9A-F]{6}$")) })
  }

  @Test
  fun `la forme du trait voyage avec la portion, pour que chaque couche filtre la sienne`() {
    val json = MapGeoJson.journeyLines(
      listOf(
        segment(stroke = TraceStroke.DOTTED),
        segment(stroke = TraceStroke.SOLID),
        segment(stroke = TraceStroke.APPROXIMATE),
      ),
    )
    assertEquals(
      listOf("DOTTED", "SOLID", "APPROXIMATE"),
      properties(json).map { it.getValue(MapGeoJson.PROPERTY_STROKE).jsonPrimitive.content },
    )
  }

  @Test
  fun `le libellé de la ligne accompagne son tracé`() {
    val json = MapGeoJson.journeyLines(listOf(segment(label = "RER B")))
    assertEquals("RER B", properties(json).single().getValue(MapGeoJson.PROPERTY_LABEL).jsonPrimitive.content)
  }

  @Test
  fun `les marqueurs portent leur nature et le nom de leur lieu`() {
    val json = MapGeoJson.journeyMarkers(
      listOf(
        TraceMarker(paris, TraceMarkerKind.ORIGIN, "Bastille"),
        TraceMarker(lyon, TraceMarkerKind.DESTINATION, "Part-Dieu"),
      ),
    )
    val properties = properties(json)
    assertEquals(
      listOf("ORIGIN", "DESTINATION"),
      properties.map {
        it.getValue(MapGeoJson.PROPERTY_KIND).jsonPrimitive.content
      },
    )
    assertEquals(
      listOf("Bastille", "Part-Dieu"),
      properties.map {
        it.getValue(MapGeoJson.PROPERTY_LABEL).jsonPrimitive.content
      },
    )
  }

  @Test
  fun `aucune coordonnée ne se retrouve ailleurs que dans la géométrie`() {
    // SPEC.md § 8 et § 11 : rien de ce qui localise l'usager ne sort de la source de la carte.
    val json = MapGeoJson.journeyMarkers(listOf(TraceMarker(paris, TraceMarkerKind.ORIGIN, "Bastille")))
    assertFalse(properties(json).single().values.any { it.jsonPrimitive.content.contains("48.85") })
  }

  private fun segment(
    points: List<LatLon> = listOf(paris, lyon),
    kind: TraceKind = TraceKind.BUS,
    stroke: TraceStroke = TraceStroke.SOLID,
    color: String? = null,
    textColor: String? = null,
    label: String = "",
  ) = TraceSegment(points = points, kind = kind, stroke = stroke, color = color, textColor = textColor, label = label)

  private fun features(json: String): JsonArray =
    Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray

  private fun properties(json: String): List<JsonObject> =
    features(json).map { it.jsonObject.getValue("properties").jsonObject }
}
