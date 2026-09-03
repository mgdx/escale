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
  top = maxOf(systemTop, searchCardCover(searchCardHeight, screenHeight)),
  bottom = maxOf(systemBottom, sheetCover(resultsSheetHeight, screenHeight)),
)

/**
 * La part de carte que la carte de recherche recouvre vraiment.
 *
 * **Un élément qui occupe presque tout l'écran n'en est plus un.** Le champ de recherche actif
 * passe en plein écran, clavier compris (SPEC.md § 5.1), et sa hauteur est alors celle de l'écran :
 * la prendre pour celle du bandeau replié rendrait le cadrage absurde. La carte est de toute façon
 * invisible à ce moment-là, et la mesure du bandeau revient dès que le champ se referme.
 *
 * En deçà, la hauteur mesurée est reprise **telle quelle**. La plafonner reviendrait à dire à la
 * caméra que le bandeau cache moins qu'il ne cache, et le tracé passerait dessous : c'est le
 * bornage final de [fittedInto], et lui seul, qui garantit qu'il reste de quoi cadrer.
 */
private fun searchCardCover(measured: Dp, screenHeight: Dp): Dp = when {
  screenHeight <= 0.dp -> measured
  measured > screenHeight * FULL_SCREEN_RATIO -> 0.dp
  else -> measured
}

/**
 * La part de carte que la feuille de résultats recouvre vraiment.
 *
 * La hauteur mesurée est reprise telle quelle jusqu'à [MAX_SHEET_RATIO], **au-delà de la hauteur
 * de la feuille repliée** : sous-estimer ce que la feuille cache faisait glisser la fin du trajet
 * — son marqueur d'arrivée compris — juste sous son bord, anomalie vue sur téléphone.
 *
 * Le plafond ne vaut donc que pour la feuille dépliée, qui ne peut pas confisquer toute la carte :
 * sans lui, il ne resterait rien à cadrer, et le bornage de [fittedInto] devrait tout rattraper.
 */
private fun sheetCover(measured: Dp, screenHeight: Dp): Dp = when {
  screenHeight <= 0.dp -> measured
  else -> minOf(measured, screenHeight * MAX_SHEET_RATIO)
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

/**
 * Part de la carte que la feuille de résultats peut revendiquer dans le cadrage.
 *
 * Au-dessus de la hauteur de la feuille repliée — 45 % de l'écran (`ResultsSheetSlot`) —, pour que
 * celle-ci soit prise en compte pour ce qu'elle est ; en dessous de la feuille dépliée, à qui on
 * ne laisse pas toute la carte.
 */
private const val MAX_SHEET_RATIO = 0.5f

/** Part d'un axe que les deux remplissages réunis peuvent consommer : il reste un cinquième. */
private const val MAX_PADDING_RATIO = 0.8f
