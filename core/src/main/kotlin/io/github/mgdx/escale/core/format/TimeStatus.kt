package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg

/**
 * Ce que l'interface a le droit d'annoncer sur l'horaire d'un trajet ou d'une portion
 * (SPEC.md § 5.2).
 *
 * [departure] et [arrival] sont nuls quand la portion ne porte **aucune donnée temps réel**. C'est
 * la distinction que SPEC.md § 5.2 impose et qu'un simple `Duration.between` ne fait pas : sans
 * temps réel, l'heure effective vaut l'horaire théorique par construction, si bien qu'un écart nul
 * ne prouve rien. Annoncer « à l'heure » dans ce cas serait un mensonge, et colorer en vert une
 * information qu'on n'a pas l'est tout autant.
 *
 * [cancelled] passe devant tout le reste : un retard sur une portion supprimée n'a aucun sens.
 */
data class TimeStatus(val cancelled: Boolean, val departure: Delay?, val arrival: Delay?) {

  /** Vrai si le serveur a donné une information temps réel : c'est la seule condition pour colorer. */
  val hasRealTime: Boolean
    get() = departure != null || arrival != null

  companion object {
    /** Le statut d'une portion. */
    fun of(leg: JourneyLeg): TimeStatus = TimeStatus(
      cancelled = leg.cancelled,
      departure = Delay.between(leg.startTime, leg.scheduledStartTime, leg.realTime),
      arrival = Delay.between(leg.endTime, leg.scheduledEndTime, leg.realTime),
    )

    /**
     * Le statut d'un trajet entier.
     *
     * Les horaires théoriques du trajet se déduisent de sa première et de sa dernière portion : ce
     * sont donc ces deux portions, et elles seules, qui disent si le départ et l'arrivée sont
     * couverts par le temps réel. Un trajet dont seule une portion intermédiaire est suivie ne
     * s'affiche pas « à l'heure » au départ.
     */
    fun of(journey: Journey): TimeStatus = TimeStatus(
      cancelled = journey.hasCancelledLeg,
      departure = journey.legs.firstOrNull()?.let {
        Delay.between(journey.startTime, journey.scheduledStartTime, it.realTime)
      },
      arrival = journey.legs.lastOrNull()?.let {
        Delay.between(journey.endTime, journey.scheduledEndTime, it.realTime)
      },
    )
  }
}
