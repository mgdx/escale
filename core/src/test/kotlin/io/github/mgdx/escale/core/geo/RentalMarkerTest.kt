package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPointKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Les deux paliers du libre-service (tableau de SPEC.md § 5.7) et la lecture d'une réponse de
 * `/api/v1/rentals`.
 *
 * Aucune coordonnée n'est journalisée nulle part dans ce calcul, et ces cas ne le vérifient pas
 * autrement qu'en n'ayant aucun journal à lire : `:core` n'a pas accès à `Log`.
 */
class RentalMarkerTest {

  private val retrieved = Instant.parse("2026-09-01T10:00:00Z")

  // --- Les deux paliers ------------------------------------------------------------------------

  @Test
  fun `les stations apparaissent au zoom treize, les vehicules isoles au zoom quinze`() {
    assertEquals(ZoomTier.ALL_STOPS, RentalMarkerKind.STATION.tier)
    assertEquals(ZoomTier.POINTS_OF_INTEREST, RentalMarkerKind.VEHICLE.tier)
    assertEquals(13.0, RentalMarkerKind.STATION.tier.minZoom, 0.0)
    assertEquals(15.0, RentalMarkerKind.VEHICLE.tier.minZoom, 0.0)
  }

  @Test
  fun `aucune requete de libre-service sous le zoom treize`() {
    assertFalse(ZoomTier.forZoom(0.0).requestsRentals)
    assertFalse(ZoomTier.forZoom(10.9).requestsRentals)
    // Le zoom 11 demande déjà des arrêts, mais toujours aucune station en libre-service : les deux
    // seuils sont distincts, et les confondre ferait partir une requête pour rien.
    assertTrue(ZoomTier.forZoom(11.0).requestsStops)
    assertFalse(ZoomTier.forZoom(11.0).requestsRentals)
    assertFalse(ZoomTier.forZoom(12.9).requestsRentals)
  }

  @Test
  fun `a partir du zoom treize, le libre-service est demande`() {
    assertTrue(ZoomTier.forZoom(13.0).requestsRentals)
    assertTrue(ZoomTier.forZoom(15.0).requestsRentals)
    assertTrue(ZoomTier.forZoom(17.5).requestsRentals)
  }

  // --- Station ou véhicule isolé ---------------------------------------------------------------

  @Test
  fun `une station se dessine en station et apparait au zoom treize`() {
    val marker = rentalMarkers(
      listOf(availability(name = "Hôtel de Ville", docks = mapOf("velo" to 6), kind = RentalPointKind.STATION)),
    ).single()
    assertEquals(RentalMarkerKind.STATION, marker.kind)
    assertEquals(ZoomTier.ALL_STOPS, marker.tier)
    assertEquals(6, marker.docksAvailable)
  }

  @Test
  fun `un vehicule isole se dessine en vehicule et n'apparait qu'au zoom quinze`() {
    val marker = rentalMarkers(
      listOf(availability(name = "", docks = emptyMap(), vehicles = 1, kind = RentalPointKind.FREE_FLOATING)),
    ).single()
    assertEquals(RentalMarkerKind.VEHICLE, marker.kind)
    assertEquals(ZoomTier.POINTS_OF_INTEREST, marker.tier)
    assertEquals(0, marker.docksAvailable)
  }

  @Test
  fun `une station sans nom ni borne reste une station`() {
    // Le cas qui a fait tomber l'heuristique précédente, et il n'a rien de théorique : la fixture
    // `rentals_brussels_closed_station.json` contient une station Villo sans nom, vide et hors
    // service, et **aucun** flux relevé ne publie de bornes. Elle cochait donc les deux conditions
    // de la devinette d'alors, et se serait dessinée en véhicule en libre accès, au zoom 15 au
    // lieu du zoom 13, avec la mauvaise forme et le mauvais libellé.
    val marker = rentalMarkers(
      listOf(availability(name = "", docks = emptyMap(), vehicles = 0, kind = RentalPointKind.STATION)),
    ).single()
    assertEquals(RentalMarkerKind.STATION, marker.kind)
    assertEquals(ZoomTier.ALL_STOPS, marker.tier)
  }

  @Test
  fun `un vehicule isole reste un vehicule, meme si le flux lui donne un nom`() {
    // Symétrique du précédent : c'est la nature qui tranche, jamais un signal réinterprété.
    val marker = rentalMarkers(
      listOf(availability(name = "Vélo 12", docks = emptyMap(), kind = RentalPointKind.FREE_FLOATING)),
    ).single()
    assertEquals(RentalMarkerKind.VEHICLE, marker.kind)
  }

  @Test
  fun `chaque nature de point a sa famille de marqueur, et une seule`() {
    assertEquals(RentalMarkerKind.STATION, RentalMarkerKind.of(RentalPointKind.STATION))
    assertEquals(RentalMarkerKind.VEHICLE, RentalMarkerKind.of(RentalPointKind.FREE_FLOATING))
  }

  // --- Ce que le marqueur retient de la réponse ------------------------------------------------

  @Test
  fun `le marqueur reprend l'identifiant, le nom, le compte et le lien de l'exploitant`() {
    val marker = rentalMarkers(
      listOf(
        availability(
          id = "velib-42",
          name = "Rivoli",
          vehicles = 7,
          docks = mapOf("velo" to 4, "velo-electrique" to 1),
          uri = "velib://station/42",
        ),
      ),
    ).single()

    assertEquals("velib-42", marker.id)
    assertEquals("Rivoli", marker.name)
    assertEquals(7, marker.vehiclesAvailable)
    assertEquals(5, marker.docksAvailable)
    assertEquals("velib://station/42", marker.rentalUriAndroid)
  }

  @Test
  fun `un lien vide vaut un lien absent`() {
    assertNull(rentalMarkers(listOf(availability(uri = "   "))).single().rentalUriAndroid)
    assertNull(rentalMarkers(listOf(availability(uri = null))).single().rentalUriAndroid)
  }

  @Test
  fun `une station hors service le reste dans son marqueur`() {
    val marker = rentalMarkers(listOf(availability(renting = false, returning = false))).single()
    assertFalse(marker.isRenting)
    assertFalse(marker.isReturning)
  }

  @Test
  fun `l'ordre de la reponse est conserve`() {
    val markers = rentalMarkers(
      listOf(availability(id = "a"), availability(id = "b"), availability(id = "c")),
    )
    assertEquals(listOf("a", "b", "c"), markers.map { it.id })
  }

  // --- Le dessin, choisi sans jamais dépendre d'une couleur ------------------------------------

  @Test
  fun `une station multi-vehicules se dessine par le plus leger`() {
    assertEquals(
      RentalFormFactor.BICYCLE,
      principalFormFactor(listOf(RentalFormFactor.CAR, RentalFormFactor.BICYCLE)),
    )
    assertEquals(
      RentalFormFactor.SCOOTER_STANDING,
      principalFormFactor(listOf(RentalFormFactor.MOPED, RentalFormFactor.SCOOTER_STANDING)),
    )
  }

  @Test
  fun `sans type publie, le marqueur n'impose aucun dessin`() {
    assertNull(principalFormFactor(emptyList()))
    assertNull(rentalMarkers(listOf(availability())).single().formFactor)
  }

  // --- Le regroupement, par famille ------------------------------------------------------------

  @Test
  fun `le regroupement s'allume au-dela de deux cents points`() {
    assertFalse(shouldClusterRentals(MapLoadRules.CLUSTER_THRESHOLD))
    assertTrue(shouldClusterRentals(MapLoadRules.CLUSTER_THRESHOLD + 1))
  }

  @Suppress("LongParameterList")
  private fun availability(
    id: String = "station",
    name: String = "Station",
    vehicles: Int = 3,
    docks: Map<String, Int> = mapOf("velo" to 2),
    uri: String? = null,
    renting: Boolean = true,
    returning: Boolean = true,
    formFactors: List<RentalFormFactor> = emptyList(),
    kind: RentalPointKind = RentalPointKind.STATION,
  ) = RentalAvailability(
    stationId = id,
    name = name,
    coordinates = LatLon(48.85, 2.35),
    numVehiclesAvailable = vehicles,
    vehicleDocksAvailable = docks,
    isRenting = renting,
    isReturning = returning,
    formFactors = formFactors,
    rentalUriAndroid = uri,
    retrievedAt = retrieved,
    kind = kind,
  )
}
