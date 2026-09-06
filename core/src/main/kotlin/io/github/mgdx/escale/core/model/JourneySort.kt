package io.github.mgdx.escale.core.model

/**
 * L'ordre dans lequel la liste d'un onglet est présentée (SPEC.md § 5.2).
 *
 * Le tri est **local** : il ne redemande rien au serveur. La liste accumulée par la pagination
 * reste recollée par heure de départ ([JourneyFeed]) — c'est elle qui fait autorité pour savoir
 * quel trajet vient d'arriver —, et ce choix ne décide que de sa présentation.
 */
enum class JourneySort {
  /** L'ordre d'arrivée des pages : par heure de départ. C'est le défaut. */
  DEPARTURE,

  /** Du trajet le plus court au plus long. */
  DURATION,

  /** Du trajet qui demande le moins de correspondances au plus, la durée départageant les ex æquo. */
  TRANSFERS,
}

/**
 * Les trajets de la liste, dans l'ordre demandé.
 *
 * Le tri est **stable** : à critère égal, l'ordre d'origine — celui des heures de départ — est
 * conservé, si bien que deux trajets d'une même durée restent dans l'ordre où ils partent.
 * [JourneySort.DEPARTURE] rend donc la liste telle quelle, sans même la recopier dans un autre
 * ordre : c'est déjà celui de [JourneyFeed].
 *
 * **Un trajet dont une portion est supprimée ne remonte jamais devant un trajet qui circule.** Sa
 * durée est celle d'un trajet qui n'aura pas lieu : le hisser en tête d'une liste ordonnée par
 * durée le présenterait comme la meilleure proposition, ce que `fastestDuration` refuse déjà de
 * faire sous la languette de l'onglet. Il garde en revanche sa place dans l'ordre des départs,
 * qui ne promet rien d'autre qu'une heure.
 */
fun JourneyFeed.sorted(sort: JourneySort): List<Journey> = when (sort) {
  JourneySort.DEPARTURE -> journeys
  JourneySort.DURATION -> journeys.sortedWith(compareBy({ it.hasCancelledLeg }, { it.duration }))
  JourneySort.TRANSFERS -> journeys.sortedWith(compareBy({ it.hasCancelledLeg }, { it.transfers }, { it.duration }))
}
