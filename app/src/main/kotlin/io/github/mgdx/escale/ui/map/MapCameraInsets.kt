package io.github.mgdx.escale.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Ce que les éléments flottants cachent de la carte (SPEC.md § 5.1 et § 5.7, règle 9).
 *
 * L'écran d'accueil **est** la carte, et tout le reste flotte par-dessus : la carte de recherche en
 * haut, la feuille de résultats en bas. La règle 9 ne nomme que la feuille, mais l'intention du
 * § 5.1 est que « la carte reste visible en haut et cadre le trajet sélectionné » : un trajet caché
 * derrière un bandeau opaque n'y répond pas davantage qu'un trajet caché sous la feuille. Les deux
 * entrent donc dans le remplissage de la caméra, de la même façon.
 *
 * Le calcul est ici, hors de tout composable et sans dépendance Android, pour être vérifiable en
 * JVM (docs/architecture.md § 1) : c'est lui qui décide si un trajet sera visible ou non.
 */

/** Le remplissage vertical de la caméra : ce que les éléments flottants recouvrent, en dp. */
data class MapCameraInsets(val top: Dp, val bottom: Dp)

/**
 * Le remplissage vertical que la caméra doit prendre.
 *
 * Chaque élément flottant ne compte que pour ce qu'il cache **en plus** de l'encart système, d'où
 * les maximums plutôt que des sommes : la carte de recherche se place déjà sous la barre d'état, et
 * la feuille de résultats au-dessus de la barre de navigation.
 *
 * @param screenHeight hauteur de la carte, qui sert à démêler un élément flottant d'un écran entier.
 *   Nulle tant que rien n'est mesuré : les hauteurs sont alors reprises telles quelles.
 */
fun mapCameraInsets(
  systemTop: Dp,
  systemBottom: Dp,
  searchCardHeight: Dp,
  resultsSheetHeight: Dp,
  screenHeight: Dp,
): MapCameraInsets = MapCameraInsets(
  top = maxOf(systemTop, floatingHeight(searchCardHeight, screenHeight)),
  bottom = maxOf(systemBottom, floatingHeight(resultsSheetHeight, screenHeight)),
)

/**
 * La part de carte qu'un élément flottant recouvre vraiment.
 *
 * Deux cas s'écartent de la mesure brute :
 *
 * - **un élément qui occupe presque tout l'écran n'en est plus un.** Le champ de recherche actif
 *   passe en plein écran, clavier compris (SPEC.md § 5.1), et sa hauteur est alors celle de
 *   l'écran : la prendre pour celle du bandeau replié rendrait le cadrage absurde. La carte est de
 *   toute façon invisible à ce moment-là, et la mesure du bandeau revient dès que le champ se
 *   referme ;
 * - **un élément ne peut pas confisquer toute la carte.** Une feuille de résultats dépliée à fond
 *   ne laisserait plus rien à cadrer : sa contribution est plafonnée, et le bornage final de
 *   [fittedInto] achève de garantir qu'un trajet reste cadrable.
 */
private fun floatingHeight(measured: Dp, screenHeight: Dp): Dp = when {
  screenHeight <= 0.dp -> measured
  measured > screenHeight * FULL_SCREEN_RATIO -> 0.dp
  else -> minOf(measured, screenHeight * MAX_FLOATING_RATIO)
}

/** Le remplissage de la caméra, en pixels, tel que MapLibre l'attend. */
data class CameraPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Réduit le remplissage jusqu'à ce qu'il laisse de quoi cadrer, sans jamais l'annuler d'un côté.
 *
 * Un cadrage d'emprise cherche l'échelle à laquelle le trajet tient dans la zone **restante** :
 * si les éléments flottants revendiquent plus que l'écran, cette zone est vide et il n'existe plus
 * aucune échelle qui convienne. Plutôt que de rendre le cadrage impossible, les remplissages d'un
 * même axe sont réduits **proportionnellement** : leur rapport est conservé, donc le trajet reste
 * cadré à la même place, simplement plus au large.
 */
fun CameraPadding.fittedInto(width: Float, height: Float): CameraPadding {
  val horizontal = shrinkFactor(left + right, width)
  val vertical = shrinkFactor(top + bottom, height)
  return CameraPadding(
    left = (left * horizontal).roundToInt(),
    top = (top * vertical).roundToInt(),
    right = (right * horizontal).roundToInt(),
    bottom = (bottom * vertical).roundToInt(),
  )
}

/** Le facteur par lequel réduire les deux remplissages d'un axe, ou 1 si rien ne déborde. */
private fun shrinkFactor(total: Int, dimension: Float): Float {
  if (dimension <= 0f) return 0f
  val room = dimension * MAX_PADDING_RATIO
  return if (total <= 0 || total <= room) 1f else room / total
}

/** Au-delà, ce n'est plus un élément flottant posé sur la carte : c'est un écran. */
private const val FULL_SCREEN_RATIO = 0.7f

/** Part de la carte qu'un seul élément flottant peut revendiquer dans le cadrage. */
private const val MAX_FLOATING_RATIO = 0.4f

/** Part d'un axe que les deux remplissages réunis peuvent consommer : il reste un cinquième. */
private const val MAX_PADDING_RATIO = 0.8f
