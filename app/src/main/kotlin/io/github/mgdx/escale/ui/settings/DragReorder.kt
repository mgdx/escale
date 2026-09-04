package io.github.mgdx.escale.ui.settings

/**
 * La géométrie d'un glissé-déposé vertical, en pixels : où tombe la ligne saisie, et de combien les
 * autres se décalent pour lui faire place.
 *
 * Deux motifs pour l'isoler ici, hors de tout composable :
 *
 * - c'est du calcul, et le calcul se vérifie en JVM (`DragReorderTest`) — un test d'interface ne
 *   dirait pas au dixième de pixel près où la ligne atterrit ;
 * - les lignes n'ont **pas toutes la même hauteur**. Un libellé long passe à deux lignes dès que
 *   l'usager agrandit le texte, et la formule « décalage divisé par la hauteur d'une ligne » qu'on
 *   écrit d'ordinaire donnerait alors la mauvaise cible (SPEC.md § 9).
 *
 * Rien n'est déplacé pendant le geste : la liste ne change qu'au lâcher. Ce qui bouge sous le doigt
 * est un décalage d'affichage, jamais un ordre à moitié appliqué.
 */
internal object DragReorder {

  /**
   * Le rang où atterrit la ligne de rang [from] traînée de [offset] pixels, vers le bas si le
   * décalage est positif.
   *
   * La ligne franchit un rang quand elle a dépassé **la moitié** de la ligne voisine : c'est le
   * seuil habituel, celui qui fait basculer la liste au moment où l'œil s'y attend.
   */
  fun targetIndex(heights: List<Int>, from: Int, offset: Float): Int {
    // Une hauteur nulle est une ligne pas encore mesurée : on ne déplace rien tant qu'on ne sait
    // pas de combien. Sans cette garde, le premier seuil serait franchi dès le premier pixel.
    if (from !in heights.indices || heights.any { it <= 0 }) return from
    // Un seul sens par geste, décidé par le signe du décalage. Enchaîner les deux parcours
    // laisserait le second défaire ce que le premier vient de franchir.
    return if (offset > 0) descending(heights, from, offset) else ascending(heights, from, offset)
  }

  private fun descending(heights: List<Int>, from: Int, offset: Float): Int {
    var index = from
    var remaining = offset
    while (index + 1 in heights.indices && remaining > heights[index + 1] / 2f) {
      remaining -= heights[index + 1]
      index++
    }
    return index
  }

  private fun ascending(heights: List<Int>, from: Int, offset: Float): Int {
    var index = from
    var remaining = offset
    while (index - 1 in heights.indices && -remaining > heights[index - 1] / 2f) {
      remaining += heights[index - 1]
      index--
    }
    return index
  }

  /**
   * De combien de pixels la ligne de rang [index] s'écarte pour laisser passer celle de rang
   * [from], partie s'installer au rang [target].
   *
   * Seules les lignes enjambées bougent, et toutes de la même quantité : la hauteur de la ligne
   * traînée. Les autres, y compris la ligne traînée elle-même — que le doigt porte déjà — ne
   * bougent pas.
   */
  fun shift(heights: List<Int>, from: Int, target: Int, index: Int): Float {
    if (from !in heights.indices || index == from) return 0f
    val height = heights[from].toFloat()
    return when {
      index in (from + 1)..target -> -height
      index in target until from -> height
      else -> 0f
    }
  }
}
