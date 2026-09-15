package io.github.mgdx.escale.core.follow

import java.time.Instant

/** Une échéance du suivi : l'instant, l'état qui vaut à partir de lui, et l'alerte à émettre, s'il y en a une. */
data class FollowEvent(val at: Instant, val state: FollowState, val alert: FollowAlert? = null)

/**
 * **La règle de progression du suivi de trajet (SPEC.md § 5.3.1), et le seul endroit qui la porte.**
 *
 * « Les instants qui déclenchent une mise à jour ou une alerte sont exactement : le départ de chaque
 * portion, le départ de chaque arrêt intermédiaire, l'arrivée de chaque portion. Entre deux
 * échéances, rien ne s'exécute. »
 *
 * Tout est déduit des heures du plan, jamais d'une position. Le service ne fait qu'appeler
 * [stateAt] à chaque réveil, dormir jusqu'à [nextAfter], et émettre les alertes que [alertsBetween]
 * lui rend. Les échéances déjà passées quand le suivi commence ne sont jamais rejouées : c'est le
 * service qui borne [alertsBetween] à l'instant du lancement.
 */
object FollowTimeline {

  /** Le seuil de l'alerte « Descente dans N arrêts » : fixe en v1 (SPEC.md § 5.3.1). */
  const val STOPS_BEFORE_ALERT = 3

  /** Toutes les échéances de [plan], par ordre chronologique. */
  fun of(plan: FollowPlan): List<FollowEvent> {
    val events = mutableListOf<FollowEvent>()
    plan.legs.forEachIndexed { index, leg ->
      when (leg) {
        is FollowLeg.Transit -> events += transitEvents(index, leg)
        is FollowLeg.Street -> events += FollowEvent(leg.start, connecting(plan, index))
      }
      events += endEvent(plan, index, leg)
    }
    return mergeSimultaneous(events.sortedBy { it.at })
  }

  /** L'état qui vaut à [now] : celui de la dernière échéance atteinte, ou l'attente du départ. */
  fun stateAt(plan: FollowPlan, events: List<FollowEvent>, now: Instant): FollowState =
    events.lastOrNull { !it.at.isAfter(now) }?.state ?: waiting(plan)

  /** La première échéance strictement postérieure à [now], ou `null` s'il n'en reste aucune. */
  fun nextAfter(events: List<FollowEvent>, now: Instant): FollowEvent? = events.firstOrNull { it.at.isAfter(now) }

  /** Les alertes des échéances comprises dans `]after, until]`, dans l'ordre. */
  fun alertsBetween(events: List<FollowEvent>, after: Instant, until: Instant): List<FollowAlert> =
    events.filter { it.at.isAfter(after) && !it.at.isAfter(until) }.mapNotNull { it.alert }

  /** L'état d'avant le départ. */
  fun waiting(plan: FollowPlan): FollowState.Waiting =
    FollowState.Waiting(first = plan.legs.first(), firstTransit = nextTransitFrom(plan, 0))

  private fun transitEvents(index: Int, leg: FollowLeg.Transit): List<FollowEvent> {
    // Seuls les arrêts desservis comptent : un arrêt supprimé ne s'annonce pas et ne se compte pas.
    val served = leg.stops.withIndex().filter { !it.value.cancelled }
    val events = mutableListOf<FollowEvent>()
    // La montée : tous les arrêts restent à atteindre, descente comprise.
    events +=
      onBoard(index, leg, nextStopIndex = served.firstOrNull()?.index, remaining = served.size + 1, at = leg.start)
    served.forEachIndexed { position, (_, stop) ->
      val remaining = served.size - position
      val next = served.getOrNull(position + 1)?.index
      events += onBoard(index, leg, nextStopIndex = next, remaining = remaining, at = stop.time)
    }
    return events
  }

  private fun onBoard(index: Int, leg: FollowLeg.Transit, nextStopIndex: Int?, remaining: Int, at: Instant) =
    FollowEvent(
      at = at,
      state = FollowState.OnBoard(
        legIndex = index,
        leg = leg,
        nextStopIndex = nextStopIndex,
        stopsRemaining = remaining,
      ),
      alert = when (remaining) {
        STOPS_BEFORE_ALERT -> FollowAlert.StopsBefore(stops = remaining, stopName = leg.toName)
        1 -> FollowAlert.NextStop(stopName = leg.toName)
        else -> null
      },
    )

  private fun endEvent(plan: FollowPlan, index: Int, leg: FollowLeg): FollowEvent {
    if (index == plan.legs.lastIndex) {
      return FollowEvent(leg.end, FollowState.Arrived, FollowAlert.Arrived(destinationName = leg.toName))
    }
    val then = connecting(plan, index + 1)
    val alert = if (leg is FollowLeg.Transit) FollowAlert.Alight(stopName = leg.toName, then = then) else null
    return FollowEvent(leg.end, then, alert)
  }

  /**
   * L'état « entre deux véhicules » à partir de la portion [index] : elle-même si c'est un
   * cheminement, sinon l'attente sur place du véhicule qu'elle est.
   */
  private fun connecting(plan: FollowPlan, index: Int): FollowState.Connecting {
    val leg = plan.legs[index]
    return if (leg is FollowLeg.Street) {
      FollowState.Connecting(streetIndex = index, street = leg, nextTransit = nextTransitFrom(plan, index + 1))
    } else {
      FollowState.Connecting(streetIndex = null, street = null, nextTransit = leg as FollowLeg.Transit)
    }
  }

  private fun nextTransitFrom(plan: FollowPlan, index: Int): FollowLeg.Transit? =
    plan.legs.drop(index).firstOrNull { it is FollowLeg.Transit } as? FollowLeg.Transit

  /**
   * Deux échéances au même instant n'émettent qu'une alerte, la plus pressante (SPEC.md § 5.3.1).
   *
   * Le cas typique est une descente dont l'heure d'arrivée est celle du départ du dernier arrêt
   * intermédiaire : « Descente au prochain arrêt » et « Descendez ici » tomberaient ensemble, et la
   * seconde suffit. L'ordre des échéances est conservé, seules les alertes surnuméraires s'effacent.
   */
  private fun mergeSimultaneous(events: List<FollowEvent>): List<FollowEvent> {
    val kept = events
      .withIndex()
      .filter { it.value.alert != null }
      .groupBy { it.value.at }
      .values
      .map { simultaneous -> simultaneous.maxBy { it.value.alert!!.priority }.index }
      .toSet()
    return events.mapIndexed { index, event ->
      if (event.alert == null || index in kept) event else event.copy(alert = null)
    }
  }
}
