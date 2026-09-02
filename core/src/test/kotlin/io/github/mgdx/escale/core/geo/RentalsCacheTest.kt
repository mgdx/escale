package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le cache des disponibilités en libre-service (SPEC.md § 5.7, règle 4).
 *
 * Ce qui est éprouvé ici, avant tout le reste : **l'expiration à soixante secondes**, et le fait
 * qu'elle ne soit pas celle des arrêts. C'est la seule différence entre les deux caches, donc
 * exactement l'endroit où une recopie distraite passerait inaperçue.
 */
class RentalsCacheTest {

  private val start = Instant.parse("2026-09-01T10:00:00Z")

  private val wide = box(48.80, 2.30, 48.90, 2.40)
  private val inside = box(48.84, 2.33, 48.86, 2.36)
  private val overlapping = box(48.85, 2.35, 48.95, 2.45)

  private val stations = listOf(station("velib-1"), station("velib-2"))

  // --- L'expiration à soixante secondes, et rien d'autre ---------------------------------------

  @Test
  fun `la duree de vie du libre-service est de soixante secondes`() {
    assertEquals(Duration.ofSeconds(60), RENTALS_CACHE_LIFETIME)
  }

  @Test
  fun `la duree de vie du libre-service n'est pas celle des arrets`() {
    assertNotEquals(
      "SPEC.md § 5.7 règle 4 : 10 min pour les arrêts, 60 s pour le libre-service",
      STOPS_CACHE_LIFETIME,
      RENTALS_CACHE_LIFETIME,
    )
    assertEquals(
      "dix fois plus court, littéralement",
      STOPS_CACHE_LIFETIME,
      RENTALS_CACHE_LIFETIME.multipliedBy(10),
    )
  }

  @Test
  fun `une entree de cinquante-neuf secondes repond encore`() {
    val entry = entry(area = wide)
    assertTrue(entry.answers(inside, start.plusSeconds(59), RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `a soixante secondes pile, l'entree est perimee`() {
    val entry = entry(area = wide)
    assertFalse(
      "la donnée est volatile : au seuil, on redemande",
      entry.answers(inside, start.plusSeconds(60), RENTALS_CACHE_LIFETIME),
    )
  }

  @Test
  fun `une entree encore valable pour des arrets est deja perimee pour du libre-service`() {
    val fiveMinutesLater = start.plusSeconds(300)
    val rental = entry(area = wide)
    assertFalse(rental.answers(inside, fiveMinutesLater, RENTALS_CACHE_LIFETIME))
    // Le même âge, jugé à l'aune des arrêts, serait parfaitement frais : la recopie de la durée
    // des arrêts serait donc invisible sans ce cas.
    assertTrue(rental.answers(inside, fiveMinutesLater, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `le cache oublie une reponse d'une minute et une seconde`() = runTest {
    var clock = start
    val cache = RentalsCache(now = { clock })
    cache.store(wide, stations)

    clock = start.plusSeconds(59)
    assertNotNull(cache.cached(inside))

    clock = start.plusSeconds(61)
    assertNull("SPEC.md § 5.7 : 60 secondes pour les disponibilités", cache.cached(inside))
  }

  // --- La couverture d'emprise, à l'identique de celle des arrêts -----------------------------

  @Test
  fun `une emprise strictement incluse est deja couverte, sans etre egale`() {
    assertFalse("le cas n'aurait aucun intérêt si les deux emprises étaient égales", wide == inside)
    assertTrue(entry(area = wide).answers(inside, start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise identique est couverte, bords compris`() {
    assertTrue(entry(area = wide).answers(wide, start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise qui deborde n'est pas couverte`() {
    assertFalse(entry(area = wide).answers(overlapping, start, RENTALS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise deja couverte n'est pas redemandee`() = runTest {
    val cache = RentalsCache(now = { start })
    assertNull("rien n'a encore été stocké", cache.cached(inside))
    cache.store(wide, stations)
    assertEquals(stations, cache.cached(inside))
  }

  @Test
  fun `une emprise voisine mais non couverte reste a demander`() = runTest {
    val cache = RentalsCache(now = { start })
    cache.store(wide, stations)
    assertNull(cache.cached(overlapping))
  }

  // --- La tenue en mémoire ---------------------------------------------------------------------

  @Test
  fun `au-dela du plafond, la plus ancienne emprise est oubliee`() = runTest {
    val cache = RentalsCache(maxEntries = 2, now = { start })
    cache.store(wide, stations)
    cache.store(box(45.70, 4.80, 45.80, 4.90), stations)
    cache.store(box(43.20, 5.30, 43.40, 5.50), stations)

    assertNull("la première emprise a été chassée", cache.cached(inside))
    assertNotNull(cache.cached(box(43.25, 5.35, 43.35, 5.45)))
  }

  @Test
  fun `changer de serveur vide le cache`() = runTest {
    val cache = RentalsCache(now = { start })
    cache.store(wide, stations)
    cache.clear()
    assertNull(cache.cached(inside))
  }

  private fun entry(area: BoundingBox) = RentalsCacheEntry(area = area, storedAt = start, availabilities = stations)

  private fun box(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) =
    BoundingBox(min = LatLon(minLat, minLon), max = LatLon(maxLat, maxLon))

  private fun station(id: String) = RentalAvailability(
    stationId = id,
    name = id,
    coordinates = LatLon(48.85, 2.35),
    numVehiclesAvailable = 3,
    vehicleDocksAvailable = mapOf("velo" to 4),
    retrievedAt = start,
  )
}
