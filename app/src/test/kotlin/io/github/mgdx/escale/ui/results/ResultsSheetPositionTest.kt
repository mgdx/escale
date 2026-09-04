package io.github.mgdx.escale.ui.results

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les positions de la feuille de résultats (SPEC.md § 5.1 et § 5.2).
 *
 * Ces cas fixent la promesse faite à l'usager : la feuille glisse pour laisser voir la carte, et
 * même au plus bas elle ne cache que le détail — jamais les onglets ni les durées qu'ils annoncent.
 */
class ResultsSheetPositionTest {

  private val available = 800.dp
  private val header = 120.dp

  @Test
  fun `repliée, la feuille laisse exactement la place de l'entête`() {
    assertEquals(header, height(ResultsSheetPosition.COLLAPSED))
  }

  @Test
  fun `repliée, la feuille est la plus basse des trois positions`() {
    val collapsed = height(ResultsSheetPosition.COLLAPSED)
    assertTrue(collapsed < height(ResultsSheetPosition.HALF))
    assertTrue(collapsed < height(ResultsSheetPosition.EXPANDED))
  }

  @Test
  fun `dépliée, la feuille laisse encore voir une bande de carte`() {
    assertTrue(height(ResultsSheetPosition.EXPANDED) < available)
  }

  @Test
  fun `un entête plus haut que la position intermédiaire ne fait pas remonter le repli`() {
    val collapsed = resultsSheetHeight(ResultsSheetPosition.COLLAPSED, headerHeight = 500.dp, available = available)
    assertEquals(height(ResultsSheetPosition.HALF), collapsed)
  }

  @Test
  fun `tant que l'entête n'est pas mesuré, la feuille ne se replie pas à zéro`() {
    val collapsed = resultsSheetHeight(ResultsSheetPosition.COLLAPSED, headerHeight = 0.dp, available = available)
    assertEquals(height(ResultsSheetPosition.HALF), collapsed)
  }

  @Test
  fun `lâchée sans élan, la feuille rejoint la position la plus proche`() {
    assertEquals(ResultsSheetPosition.COLLAPSED, settled(height = 150.dp, velocity = 0f))
    assertEquals(ResultsSheetPosition.HALF, settled(height = 330.dp, velocity = 0f))
    assertEquals(ResultsSheetPosition.EXPANDED, settled(height = 700.dp, velocity = 0f))
  }

  @Test
  fun `une chiquenaude vers le bas replie la feuille depuis la position intermédiaire`() {
    assertEquals(ResultsSheetPosition.COLLAPSED, settled(height = 360.dp, velocity = -800f))
  }

  @Test
  fun `une chiquenaude vers le haut déplie la feuille depuis la position intermédiaire`() {
    assertEquals(ResultsSheetPosition.EXPANDED, settled(height = 360.dp, velocity = 800f))
  }

  @Test
  fun `une chiquenaude ne fait pas sortir la feuille de ses positions extrêmes`() {
    assertEquals(ResultsSheetPosition.EXPANDED, settled(height = 720.dp, velocity = 800f))
    assertEquals(ResultsSheetPosition.COLLAPSED, settled(height = header, velocity = -800f))
  }

  @Test
  fun `un frôlement ne suffit pas à changer de position`() {
    assertEquals(ResultsSheetPosition.HALF, settled(height = 360.dp, velocity = -100f))
  }

  @Test
  fun `l'appui sur la poignée fait le tour des trois positions`() {
    assertEquals(ResultsSheetPosition.HALF, ResultsSheetPosition.COLLAPSED.next())
    assertEquals(ResultsSheetPosition.EXPANDED, ResultsSheetPosition.HALF.next())
    assertEquals(ResultsSheetPosition.COLLAPSED, ResultsSheetPosition.EXPANDED.next())
  }

  private fun height(position: ResultsSheetPosition) =
    resultsSheetHeight(position, headerHeight = header, available = available)

  private fun settled(height: Dp, velocity: Float) =
    settledResultsSheetPosition(height, velocity, headerHeight = header, available = available)
}
