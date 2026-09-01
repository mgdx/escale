package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg

/**
 * Une portion de la frise horizontale d'une carte de résultat (SPEC.md § 5.2).
 *
 * [weight] est la part de largeur que la portion occupe, entre 0 et 1. La somme des parts d'une
 * frise vaut toujours 1 : l'interface les pose telles quelles en `Modifier.weight`.
 */
data class TimelineSegment(val leg: JourneyLeg, val weight: Float)

/**
 * La frise des portions d'un trajet, **à l'échelle de leur durée** (SPEC.md § 5.2).
 *
 * La règle n'est pas une simple proportion : une correspondance d'une minute au milieu d'un trajet
 * d'une heure vaudrait moins d'un point de pourcentage, c'est-à-dire un trait invisible. Chaque
 * portion reçoit donc un plancher, et la place ainsi réservée est reprise aux portions qui ont de
 * la marge, au prorata de ce qui leur reste au-dessus du plancher. Les rapports de durée entre les
 * portions longues sont conservés, et les portions courtes restent visibles.
 *
 * Ce calcul est ici, et non dans un composable, parce qu'il est vérifiable en JVM
 * (docs/architecture.md § 1).
 */
object JourneyTimeline {

  /** Part minimale d'une portion : en deçà, le segment n'est plus lisible à l'écran. */
  const val MINIMUM_WEIGHT: Float = 0.06f

  /** Les portions de [journey], dans l'ordre, avec leur part de largeur. */
  fun of(journey: Journey): List<TimelineSegment> = of(journey.legs)

  /** Voir [of] : la variante qui prend directement les portions, pour les tests et les aperçus. */
  fun of(legs: List<JourneyLeg>): List<TimelineSegment> {
    if (legs.isEmpty()) return emptyList()
    val equalShare = 1.0 / legs.size
    val minimum = minOf(MINIMUM_WEIGHT.toDouble(), equalShare)
    val seconds = legs.map { maxOf(it.duration.seconds, 0L).toDouble() }
    val total = seconds.sum()

    // Des portions toutes de durée nulle — cela arrive sur un trajet dégénéré — se partagent la
    // frise à parts égales plutôt que de faire une division par zéro.
    val shares = if (total > 0.0) seconds.map { it / total } else List(legs.size) { equalShare }
    val deficit = shares.sumOf { maxOf(minimum - it, 0.0) }
    val surplus = shares.sumOf { maxOf(it - minimum, 0.0) }

    // Aucune portion n'a de marge : le plancher ne peut être servi par personne, tout le monde
    // reçoit la même part. C'est le cas d'un trajet de plus de seize portions.
    if (surplus <= 0.0) return legs.map { TimelineSegment(it, equalShare.toFloat()) }

    return legs.mapIndexed { index, leg ->
      val share = shares[index]
      val weight = if (share <= minimum) minimum else share - deficit * (share - minimum) / surplus
      TimelineSegment(leg, weight.toFloat())
    }
  }
}
