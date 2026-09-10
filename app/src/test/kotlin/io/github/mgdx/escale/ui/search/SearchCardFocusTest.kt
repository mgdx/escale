package io.github.mgdx.escale.ui.search

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anomalie A5 du rapport du 9 septembre 2026 : un bandeau gris couvrait la ligne « Départ » dès le
 * lancement, sans le moindre geste de l'usager.
 *
 * La cause est le fond de focus de Material, dessiné dans les bornes de la ligne, allumé par le
 * focus initial que le système accorde quand l'appareil est sorti du mode tactile. Ces deux cas
 * d'essai tiennent la correction : plus aucun fond au focus, et un anneau qui reste à l'intérieur
 * de l'angle arrondi de la carte.
 */
class SearchCardFocusTest {

  @Test
  fun `le focus ne pose plus aucun fond sur une ligne de la carte`() {
    val alpha = requireNotNull(SearchRowRipple.rippleAlpha)
    assertEquals(0f, alpha.focusedAlpha, 0f)
  }

  @Test
  fun `l'indication au toucher reste celle du theme`() {
    val alpha = requireNotNull(SearchRowRipple.rippleAlpha)
    assertTrue("l'appui doit rester visible (SPEC.md § 9)", alpha.pressedAlpha > 0f)
    assertTrue(alpha.hoveredAlpha > 0f)
    assertTrue(alpha.draggedAlpha > 0f)
  }

  @Test
  fun `l'anneau de focus epouse l'angle arrondi de la carte`() {
    // Le bord extérieur de l'anneau est en retrait de `FocusRingInset` ; pour rester parallèle à
    // l'angle de la carte, son rayon doit valoir celui de la carte moins ce retrait.
    assertEquals(CardCornerRadius, FocusRingInset + FocusRingWidth / 2 + FocusRingRadius)
    assertTrue("l'anneau doit rester en retrait du bord", FocusRingInset > 0.dp)
  }
}
