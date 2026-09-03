package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Les comptages affichés par la portion en libre-service (SPEC.md § 5.3).
 *
 * Ils méritent d'être éprouvés parce qu'ils échouent en silence : un total qui additionne les vélos
 * cargos aux vélos, ou une ventilation qui laisse tomber un type, ne lève aucune exception. Ils
 * affichent simplement un chiffre faux, sur lequel quelqu'un va fonder un déplacement.
 *
 * Les jeux de données reprennent des cas réels relevés sur `api.transitous.org` : nextbike Berlin
 * décline trois types de vélos sur une même station, mein konrad en mélange deux natures.
 */
class RentalStationsTest {

  private val at = Instant.parse("2026-09-02T05:06:25Z")

  private val bike = RentalVehicleKind(RentalFormFactor.BICYCLE, RentalPropulsionType.HUMAN)
  private val eBike = RentalVehicleKind(RentalFormFactor.BICYCLE, RentalPropulsionType.ELECTRIC_ASSIST)
  private val cargo = RentalVehicleKind(RentalFormFactor.CARGO_BICYCLE, RentalPropulsionType.HUMAN)

  // --- Le compte annoncé ------------------------------------------------------------------------

  @Test
  fun `une station qui ne detaille rien annonce son total`() {
    val station = station(total = 7)
    assertEquals(7, station.vehiclesFor(RentalFormFactor.BICYCLE))
  }

  @Test
  fun `une station melangee ne compte que le type de la portion`() {
    // Cas réel : « Stephansplatz (k PLUS Station) » annonce deux véhicules, dont un seul vélo —
    // l'autre est un vélo cargo. Annoncer « 2 vélos disponibles » enverrait quelqu'un pour rien.
    val station = station(
      total = 2,
      types = mapOf("299" to 1, "300" to 1),
      kinds = mapOf("299" to bike, "300" to cargo),
    )
    assertEquals(1, station.vehiclesFor(RentalFormFactor.BICYCLE))
    assertEquals(1, station.vehiclesFor(RentalFormFactor.CARGO_BICYCLE))
  }

  @Test
  fun `un type inconnu fait renoncer a la ventilation, pas produire un total tronque`() {
    val station = station(
      total = 12,
      types = mapOf("196" to 2, "431" to 9, "446" to 1),
      // Le serveur n'a pas décrit `431` : le compter pour zéro annoncerait trois vélos au lieu de
      // douze, avec le même aplomb. Le seul chiffre sûr est alors le total.
      kinds = mapOf("196" to bike, "446" to eBike),
    )
    assertEquals(12, station.vehiclesFor(RentalFormFactor.BICYCLE))
  }

  @Test
  fun `sans type de vehicule connu, le total fait foi`() {
    val station = station(total = 5, types = mapOf("dott_scooter" to 5), kinds = mapOf("dott_scooter" to bike))
    assertEquals(5, station.vehiclesFor(formFactor = null))
  }

  @Test
  fun `une station vide annonce zero, sans se tromper de type`() {
    val station = station(
      total = 0,
      types = mapOf("mechanical" to 0, "electrical" to 0),
      kinds = mapOf("mechanical" to bike, "electrical" to eBike),
    )
    assertEquals(0, station.vehiclesFor(RentalFormFactor.BICYCLE))
  }

  // --- Les places libres au retour --------------------------------------------------------------

  @Test
  fun `une station sans compte de bornes se distingue d'une station sans borne libre`() {
    // Tous les flux relevés — Vélib', Villo, nextbike — laissent `vehicleDocksAvailable` vide.
    // « Je ne sais pas » ne doit surtout pas s'afficher « 0 place libre ».
    assertFalse(station(total = 7).hasDockCounts())
    assertTrue(station(total = 7, docks = mapOf("mechanical" to 0)).hasDockCounts())
  }

  @Test
  fun `les places libres s'additionnent sur tous les types`() {
    val station = station(total = 3, docks = mapOf("mechanical" to 3, "electrical" to 1))
    assertEquals(4, station.docksAvailable())
  }

  // --- La ventilation par type ------------------------------------------------------------------

  @Test
  fun `deux identifiants de meme nature n'en font qu'une ligne`() {
    // Cas réel de nextbike Berlin : 196 et 431 sont deux références du même vélo mécanique.
    val station = station(
      total = 12,
      types = mapOf("196" to 2, "431" to 9, "446" to 1),
      kinds = mapOf("196" to bike, "431" to bike, "446" to eBike),
    )
    assertEquals(
      listOf(RentalTypeCount(bike, vehicles = 11, docks = 0), RentalTypeCount(eBike, vehicles = 1, docks = 0)),
      station.countsByKind(),
    )
  }

  @Test
  fun `une nature sans vehicule ni place libre n'est pas affichee`() {
    val station = station(
      total = 3,
      types = mapOf("mechanical" to 3, "electrical" to 0),
      kinds = mapOf("mechanical" to bike, "electrical" to eBike),
    )
    assertEquals(listOf(RentalTypeCount(bike, vehicles = 3, docks = 0)), station.countsByKind())
  }

  @Test
  fun `l'ordre suit celui du domaine, pas celui de la reponse`() {
    val station = station(
      total = 2,
      types = linkedMapOf("300" to 1, "299" to 1),
      kinds = mapOf("300" to cargo, "299" to bike),
    )
    assertEquals(listOf(bike, cargo), station.countsByKind().map { it.kind })
  }

  @Test
  fun `une nature inconnue passe en fin de liste`() {
    val station = station(
      total = 2,
      types = linkedMapOf("mystere" to 1, "299" to 1),
      kinds = mapOf("299" to bike),
    )
    assertEquals(listOf(bike, null), station.countsByKind().map { it.kind })
  }

  // --- Le choix de la station -------------------------------------------------------------------

  @Test
  fun `le nom de la portion l'emporte sur la proximite`() {
    // Trois exploitants se partagent le parvis de la gare de Berlin, à quelques mètres près. Prendre
    // la plus proche annoncerait les vélos de Call a Bike sur une portion nextbike.
    val point = LatLon(52.52369, 13.37076)
    val callABike = station(total = 13, name = "Jelbi S+U Hauptbahnhof / Washingtonplatz", at = point)
    val nextbike = station(
      total = 12,
      name = "Jelbi S+U Hauptbahnhof/Washingtonplatz (MOA/HW)",
      at = LatLon(52.523773, 13.370795),
    )
    val chosen = listOf(callABike, nextbike).stationFor(point, "Jelbi S+U Hauptbahnhof/Washingtonplatz (MOA/HW)")
    assertEquals(12, chosen?.numVehiclesAvailable)
  }

  @Test
  fun `la casse et les espaces du nom ne font pas rater la station`() {
    val point = LatLon(50.846331, 4.355193)
    val agora = station(total = 5, name = "AGORA", at = point)
    assertEquals(agora, listOf(agora).stationFor(point, "  agora "))
  }

  @Test
  fun `faute de nom reconnu, la plus proche fait office`() {
    val point = LatLon(52.52369, 13.37076)
    val near = station(total = 13, name = "ici", at = point)
    val far = station(total = 12, name = "là-bas", at = LatLon(52.60, 13.50))
    assertEquals(near, listOf(near, far).stationFor(point, "un nom que le serveur ne connaît pas"))
  }

  @Test
  fun `une portion sans nom de station se contente de la proximite`() {
    val point = LatLon(47.661659, 9.173094)
    val near = station(total = 2, name = "Stephansplatz (k PLUS Station)", at = point)
    val far = station(total = 7, name = "Bahnhof Konstanz (k PLUS Station)", at = LatLon(47.658, 9.178))
    assertEquals(near, listOf(near, far).stationFor(point, name = null))
  }

  @Test
  fun `un serveur qui ne rend aucune station ne fait pas choisir au hasard`() {
    assertNull(emptyList<RentalAvailability>().stationFor(LatLon(48.85, 2.35), "Bastille"))
  }

  private fun station(
    total: Int,
    name: String = "station",
    at: LatLon = LatLon(52.52369, 13.37076),
    types: Map<String, Int> = emptyMap(),
    docks: Map<String, Int> = emptyMap(),
    kinds: Map<String, RentalVehicleKind> = emptyMap(),
  ) = RentalAvailability(
    stationId = name,
    name = name,
    coordinates = at,
    numVehiclesAvailable = total,
    vehicleTypesAvailable = types,
    vehicleDocksAvailable = docks,
    retrievedAt = this.at,
    vehicleKinds = kinds,
  )
}
