package io.github.mgdx.escale.core.model

/**
 * Le trajet dont les extrémités anonymes portent enfin un nom (SPEC.md § 5.3).
 *
 * Une adresse envoyée au serveur en coordonnées revient sans nom : MOTIS y pose ses marqueurs
 * internes, que `:data` traduit en chaîne vide. Le seul endroit où le vrai libellé existe encore
 * est **ce que l'usager a saisi** — c'est lui que cette fonction repose sur les deux bouts du
 * trajet, et lui seul : les arrêts intermédiaires, eux, sont nommés par le serveur.
 *
 * Un nom déjà renseigné n'est jamais écrasé : quelqu'un qui a choisi « Gare de Lyon » dans
 * l'autocomplétion doit lire le nom officiel de l'arrêt que le serveur lui a effectivement servi,
 * pas sa propre saisie.
 *
 * @param origin le libellé du départ saisi par l'usager, ou `null` s'il n'est plus disponible.
 * @param destination le libellé de l'arrivée, même règle.
 */
fun Journey.withEndpointNames(origin: String?, destination: String?): Journey {
  if (legs.isEmpty()) return this
  val updated = legs.toMutableList()
  val first = updated.first()
  if (first.from.name.isBlank() && !origin.isNullOrBlank()) {
    updated[0] = first.withEndpoints(from = first.from.copy(name = origin.trim()))
  }
  // Relu après la première substitution : sur un trajet d'une seule portion, les deux extrémités
  // appartiennent à la même portion et la seconde doit partir de la copie, pas de l'original.
  val last = updated.last()
  if (last.to.name.isBlank() && !destination.isNullOrBlank()) {
    updated[updated.lastIndex] = last.withEndpoints(to = last.to.copy(name = destination.trim()))
  }
  return copy(legs = updated)
}

/** Une copie de la portion avec d'autres extrémités, quel que soit son sous-type. */
private fun JourneyLeg.withEndpoints(from: Place = this.from, to: Place = this.to): JourneyLeg = when (this) {
  is JourneyLeg.Transit -> copy(from = from, to = to)
  is JourneyLeg.Walk -> copy(from = from, to = to)
  is JourneyLeg.Bike -> copy(from = from, to = to)
  is JourneyLeg.Car -> copy(from = from, to = to)
  is JourneyLeg.Rental -> copy(from = from, to = to)
}
