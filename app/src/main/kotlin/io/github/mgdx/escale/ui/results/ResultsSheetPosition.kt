package io.github.mgdx.escale.ui.results

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * Où la feuille de résultats se pose, et jusqu'où elle glisse (SPEC.md § 5.1).
 *
 * La règle est ici, hors de tout composable et sans dépendance Android, pour être vérifiable en
 * JVM comme celle du remplissage de la caméra (docs/architecture.md § 1) : c'est elle qui décide
 * de la part de carte laissée visible, donc de ce que l'usager voit du trajet.
 */

/**
 * Les trois hauteurs auxquelles la feuille s'arrête.
 *
 * [COLLAPSED] ne cache **que le détail** : la poignée, les onglets et les durées qu'ils annoncent
 * restent visibles, sans quoi l'usager perdrait la comparaison des modes en même temps que la
 * liste, et n'aurait plus rien pour rouvrir la feuille (SPEC.md § 5.2).
 */
enum class ResultsSheetPosition {
  COLLAPSED,
  HALF,
  EXPANDED,
}

/** La position suivante du cycle de l'appui sur la poignée, qui repasse par le repli. */
fun ResultsSheetPosition.next(): ResultsSheetPosition = when (this) {
  ResultsSheetPosition.COLLAPSED -> ResultsSheetPosition.HALF
  ResultsSheetPosition.HALF -> ResultsSheetPosition.EXPANDED
  ResultsSheetPosition.EXPANDED -> ResultsSheetPosition.COLLAPSED
}

/**
 * La hauteur de la feuille à une position donnée.
 *
 * @param headerHeight hauteur mesurée de la poignée et des onglets. Tant qu'elle est inconnue —
 *   la toute première image —, la feuille ne se replie pas plus bas que sa position intermédiaire :
 *   se replier à zéro ferait clignoter une feuille vide le temps d'une mesure.
 * @param available hauteur disponible pour la feuille, encarts système compris.
 */
fun resultsSheetHeight(position: ResultsSheetPosition, headerHeight: Dp, available: Dp): Dp {
  val half = available * HALF_FRACTION
  return when (position) {
    // Jamais plus haut que la position intermédiaire : à 200 % d'agrandissement, l'entête peut
    // devenir plus haut qu'elle, et « replié » doit rester la plus basse des trois positions.
    ResultsSheetPosition.COLLAPSED -> if (headerHeight <= 0.dp) half else minOf(headerHeight, half)

    ResultsSheetPosition.HALF -> half

    ResultsSheetPosition.EXPANDED -> available * EXPANDED_FRACTION
  }
}

/**
 * La position à laquelle la feuille se pose quand le doigt la lâche.
 *
 * Un geste franc emmène la feuille à la position suivante dans le sens du geste, même s'il est
 * court : c'est ce qui permet de replier d'une chiquenaude. En deçà, c'est la position la plus
 * proche qui l'emporte, pour qu'un déplacement lent finisse là où on l'a laissé.
 *
 * @param velocity vitesse de la **hauteur** de la feuille en dp par seconde : positive quand la
 *   feuille grandit, c'est-à-dire quand le doigt monte.
 */
fun settledResultsSheetPosition(height: Dp, velocity: Float, headerHeight: Dp, available: Dp): ResultsSheetPosition {
  val anchors = ResultsSheetPosition.entries.map { it to resultsSheetHeight(it, headerHeight, available) }
  val above = anchors.firstOrNull { (_, anchor) -> anchor > height }?.first ?: ResultsSheetPosition.EXPANDED
  val below = anchors.lastOrNull { (_, anchor) -> anchor < height }?.first ?: ResultsSheetPosition.COLLAPSED
  val nearest = anchors.minBy { (_, anchor) -> abs(anchor.value - height.value) }.first
  return when {
    velocity > FLING_VELOCITY -> above
    velocity < -FLING_VELOCITY -> below
    else -> nearest
  }
}

/** Position intermédiaire : la moitié basse de l'écran, la carte reste visible au-dessus. */
private const val HALF_FRACTION = 0.45f

/** Position dépliée : la carte reste visible, même réduite à une bande. */
private const val EXPANDED_FRACTION = 0.9f

/** Au-delà, en dp par seconde, le geste est franc et emporte la feuille d'une position. */
private const val FLING_VELOCITY = 200f
