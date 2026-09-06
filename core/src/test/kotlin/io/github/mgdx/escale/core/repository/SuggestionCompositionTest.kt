package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** La composition de la liste affichée (SPEC.md § 5.1). */
class SuggestionCompositionTest {

  private var nextPoint = 0

  /**
   * Chaque suggestion a son propre point : rien ici ne doit dépendre des coordonnées.
   *
   * Le score décroît le long de la liste, comme le fait le serveur : c'est cet ordre-là que la
   * composition conserve, et aucune règle ne compare le score à un seuil.
   */
  private fun location(name: String, kind: PlaceKind, description: String?): Location {
    nextPoint++
    return Location(
      id = if (kind == PlaceKind.STOP) "stop-$nextPoint" else null,
      name = name,
      description = description,
      coordinates = LatLon(lat = 48.0 + nextPoint / 100.0, lon = 2.0 + nextPoint / 100.0),
      kind = kind,
      score = 1.0 - nextPoint / 100.0,
    )
  }

  private fun stop(description: String) = location("Rue de la Paix", PlaceKind.STOP, description)

  private fun address(description: String) = location("Rue de la Paix", PlaceKind.ADDRESS, description)

  private fun place(name: String) = location(name, PlaceKind.PLACE, "Paris, Île-de-France")

  /**
   * Le cas relevé : `text=rue de la paix`, carte sur Paris. Dix arrêts homonymes de communes
   * voisines, et les deux adresses cherchées aux rangs 14 et 17.
   */
  private fun releve(): List<Location> = buildList {
    add(stop("Paris, Île-de-France"))
    listOf("Levallois-Perret", "Clichy", "Puteaux", "Courbevoie", "Asnières", "Colombes", "Nanterre", "Suresnes")
      .forEach { add(stop(it)) }
    add(stop("Paris, Île-de-France"))
    repeat(3) { add(place("Hôtel de la Paix")) }
    add(address("Paris, Île-de-France"))
    repeat(2) { add(place("Café de la Paix")) }
    add(address("Neuilly-sur-Seine"))
    repeat(3) { add(place("Passage de la Paix")) }
  }

  @Test
  fun `les deux adresses du releve figurent dans les dix affiches`() {
    val candidates = releve()
    assertEquals(20, candidates.size)
    // Sans composition, les deux adresses sont aux rangs 14 et 17 : hors de l'écran.
    assertEquals(13, candidates.indexOfFirst { it.kind == PlaceKind.ADDRESS })

    val composed = composeSuggestions(candidates)

    assertEquals(10, composed.size)
    assertTrue(composed.any { it.kind == PlaceKind.ADDRESS })
    assertTrue(composed.any { it.kind == PlaceKind.PLACE })
  }

  @Test
  fun `au plus quatre arrets d une autre commune que le premier`() {
    val composed = composeSuggestions(releve())
    val distant = composed.count { it.kind == PlaceKind.STOP && it.description != "Paris, Île-de-France" }
    assertEquals(MAX_DISTANT_STOPS, distant)
  }

  @Test
  fun `l ordre du serveur est conserve entre les suggestions gardees`() {
    val candidates = releve()
    val composed = composeSuggestions(candidates)
    // Les rangs d'origine des lignes affichées ne descendent jamais, sauf pour les places réservées
    // à la première adresse et au premier lieu, qui viennent en fin de liste.
    val ranks = composed.map(candidates::indexOf)
    assertEquals(ranks.take(9), ranks.take(9).sorted())
    assertSame(candidates.first(), composed.first())
  }

  @Test
  fun `une liste plus courte que la limite est rendue telle quelle`() {
    val candidates = listOf(stop("Paris"), stop("Clichy"), stop("Levallois"), stop("Puteaux"), stop("Colombes"))
    assertEquals(candidates, composeSuggestions(candidates, limit = 10))
  }

  @Test
  fun `sans adresse ni lieu, les arrets excedentaires sont repousses derriere les autres`() {
    // Un arrêt de la commune visée, huit homonymes d'ailleurs, puis trois de la commune visée.
    val candidates = List(12) { rang ->
      stop(if (rang == 0 || rang > 8) "Paris" else "Commune $rang")
    }
    val composed = composeSuggestions(candidates, limit = 10)
    assertEquals(10, composed.size)
    // L'arrêt parisien, quatre homonymes, puis les trois arrêts parisiens qui suivaient.
    assertEquals(candidates.take(5) + candidates.subList(9, 12), composed.take(8))
    // Et seulement ensuite les homonymes repoussés, dans leur ordre d'origine.
    assertEquals(candidates.subList(5, 7), composed.drop(8))
  }

  @Test
  fun `sans arret, rien n est repousse`() {
    val candidates = List(12) { place("Lieu $it") }
    assertEquals(candidates.take(10), composeSuggestions(candidates, limit = 10))
  }

  @Test
  fun `une adresse hors des dix se voit reserver une place, en bas de liste`() {
    // Douze arrêts de la même commune : aucun n'est repoussé, et l'adresse reste au douzième rang.
    val candidates = List(12) { stop("Paris") } + address("Paris")
    val composed = composeSuggestions(candidates, limit = 10)
    assertEquals(10, composed.size)
    assertEquals(candidates.take(9), composed.take(9))
    assertSame(candidates.last(), composed.last())
  }

  @Test
  fun `une limite nulle ne rend rien`() {
    assertEquals(emptyList<Location>(), composeSuggestions(releve(), limit = 0))
  }
}
