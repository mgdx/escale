package io.github.mgdx.escale.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les clés de la liste d'autocomplétion (SPEC.md § 5.1), partagées par la carte de recherche et par
 * le choix d'un lieu de l'écran des favoris.
 *
 * La liste vient du serveur de géocodage : rien ne garantit qu'il ne rendra pas deux fois la même
 * chose, et deux clés égales font lever la liste paresseuse.
 */
class SuggestionKeyTest {

  @Test
  fun `un serveur qui rend deux fois le meme arret ne fait pas lever la liste`() {
    val gare = stop("de:06:1234", "Gare de Lyon")
    val suggestions = listOf(gare, gare.copy(description = "Paris 12e"))

    val keys = suggestions.mapIndexed(::suggestionKey)

    assertEquals(keys.size, keys.distinct().size)
  }

  @Test
  fun `deux adresses identiques au meme point restent deux entrees distinctes`() {
    val adresse = address("12 rue des Lilas")
    val suggestions = listOf(adresse, adresse)

    val keys = suggestions.mapIndexed(::suggestionKey)

    assertEquals(keys.size, keys.distinct().size)
  }

  @Test
  fun `deux lieux differents ont des cles differentes`() {
    val suggestions = listOf(address("Bastille"), stop("de:06:1234", "Gare de Lyon"))

    val keys = suggestions.mapIndexed(::suggestionKey)

    assertEquals(keys.size, keys.distinct().size)
  }
}
