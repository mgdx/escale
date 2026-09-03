package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.geo.rentalMarkers
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPointKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * La mise en GeoJSON du libre-service (SPEC.md § 5.7, règles 6 et 7).
 *
 * Ce que les couches et l'infobulle lisent est vérifié ici, une fois : elles ne sont jamais
 * reconstruites, et une propriété mal nommée se traduirait par une carte muette plutôt que par une
 * erreur.
 */
class MapRentalsGeoJsonTest {

  private val json = Json

  private val retrieved = Instant.parse("2026-09-01T10:00:00Z")

  @Test
  fun `une station porte de quoi se dessiner, se toucher et remplir son infobulle`() {
    val geoJson = MapGeoJson.rentals(
      rentalMarkers(
        listOf(
          RentalAvailability(
            stationId = "velib:42",
            name = "Hôtel de Ville",
            coordinates = LatLon(48.8566, 2.3522),
            numVehiclesAvailable = 7,
            vehicleDocksAvailable = mapOf("velo" to 3, "velo-electrique" to 1),
            formFactors = listOf(RentalFormFactor.BICYCLE),
            rentalUriAndroid = "https://example.org/42",
            retrievedAt = retrieved,
          ),
        ),
      ),
    )

    val feature = geoJson.features().single()
    val properties = feature["properties"]!!.jsonObject
    assertEquals("velib:42", properties[MapGeoJson.PROPERTY_RENTAL_ID]?.jsonPrimitive?.content)
    assertEquals("Hôtel de Ville", properties[MapGeoJson.PROPERTY_LABEL]?.jsonPrimitive?.content)
    assertEquals(
      RentalMarkerKind.STATION.name,
      properties[MapGeoJson.PROPERTY_RENTAL_KIND]?.jsonPrimitive?.content,
    )
    assertEquals(
      RentalIcon.BICYCLE.imageId(RentalMarkerKind.STATION),
      properties[MapGeoJson.PROPERTY_ICON]?.jsonPrimitive?.content,
    )
    assertEquals(7, properties[MapGeoJson.PROPERTY_RENTAL_VEHICLES]?.jsonPrimitive?.int)
    assertEquals(4, properties[MapGeoJson.PROPERTY_RENTAL_DOCKS]?.jsonPrimitive?.int)
    assertTrue(properties[MapGeoJson.PROPERTY_RENTAL_RENTING]!!.jsonPrimitive.boolean)
    assertEquals("https://example.org/42", properties[MapGeoJson.PROPERTY_RENTAL_URI]?.jsonPrimitive?.content)

    // GeoJSON ordonne les coordonnées en longitude puis latitude (RFC 7946 § 3.1.1).
    val coordinates = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
    assertEquals(2.3522, coordinates[0].jsonPrimitive.double, 1e-9)
    assertEquals(48.8566, coordinates[1].jsonPrimitive.double, 1e-9)
  }

  @Test
  fun `un vehicule isole porte le dessin de sa famille, et aucun lien s'il n'en a pas`() {
    val geoJson = MapGeoJson.rentals(
      rentalMarkers(
        listOf(
          RentalAvailability(
            stationId = "trott:7",
            name = "",
            coordinates = LatLon(48.86, 2.36),
            numVehiclesAvailable = 1,
            formFactors = listOf(RentalFormFactor.SCOOTER_STANDING),
            retrievedAt = retrieved,
            // C'est la nature du point qui en fait un véhicule isolé, pas son nom vide.
            kind = RentalPointKind.FREE_FLOATING,
          ),
        ),
      ),
    )

    val properties = geoJson.features().single()["properties"]!!.jsonObject
    assertEquals(
      RentalMarkerKind.VEHICLE.name,
      properties[MapGeoJson.PROPERTY_RENTAL_KIND]?.jsonPrimitive?.content,
    )
    assertEquals(
      RentalIcon.SCOOTER.imageId(RentalMarkerKind.VEHICLE),
      properties[MapGeoJson.PROPERTY_ICON]?.jsonPrimitive?.content,
    )
    assertEquals(0, properties[MapGeoJson.PROPERTY_RENTAL_DOCKS]?.jsonPrimitive?.int)
    assertNull("pas de lien publié, pas de propriété", properties[MapGeoJson.PROPERTY_RENTAL_URI])
  }

  @Test
  fun `une station hors service le dit dans son entite, sans passer par une couleur`() {
    val geoJson = MapGeoJson.rentals(
      rentalMarkers(
        listOf(
          RentalAvailability(
            stationId = "velib:9",
            name = "Bastille",
            coordinates = LatLon(48.85, 2.37),
            numVehiclesAvailable = 0,
            vehicleDocksAvailable = mapOf("velo" to 0),
            isRenting = false,
            isReturning = false,
            retrievedAt = retrieved,
          ),
        ),
      ),
    )

    val properties = geoJson.features().single()["properties"]!!.jsonObject
    assertFalse(properties[MapGeoJson.PROPERTY_RENTAL_RENTING]!!.jsonPrimitive.boolean)
    assertFalse(properties[MapGeoJson.PROPERTY_RENTAL_RETURNING]!!.jsonPrimitive.boolean)
  }

  @Test
  fun `aucun point produit une collection vide, pas une source demontee`() {
    assertEquals(MapGeoJson.EMPTY, MapGeoJson.rentals(emptyList()))
  }

  @Test
  fun `chaque dessin de libre-service a son identifiant, et ils sont tous distincts`() {
    val ids = RentalMarkerKind.entries.flatMap { kind -> RentalIcon.entries.map { it.imageId(kind) } }
    assertEquals("une station et un véhicule du même type ont deux dessins", ids.size, ids.toSet().size)
    assertTrue(ids.all { it.isNotBlank() })
  }

  @Test
  fun `chaque dessin de libre-service se distingue de ceux des arrets`() {
    val rentalIds = RentalMarkerKind.entries.flatMap { kind -> RentalIcon.entries.map { it.imageId(kind) } }
    val stopIds = StopIcon.entries.map { it.imageId }
    assertTrue(rentalIds.none { it in stopIds })
  }

  @Test
  fun `chaque type de vehicule a un dessin`() {
    // Une valeur ajoutée par une version plus récente de l'API doit tomber sur le dessin générique
    // plutôt que de faire disparaître le marqueur.
    RentalFormFactor.entries.forEach { formFactor -> RentalIcon.of(formFactor) }
    assertEquals(RentalIcon.OTHER, RentalIcon.of(null))
    assertEquals(RentalIcon.OTHER, RentalIcon.of(RentalFormFactor.OTHER))
    assertEquals(RentalIcon.BICYCLE, RentalIcon.of(RentalFormFactor.CARGO_BICYCLE))
    assertEquals(RentalIcon.SCOOTER, RentalIcon.of(RentalFormFactor.SCOOTER_SEATED))
  }

  @Test
  fun `les deux familles ont des sources distinctes, et distinctes de celles des arrets`() {
    val sources = RentalMarkerKind.entries.flatMap { listOf(rentalSource(it), rentalClusteredSource(it)) }
    assertEquals(sources.size, sources.toSet().size)
    assertTrue(sources.none { it == STOPS_SOURCE || it == STOPS_CLUSTERED_SOURCE })
  }

  private fun String.features(): List<JsonObject> =
    json.parseToJsonElement(this).jsonObject["features"]!!.jsonArray.map { it.jsonObject }
}
