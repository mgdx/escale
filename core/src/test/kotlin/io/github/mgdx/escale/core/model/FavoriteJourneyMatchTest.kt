package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** La reconnaissance d'un trajet favori depuis l'écran de détail (SPEC.md § 5.5.1). */
class FavoriteJourneyMatchTest {

  private val lyon = stop("Gare de Lyon", "stop-lyon")
  private val nation = stop("Nation", "stop-nation")

  private val favorite = FavoriteJourney(
    id = 7,
    label = null,
    from = lyon,
    to = nation,
    category = JourneyCategory.TRANSIT,
    createdAt = Instant.parse("2026-09-01T08:00:00Z"),
  )

  @Test
  fun `le meme couple d'arrets sur le meme onglet est reconnu`() {
    val found = FavoriteJourneyMatch.find(listOf(favorite), lyon, nation, JourneyCategory.TRANSIT)

    assertEquals(7L, found?.id)
  }

  @Test
  fun `le trajet inverse n'est pas le meme favori`() {
    assertNull(FavoriteJourneyMatch.find(listOf(favorite), nation, lyon, JourneyCategory.TRANSIT))
  }

  @Test
  fun `un autre onglet n'est pas le meme favori`() {
    assertNull(FavoriteJourneyMatch.find(listOf(favorite), lyon, nation, JourneyCategory.BIKE))
  }

  @Test
  fun `un brouillon incomplet ne correspond a rien`() {
    assertNull(FavoriteJourneyMatch.find(listOf(favorite), null, nation, JourneyCategory.TRANSIT))
  }

  @Test
  fun `deux adresses a un metre pres sont le meme lieu`() {
    val here = address(48.844300, 2.373500)
    val almost = address(48.844301, 2.373499)

    assertTrue(FavoriteJourneyMatch.same(here, almost))
  }

  @Test
  fun `un arret et une adresse a la meme position restent distincts`() {
    assertFalse(FavoriteJourneyMatch.same(lyon, address(lyon.coordinates.lat, lyon.coordinates.lon)))
  }

  private fun stop(name: String, id: String) = Location(
    id = id,
    name = name,
    description = null,
    coordinates = LatLon(lat = 48.8443, lon = 2.3735),
    kind = PlaceKind.STOP,
  )

  private fun address(lat: Double, lon: Double) = Location(
    id = null,
    name = "12 rue de Lyon",
    description = null,
    coordinates = LatLon(lat = lat, lon = lon),
    kind = PlaceKind.ADDRESS,
  )
}
