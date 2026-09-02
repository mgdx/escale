package io.github.mgdx.escale.core.model

/**
 * Les instructions pas-à-pas d'une portion, quel que soit son sous-type (SPEC.md § 5.3).
 *
 * Les quatre portions de rue — [JourneyLeg.Walk], [JourneyLeg.Bike], [JourneyLeg.Car] et
 * [JourneyLeg.Rental] — portent chacune leur propre champ `steps`, sans propriété commune sur
 * l'interface : les manœuvres n'ont aucun sens sur une portion en transport en commun, où c'est le
 * véhicule qui conduit. Cette lecture unifiée évite à l'écran de détail de refaire le même `when`
 * à chaque endroit où il affiche un cheminement.
 *
 * Une portion en transport en commun rend une liste vide, jamais `null` : l'appelant n'a pas à
 * distinguer « pas de manœuvre » de « pas de manœuvre possible ».
 */
val JourneyLeg.travelSteps: List<TravelStep>
  get() = when (this) {
    is JourneyLeg.Walk -> steps
    is JourneyLeg.Bike -> steps
    is JourneyLeg.Car -> steps
    is JourneyLeg.Rental -> steps
    is JourneyLeg.Transit -> emptyList()
  }
