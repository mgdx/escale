package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'ordre des onglets réglé par l'usager (SPEC.md § 5.2 et § 5.6).
 *
 * Deux promesses à tenir, et elles sont toutes deux ici : le glissé-déposé déplace la catégorie
 * saisie sans en faire sauter d'autres, et **quoi qu'il y ait sur le disque, les quatre catégories
 * sont là**. Une catégorie perdue en relecture serait un onglet inatteignable.
 */
class CategoryOrderTest {

  @Test
  fun `l ordre par defaut est celui de la spec`() {
    assertEquals(
      listOf(JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.BIKE, JourneyCategory.WALK),
      CategoryOrder.DEFAULT,
    )
  }

  @Test
  fun `deplacer vers le bas resserre les categories enjambees`() {
    // Le vélo passe en tête : les trois autres reculent d'un rang, aucune n'est échangée.
    assertEquals(
      listOf(JourneyCategory.BIKE, JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.WALK),
      CategoryOrder.moved(CategoryOrder.DEFAULT, from = 2, to = 0),
    )
  }

  @Test
  fun `deplacer vers le haut resserre de meme`() {
    assertEquals(
      listOf(JourneyCategory.CAR, JourneyCategory.BIKE, JourneyCategory.WALK, JourneyCategory.TRANSIT),
      CategoryOrder.moved(CategoryOrder.DEFAULT, from = 0, to = 3),
    )
  }

  @Test
  fun `un rang hors de la liste ne deplace rien`() {
    assertEquals(CategoryOrder.DEFAULT, CategoryOrder.moved(CategoryOrder.DEFAULT, from = -1, to = 2))
    assertEquals(CategoryOrder.DEFAULT, CategoryOrder.moved(CategoryOrder.DEFAULT, from = 1, to = 4))
    assertEquals(CategoryOrder.DEFAULT, CategoryOrder.moved(CategoryOrder.DEFAULT, from = 1, to = 1))
  }

  @Test
  fun `un ordre partiel est complete dans l ordre par defaut`() {
    assertEquals(
      listOf(JourneyCategory.WALK, JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.BIKE),
      CategoryOrder.sanitized(listOf(JourneyCategory.WALK)),
    )
  }

  @Test
  fun `un doublon ne compte qu une fois`() {
    assertEquals(
      listOf(JourneyCategory.BIKE, JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.WALK),
      CategoryOrder.sanitized(listOf(JourneyCategory.BIKE, JourneyCategory.BIKE, JourneyCategory.TRANSIT)),
    )
  }

  @Test
  fun `un nom inconnu est ignore, pas propage`() {
    // Ce que rendrait un fichier écrit par une version future, ou abîmé : on garde ce qu'on
    // comprend, on complète le reste, et l'application démarre.
    assertEquals(
      listOf(JourneyCategory.WALK, JourneyCategory.BIKE, JourneyCategory.TRANSIT, JourneyCategory.CAR),
      CategoryOrder.of(listOf("WALK", "HYPERLOOP", "BIKE")),
    )
  }

  @Test
  fun `une liste vide rend l ordre par defaut`() {
    assertEquals(CategoryOrder.DEFAULT, CategoryOrder.of(emptyList()))
    assertEquals(CategoryOrder.DEFAULT, CategoryOrder.of(listOf("")))
  }
}
