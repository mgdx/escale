package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
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
 * Le cache par emprise et par palier de SPEC.md § 5.7, règle 4.
 *
 * « Une emprise déjà **couverte** par une réponse en cache n'est pas redemandée » : couverte veut
 * dire **incluse**, pas égale. C'est le point sur lequel on se trompe, et c'est donc celui qui est
 * éprouvé le plus longuement ici.
 */
class StopsCacheTest {

  private val start = Instant.parse("2026-09-01T10:00:00Z")

  private val wide = box(48.80, 2.30, 48.90, 2.40)
  private val inside = box(48.84, 2.33, 48.86, 2.36)
  private val overlapping = box(48.85, 2.35, 48.95, 2.45)

  private val rail = TransitMode.HEAVY_RAIL_MODES
  private val everything = rail + setOf(TransitMode.BUS, TransitMode.TRAM)

  private val stops = listOf(stop("gare"), stop("halte"))

  // --- La règle, à l'état pur -----------------------------------------------------------------

  @Test
  fun `une emprise strictement incluse est deja couverte, sans etre egale`() {
    val entry = entry(area = wide, modes = rail)
    assertFalse("le cas n'aurait aucun intérêt si les deux emprises étaient égales", wide == inside)
    assertTrue(entry.answers(inside, rail, start, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise identique est couverte, bords compris`() {
    assertTrue(entry(area = wide, modes = rail).answers(wide, rail, start, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `une emprise qui deborde n'est pas couverte`() {
    assertFalse(entry(area = wide, modes = rail).answers(overlapping, rail, start, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `un palier plus riche repond a un palier plus pauvre, jamais l'inverse`() {
    // Dézoomer de 13 vers 11 ne redemande rien : les gares sont déjà là (règle 5).
    assertTrue(entry(area = wide, modes = everything).answers(inside, rail, start, STOPS_CACHE_LIFETIME))
    // Zoomer de 11 vers 13 réclame les bus et les trams, que l'entrée ferrée ne contient pas.
    assertFalse(entry(area = wide, modes = rail).answers(inside, everything, start, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `une entree sans mode vaut pour tous les modes`() {
    // Un ensemble vide signifie « tous les modes » côté API : une telle réponse répond à tout.
    assertTrue(entry(area = wide, modes = emptySet()).answers(inside, everything, start, STOPS_CACHE_LIFETIME))
    assertFalse(entry(area = wide, modes = rail).answers(inside, emptySet(), start, STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `passe dix minutes, l'entree ne repond plus`() {
    val entry = entry(area = wide, modes = rail)
    assertTrue(entry.answers(inside, rail, start.plus(Duration.ofMinutes(9)), STOPS_CACHE_LIFETIME))
    assertFalse(entry.answers(inside, rail, start.plus(STOPS_CACHE_LIFETIME), STOPS_CACHE_LIFETIME))
  }

  @Test
  fun `la duree de vie des arrets est celle de la spec`() {
    assertEquals(Duration.ofMinutes(10), STOPS_CACHE_LIFETIME)
  }

  // --- Le cache lui-même ----------------------------------------------------------------------

  @Test
  fun `une emprise deja couverte n'est pas redemandee`() = runTest {
    val cache = StopsCache(now = { start })
    cache.store(wide, rail, stops)
    assertEquals(stops, cache.cached(inside, rail))
  }

  @Test
  fun `une emprise voisine mais debordante doit etre demandee`() = runTest {
    val cache = StopsCache(now = { start })
    cache.store(wide, rail, stops)
    assertNull(cache.cached(overlapping, rail))
  }

  @Test
  fun `une entree perimee est demandee a nouveau`() = runTest {
    var clock = start
    val cache = StopsCache(now = { clock })
    cache.store(wide, rail, stops)
    clock = start.plus(Duration.ofMinutes(11))
    assertNull(cache.cached(inside, rail))
  }

  @Test
  fun `le cache retient plusieurs emprises et oublie la plus ancienne`() = runTest {
    val cache = StopsCache(maxEntries = 2, now = { start })
    cache.store(box(0.0, 0.0, 1.0, 1.0), rail, listOf(stop("premier")))
    cache.store(box(10.0, 10.0, 11.0, 11.0), rail, listOf(stop("deuxième")))
    cache.store(box(20.0, 20.0, 21.0, 21.0), rail, listOf(stop("troisième")))

    assertNull(cache.cached(box(0.1, 0.1, 0.2, 0.2), rail))
    assertNotNull(cache.cached(box(10.1, 10.1, 10.2, 10.2), rail))
    assertNotNull(cache.cached(box(20.1, 20.1, 20.2, 20.2), rail))
  }

  @Test
  fun `changer de serveur vide le cache`() = runTest {
    val cache = StopsCache(now = { start })
    cache.store(wide, rail, stops)
    cache.clear()
    assertNull(cache.cached(inside, rail))
  }

  private fun entry(area: BoundingBox, modes: Set<TransitMode>) =
    StopsCacheEntry(area = area, modes = modes, storedAt = start, stops = stops)

  private fun box(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) =
    BoundingBox(min = LatLon(minLat, minLon), max = LatLon(maxLat, maxLon))

  private fun stop(id: String) = Stop(id = id, name = id, coordinates = LatLon(48.85, 2.35))
}
