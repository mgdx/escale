package io.github.mgdx.escale.core.model

/**
 * La **desserte complète** d'une course, telle que l'écran « détail de la course » l'affiche
 * (SPEC.md § 5.3, « appui sur la ligne → écran détail de la course avec la desserte complète »).
 *
 * `GET /api/v6/trip` rend la course sous la forme d'un itinéraire ordinaire : ses arrêts sont donc
 * répartis entre `from`, `intermediateStops` et `to` d'une portion, et `intermediateStops`
 * **exclut les deux extrémités** — vérifié sur `api.transitous.org`, où l'ICE 91 Altona →
 * Nuremberg rend neuf arrêts intermédiaires pour onze arrêts desservis. Recoller les trois est une
 * règle calculatoire : elle vit donc dans `:core`, où elle se vérifie en JVM
 * (docs/architecture.md § 1), et non dans un composable.
 *
 * Le paramètre `joinInterlinedLegs` vaut `true` par défaut côté serveur : une course ordinaire
 * revient en une seule portion. Un serveur qui en rendrait plusieurs — ou une course à changement
 * de numéro sans changement de véhicule — ne doit pas faire afficher deux fois l'arrêt de jonction,
 * d'où le recollement de [mergedWith].
 */
val Journey.calls: List<StopVisit>
  get() {
    val calls = mutableListOf<StopVisit>()
    legs.filterIsInstance<JourneyLeg.Transit>().flatMap { it.calls }.forEach { call ->
      val previous = calls.lastOrNull()
      if (previous != null && previous.isSameStopAs(call)) {
        calls[calls.lastIndex] = previous.mergedWith(call)
      } else {
        calls += call
      }
    }
    return calls
  }

/**
 * Les arrêts d'une portion en transport en commun, extrémités comprises.
 *
 * L'origine n'a pas d'heure d'arrivée et le terminus pas d'heure de départ : c'est la convention
 * de [StopVisit], et elle est vraie ici pour la même raison qu'ailleurs — on n'arrive pas à l'arrêt
 * d'où l'on part.
 *
 * `cancelled` des deux extrémités est celui de la portion : le schéma `Place` porte bien un champ
 * `cancelled`, mais le modèle de domaine ne le retient que sur un arrêt intermédiaire
 * (docs/architecture.md § 4). Une course annulée marque donc tous ses arrêts, ce qui est exact.
 */
val JourneyLeg.Transit.calls: List<StopVisit>
  get() = buildList {
    add(StopVisit(place = from, arrival = null, departure = from.time, cancelled = cancelled))
    addAll(intermediateStops)
    add(StopVisit(place = to, arrival = to.time, departure = null, cancelled = cancelled))
  }

/**
 * Deux passages décrivent-ils le même arrêt ?
 *
 * L'identifiant fait foi quand les deux en ont un. À défaut — une extrémité rendue sans `stopId`,
 * ce que le schéma autorise —, le nom et la position servent de repli : deux arrêts homonymes à
 * dix mètres l'un de l'autre sont le même quai, deux arrêts homonymes à deux kilomètres ne le
 * sont pas, et la comparaison exacte des coordonnées suffit ici puisqu'elles viennent du même
 * serveur, pour le même point, dans la même réponse.
 */
private fun StopVisit.isSameStopAs(other: StopVisit): Boolean {
  val id = place.stopId
  val otherId = other.place.stopId
  if (id != null && otherId != null) return id == otherId
  return place.name == other.place.name && place.coordinates == other.place.coordinates
}

/**
 * Recolle un arrêt de jonction : on y arrive par la portion précédente, on en repart par la
 * suivante. Le quai est celui que l'une des deux connaît, l'annulation vaut dès que l'une la porte.
 */
private fun StopVisit.mergedWith(next: StopVisit): StopVisit = copy(
  place = if (place.track == null) place.copy(track = next.place.track) else place,
  arrival = arrival ?: next.arrival,
  departure = next.departure ?: departure,
  cancelled = cancelled || next.cancelled,
)
