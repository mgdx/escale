package io.github.mgdx.escale.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le remplissage de la caméra (SPEC.md § 5.1 et § 5.7, règle 9).
 *
 * Anomalie relevée sur téléphone : un trajet cadré passait sous la carte de recherche flottante,
 * dont le remplissage ne tenait pas compte. Ces cas fixent ce que « la carte reste visible en haut
 * et cadre le trajet sélectionné » veut dire quand deux éléments flottent par-dessus elle.
 */
class MapCameraInsetsTest {

  private val screen = 800.dp
  private val statusBar = 24.dp
  private val navigationBar = 16.dp

  @Test
  fun `sans rien qui flotte, seuls les encarts système comptent`() {
    val insets = insets(searchCard = 0.dp, sheet = 0.dp)
    assertEquals(statusBar, insets.top)
    assertEquals(navigationBar, insets.bottom)
  }

  @Test
  fun `la carte de recherche seule repousse le haut du cadrage`() {
    val insets = insets(searchCard = 180.dp, sheet = 0.dp)
    assertEquals(180.dp, insets.top)
    // Elle ne touche pas au bas : le bouton de position et l'attribution y restent chez eux.
    assertEquals(navigationBar, insets.bottom)
  }

  @Test
  fun `la feuille de résultats seule repousse le bas du cadrage`() {
    val insets = insets(searchCard = 0.dp, sheet = 300.dp)
    assertEquals(statusBar, insets.top)
    assertEquals(300.dp, insets.bottom)
  }

  @Test
  fun `les deux ensemble laissent le trajet dans la bande visible`() {
    val insets = insets(searchCard = 180.dp, sheet = 300.dp)
    assertEquals(180.dp, insets.top)
    assertEquals(300.dp, insets.bottom)
    assertTrue(insets.top + insets.bottom < screen)
  }

  @Test
  fun `un élément qui prend tout l'écran n'est plus un élément flottant`() {
    // Le champ de recherche actif passe en plein écran, clavier compris (SPEC.md § 5.1) : sa
    // hauteur ne doit pas être prise pour celle du bandeau replié.
    assertEquals(statusBar, insets(searchCard = screen, sheet = 0.dp).top)
    assertEquals(statusBar, insets(searchCard = 760.dp, sheet = 0.dp).top)
  }

  @Test
  fun `un élément ne confisque jamais plus de deux cinquièmes de la carte`() {
    // Une feuille dépliée aux trois cinquièmes reste un élément flottant, mais sa contribution au
    // cadrage est plafonnée : sans quoi il ne resterait rien à cadrer.
    assertEquals(screen * 0.4f, insets(searchCard = 0.dp, sheet = 480.dp).bottom)
  }

  @Test
  fun `tant que rien n'est mesuré, les hauteurs sont reprises telles quelles`() {
    val insets = mapCameraInsets(statusBar, navigationBar, 180.dp, 300.dp, screenHeight = 0.dp)
    assertEquals(180.dp, insets.top)
    assertEquals(300.dp, insets.bottom)
  }

  // --- Bornage final, en pixels (SPEC.md § 5.7, règle 9) --------------------------------------

  @Test
  fun `un remplissage qui tient dans la carte n'est pas touché`() {
    val padding = CameraPadding(left = 0, top = 200, right = 0, bottom = 300)
    assertEquals(padding, padding.fittedInto(width = 1080f, height = 1920f))
  }

  @Test
  fun `un remplissage qui déborde est réduit proportionnellement, jamais annulé`() {
    // Carte de recherche et feuille dépliée revendiquant ensemble plus que l'écran : il n'existe
    // alors aucune échelle à laquelle une emprise tienne.
    val padding = CameraPadding(left = 0, top = 800, right = 0, bottom = 1600)
    val fitted = padding.fittedInto(width = 1080f, height = 1920f)

    // Il reste un cinquième de la hauteur pour le trajet.
    assertEquals(1536, fitted.top + fitted.bottom)
    assertTrue(fitted.top > 0 && fitted.bottom > 0)
    // Le rapport entre le haut et le bas est conservé : le trajet reste cadré à la même place.
    assertEquals(padding.top.toDouble() / padding.bottom, fitted.top.toDouble() / fitted.bottom, 0.01)
  }

  @Test
  fun `une carte pas encore mesurée ne reçoit aucun remplissage`() {
    val padding = CameraPadding(left = 10, top = 200, right = 10, bottom = 300)
    assertEquals(CameraPadding(0, 0, 0, 0), padding.fittedInto(width = 0f, height = 0f))
  }

  private fun insets(searchCard: Dp, sheet: Dp) = mapCameraInsets(
    systemTop = statusBar,
    systemBottom = navigationBar,
    searchCardHeight = searchCard,
    resultsSheetHeight = sheet,
    screenHeight = screen,
  )
}
