package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le cache de **soixante secondes** des disponibilités en libre-service (SPEC.md § 5.7, règle 4).
 *
 * Toute la valeur de ce test tient dans une différence de facteur dix : dix minutes pour un arrêt
 * de bus, soixante secondes pour un vélo. Une erreur d'unité ou une constante recopiée depuis le
 * cache des arrêts ne casserait rien de visible — elle ferait simplement afficher, pendant neuf
 * minutes, un chiffre auquel l'usager va se fier pour aller quelque part.
 */
class RentalsCacheTest {

  private val start = Instant.parse("2026-09-01T10:00:00Z")

  private val station = LatLon(52.52369, 13.37076)
  private val around = RentalsQuery.Around(station, radiusMeters = 50)

  private val wide = BoundingBox(min = LatLon(52.51, 13.36), max = LatLon(52.53, 13.39))
  private val inside = BoundingBox(min = LatLon(52.520, 13.368), max = LatLon(52.526, 13.374))

  private val stations = listOf(availability("gare"), availability("place"))

  // --- La durée de vie, qui est tout l'enjeu ---------------------------------------------------

  @Test
  fun `l'expiration vaut exactement soixante secondes`() {
    assertEquals(Duration.ofSeconds(60), RENTALS_CACHE_LIFETIME)
  }

  @Test
  fun `elle est dix fois plus courte que celle des arrets, et c'est voulu`() {
    assertEquals(STOPS_CACHE_LIFETIME.seconds, RENTALS_CACHE_LIFETIME.seconds * 10)
  }

  @Test
  fun `une entree de cinquante-neuf secondes repond encore`() {
    val entry = entry(around)
    assertTrue(entry.answers(around, start.plusSeconds(59), RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `une entree de soixante secondes pile ne repond plus`() {
    val entry = entry(around)
    assertFalse(
      "à l'instant exact de l'expiration, la donnée est périmée : le doute profite à l'usager",
      entry.answers(around, start.plusSeconds(60), RENTALS_CACHE_LIFETIME),
    )
  }

  @Test
  fun `une entree de deux minutes est perimee, quelle que soit la demande`() {
    val entry = entry(RentalsQuery.Within(wide))
    assertFalse(entry.answers(RentalsQuery.Within(inside), start.plusSeconds(120), RENTALS_CACHE_LIFETIME))
  }

  // --- Ce qu'une entrée couvre -----------------------------------------------------------------

  @Test
  fun `une emprise deja chargee couvre toute emprise incluse dedans`() {
    assertFalse("le cas n'aurait aucun intérêt si les deux emprises étaient égales", wide == inside)
    assertTrue(entry(RentalsQuery.Within(wide)).answers(RentalsQuery.Within(inside), start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise ne repond pas a une demande autour d'un point`() {
    assertFalse(entry(RentalsQuery.Within(wide)).answers(around, start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `un rayon plus large ne repond pas a un rayon plus etroit`() {
    val larger = RentalsQuery.Around(station, radiusMeters = 200)
    assertFalse(
      "un rayon différent est une autre demande : la station la plus proche n'y est pas la même",
      entry(larger).answers(around, start, RENTALS_CACHE_LIFETIME),
    )
  }

  @Test
  fun `un point voisin ne repond pas non plus`() {
    val neighbour = RentalsQuery.Around(LatLon(52.52400, 13.37100), radiusMeters = 50)
    assertFalse(entry(around).answers(neighbour, start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `la meme demande, elle, est servie sans requete`() {
    assertTrue(entry(around).answers(around, start.plusSeconds(30), RENTALS_CACHE_LIFETIME))
  }

  // --- Le cache lui-même -----------------------------------------------------------------------

  @Test
  fun `le cache rend la reponse deja obtenue dans la minute`() = runTest {
    var now = start
    val cache = RentalsCache(now = { now })
    cache.store(around, stations)
    now = start.plusSeconds(59)
    assertEquals(stations, cache.cached(around))
  }

  @Test
  fun `le cache oublie la reponse passe la minute`() = runTest {
    var now = start
    val cache = RentalsCache(now = { now })
    cache.store(around, stations)
    now = start.plusSeconds(61)
    assertNull(cache.cached(around))
  }

  @Test
  fun `un changement de serveur vide le cache`() = runTest {
    val cache = RentalsCache(now = { start })
    cache.store(around, stations)
    cache.clear()
    assertNull(cache.cached(around))
  }

  @Test
  fun `au-dela du plafond, la plus ancienne entree est oubliee`() = runTest {
    val cache = RentalsCache(maxEntries = 2, now = { start })
    val first = RentalsQuery.Around(LatLon(52.1, 13.1), radiusMeters = 50)
    val second = RentalsQuery.Around(LatLon(52.2, 13.2), radiusMeters = 50)
    val third = RentalsQuery.Around(LatLon(52.3, 13.3), radiusMeters = 50)
    cache.store(first, stations)
    cache.store(second, stations)
    cache.store(third, stations)

    assertNull("la plus ancienne est partie", cache.cached(first))
    assertNotNull(cache.cached(second))
    assertNotNull(cache.cached(third))
  }

  private fun entry(query: RentalsQuery) = RentalsCacheEntry(query = query, storedAt = start, stations = stations)

  private fun availability(name: String) = RentalAvailability(
    stationId = name,
    name = name,
    coordinates = station,
    numVehiclesAvailable = 3,
    retrievedAt = start,
  )
}
