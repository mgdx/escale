package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Les doublons stricts de `/api/v1/geocode`, et ce qui n'en est pas. */
class LocationDeduplicationTest {

  private fun location(
    name: String,
    description: String? = "Paris, Île-de-France",
    lat: Double = 48.8606,
    lon: Double = 2.3376,
    kind: PlaceKind = PlaceKind.PLACE,
    id: String? = null,
    servedModes: List<TransitMode> = emptyList(),
  ) = Location(
    id = id,
    name = name,
    description = description,
    coordinates = LatLon(lat, lon),
    kind = kind,
    servedModes = servedModes,
  )

  @Test
  fun `trois fois le meme lieu ne font qu une suggestion`() {
    // Relevé sur Transitous, `text=louvre`.
    val suggestions = listOf(location("Louvre"), location("Louvre"), location("Louvre"))
    assertEquals(listOf("Louvre"), suggestions.withoutDuplicates().map { it.name })
  }

  @Test
  fun `le premier exemplaire est celui qui reste, et l ordre ne bouge pas`() {
    // Le serveur classe par pertinence : celui qu'il met en tête est le bon.
    val premier = location("Gare de Lyon-Diderot")
    val suggestions = listOf(
      location("Musée du Louvre"),
      premier,
      location("Gare de Lyon-Diderot"),
      location("Bastille", lat = 48.8532, lon = 2.3692),
    )
    val kept = suggestions.withoutDuplicates()
    assertEquals(listOf("Musée du Louvre", "Gare de Lyon-Diderot", "Bastille"), kept.map { it.name })
    assertSame(premier, kept[1])
  }

  @Test
  fun `deux communes de meme nom restent deux suggestions`() {
    val suggestions = listOf(
      location("Rue de la Paix", description = "Paris, Île-de-France"),
      location("Rue de la Paix", description = "Vierzon, Centre-Val de Loire", lat = 47.2229, lon = 2.0699),
    )
    assertEquals(2, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `deux arrets homonymes a vingt metres restent deux suggestions`() {
    // Les deux sens d'une même rue : confondre leurs `stopId` ferait partir du mauvais quai.
    val suggestions = listOf(
      location("Rivoli", kind = PlaceKind.STOP, id = "fr:stop:1", lat = 48.8560, lon = 2.3570),
      location("Rivoli", kind = PlaceKind.STOP, id = "fr:stop:2", lat = 48.8562, lon = 2.3570),
    )
    assertEquals(listOf("fr:stop:1", "fr:stop:2"), suggestions.withoutDuplicates().map { it.id })
  }

  @Test
  fun `deux arrets de meme identifiant ne font qu une suggestion`() {
    val suggestions = listOf(
      location("Châtelet", kind = PlaceKind.STOP, id = "fr:stop:1"),
      location("Châtelet", kind = PlaceKind.STOP, id = "fr:stop:1"),
    )
    assertEquals(1, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `la casse, les espaces et la ponctuation typographique ne font pas deux lieux`() {
    val suggestions = listOf(
      location("Gare de Lyon-Diderot"),
      location("gare  de lyon–diderot"),
      location("Place d'Italie", lat = 48.8312, lon = 2.3555),
      location("Place d’Italie", lat = 48.8312, lon = 2.3555),
    )
    assertEquals(listOf("Gare de Lyon-Diderot", "Place d'Italie"), suggestions.withoutDuplicates().map { it.name })
  }

  @Test
  fun `un arret et une adresse de meme nom sont deux endroits`() {
    val suggestions = listOf(
      location("Bastille", kind = PlaceKind.STOP, id = "fr:stop:9"),
      location("Bastille", kind = PlaceKind.ADDRESS),
    )
    assertEquals(2, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `au-dela de cent metres, deux homonymes sont deux endroits`() {
    // 0,002° de latitude valent environ 220 m.
    val suggestions = listOf(
      location("Mairie"),
      location("Mairie", lat = 48.8606 + 0.002),
    )
    assertEquals(2, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `en deca de cent metres, deux homonymes n en font qu un`() {
    // 0,0005° de latitude valent environ 55 m : le serveur place tantôt l'entrée, tantôt le centre.
    val suggestions = listOf(
      location("Mairie"),
      location("Mairie", lat = 48.8606 + 0.0005),
    )
    assertEquals(1, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `une description absente ne vaut pas une description vide de sens`() {
    val suggestions = listOf(location("Louvre", description = null), location("Louvre"))
    assertEquals(2, suggestions.withoutDuplicates().size)
  }

  @Test
  fun `une liste vide ou d un seul element traverse sans dommage`() {
    assertEquals(emptyList<Location>(), emptyList<Location>().withoutDuplicates())
    val seule = listOf(location("Louvre"))
    assertEquals(seule, seule.withoutDuplicates())
  }
}
