package io.github.mgdx.escale.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La géométrie du glissé-déposé de l'écran « Ordre des catégories » (SPEC.md § 5.6).
 *
 * Les hauteurs sont volontairement inégales dans la plupart des cas : c'est la situation réelle dès
 * qu'un libellé passe à deux lignes, et c'est là que la formule naïve « décalage divisé par la
 * hauteur d'une ligne » se trompe de cible (SPEC.md § 9).
 */
class DragReorderTest {

  private val egales = listOf(100, 100, 100, 100)
  private val inegales = listOf(100, 200, 100, 300)

  @Test
  fun `un decalage plus court qu une demi ligne ne change rien`() {
    assertEquals(1, DragReorder.targetIndex(egales, from = 1, offset = 49f))
    assertEquals(1, DragReorder.targetIndex(egales, from = 1, offset = -49f))
  }

  @Test
  fun `la ligne franchit sa voisine passe la moitie de celle-ci`() {
    assertEquals(2, DragReorder.targetIndex(egales, from = 1, offset = 51f))
    assertEquals(0, DragReorder.targetIndex(egales, from = 1, offset = -51f))
  }

  @Test
  fun `le seuil suit la hauteur de la voisine, pas celle de la ligne trainee`() {
    // La ligne 0 fait 100, sa voisine 200 : il faut dépasser 100, et non 50, pour la franchir.
    assertEquals(0, DragReorder.targetIndex(inegales, from = 0, offset = 99f))
    assertEquals(1, DragReorder.targetIndex(inegales, from = 0, offset = 101f))
  }

  @Test
  fun `un long decalage traverse plusieurs lignes de hauteurs differentes`() {
    // Depuis le rang 0 : franchir la ligne 1 (200) puis la moitié de la ligne 2 (50) fait 250.
    assertEquals(1, DragReorder.targetIndex(inegales, from = 0, offset = 249f))
    assertEquals(2, DragReorder.targetIndex(inegales, from = 0, offset = 251f))
  }

  @Test
  fun `le decalage ne fait jamais sortir de la liste`() {
    assertEquals(3, DragReorder.targetIndex(egales, from = 0, offset = 10_000f))
    assertEquals(0, DragReorder.targetIndex(egales, from = 3, offset = -10_000f))
  }

  @Test
  fun `tant qu une ligne n est pas mesuree, rien ne bouge`() {
    // La toute première image : les hauteurs ne sont pas connues, et un seuil calculé sur zéro
    // serait franchi dès le premier pixel.
    assertEquals(1, DragReorder.targetIndex(listOf(100, 0, 100, 100), from = 1, offset = 500f))
  }

  @Test
  fun `seules les lignes enjambees s ecartent, de la hauteur de la ligne trainee`() {
    // La ligne 3 (300 de haut) remonte au rang 1 : les lignes 1 et 2 descendent de 300, la 0 non.
    val shift = { index: Int -> DragReorder.shift(inegales, from = 3, target = 1, index = index) }
    assertEquals(0f, shift(0), 0f)
    assertEquals(300f, shift(1), 0f)
    assertEquals(300f, shift(2), 0f)
    assertEquals(0f, shift(3), 0f)
  }

  @Test
  fun `une ligne descendue fait remonter celles qu elle depasse`() {
    val shift = { index: Int -> DragReorder.shift(inegales, from = 0, target = 2, index = index) }
    assertEquals(0f, shift(0), 0f)
    assertEquals(-100f, shift(1), 0f)
    assertEquals(-100f, shift(2), 0f)
    assertEquals(0f, shift(3), 0f)
  }

  @Test
  fun `sans deplacement, aucune ligne ne s ecarte`() {
    inegales.indices.forEach { index ->
      assertEquals(0f, DragReorder.shift(inegales, from = 2, target = 2, index = index), 0f)
    }
  }
}
