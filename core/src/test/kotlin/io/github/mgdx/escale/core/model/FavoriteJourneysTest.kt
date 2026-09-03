package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Ce qui fait que deux trajets favoris sont le même (SPEC.md § 5.5).
 *
 * La règle vaut à deux endroits qui doivent dire la même chose : l'étoile de l'écran de détail, qui
 * en reflète l'état, et l'index unique de la base, qui refuse le doublon.
 */
class FavoriteJourneysTest {

  private val bastille = Location(
    id = null,
    name = "Place de la Bastille",
    description = "Paris",
    coordinates = LatLon(48.8532, 2.3692),
    kind = PlaceKind.ADDRESS,
  )

  private val gareDeLyon = Location(
    id = "de:06:1234",
    name = "Gare de Lyon",
    description = "Paris 12e",
    coordinates = LatLon(48.8443, 2.3735),
    kind = PlaceKind.STOP,
    servedModes = listOf(TransitMode.SUBWAY),
  )

  private fun favorite(id: Long, from: Location, to: Location, category: JourneyCategory) = FavoriteJourney(
    id = id,
    label = null,
    from = from,
    to = to,
    category = category,
    createdAt = Instant.parse("2026-03-01T08:10:00Z"),
  )

  @Test
  fun `le meme couple dans la meme categorie est le meme trajet`() {
    val favoris = listOf(favorite(1, bastille, gareDeLyon, JourneyCategory.TRANSIT))

    assertEquals(1L, favoris.matching(bastille, gareDeLyon, JourneyCategory.TRANSIT)?.id)
  }

  @Test
  fun `la categorie fait partie de l identite`() {
    val favoris = listOf(favorite(1, bastille, gareDeLyon, JourneyCategory.TRANSIT))

    assertNull(favoris.matching(bastille, gareDeLyon, JourneyCategory.BIKE))
  }

  @Test
  fun `le sens compte, un aller n est pas un retour`() {
    val favoris = listOf(favorite(1, bastille, gareDeLyon, JourneyCategory.TRANSIT))

    assertNull(favoris.matching(gareDeLyon, bastille, JourneyCategory.TRANSIT))
  }

  @Test
  fun `deux homonymes a des points differents ne se confondent pas`() {
    val ailleurs = bastille.copy(coordinates = LatLon(45.7578, 4.8320))
    val favoris = listOf(favorite(1, bastille, gareDeLyon, JourneyCategory.TRANSIT))

    assertNull(favoris.matching(ailleurs, gareDeLyon, JourneyCategory.TRANSIT))
  }

  @Test
  fun `un lieu redecrit par le serveur reste le meme point`() {
    // Le serveur peut rendre le même arrêt avec une description différente, ou sans ses modes :
    // ni l'une ni les autres ne font l'identité, sans quoi l'étoile changerait d'état toute seule
    // entre deux réponses.
    val redecrit = gareDeLyon.copy(description = "Paris", servedModes = emptyList())
    val favoris = listOf(favorite(1, bastille, gareDeLyon, JourneyCategory.TRANSIT))

    assertEquals(1L, favoris.matching(bastille, redecrit, JourneyCategory.TRANSIT)?.id)
  }
}
