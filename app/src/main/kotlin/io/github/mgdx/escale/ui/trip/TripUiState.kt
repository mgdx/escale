package io.github.mgdx.escale.ui.trip

import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.Disruptions
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.calls
import io.github.mgdx.escale.core.result.EscaleError
import java.time.Instant

/**
 * L'état de l'écran « détail de la course » (SPEC.md § 5.3).
 *
 * [journey] nul signifie « pas encore reçu » : l'écran affiche alors le titre que la navigation lui
 * a remis et un indicateur de chargement, jamais une desserte vide, qui affirmerait faussement que
 * la course ne dessert rien.
 */
data class TripUiState(
  /** Ligne annoncée par l'appelant, remplacée par celle du serveur dès qu'elle arrive. */
  val lineName: String = "",
  /** Direction annoncée par l'appelant, remplacée par celle du serveur dès qu'elle arrive. */
  val headsign: String = "",
  val loading: Boolean = false,
  /** Vrai pendant un rafraîchissement : la desserte reste affichée (SPEC.md § 8). */
  val refreshing: Boolean = false,
  val journey: Journey? = null,
  val error: EscaleError? = null,
  /**
   * Heure du dernier chargement réussi. Elle sert au seuil de 60 secondes de SPEC.md § 7.4 et à
   * dater le bandeau de perturbations, pour qu'il ne change pas au fil des secondes.
   */
  val loadedAt: Instant? = null,
) {
  /**
   * La portion qui porte la course.
   *
   * `/api/v6/trip` rend la course sous la forme d'un itinéraire ; avec `joinInterlinedLegs` à sa
   * valeur par défaut, elle tient en une seule portion. La première portion en transport en commun
   * est donc celle qui nomme la ligne, la girouette et le transporteur.
   */
  val leg: JourneyLeg.Transit?
    get() = journey?.legs?.filterIsInstance<JourneyLeg.Transit>()?.firstOrNull()

  /**
   * La desserte complète, terminus compris.
   *
   * Le recollement des trois champs de l'API est dans `:core` (`Journey.calls`), où il se vérifie
   * en JVM : cet écran ne fait que l'afficher.
   */
  val calls: List<StopVisit>
    get() = journey?.calls.orEmpty()

  /** Les perturbations de la course, pour le bandeau. */
  val alerts: List<Disruption>
    get() = journey?.alerts.orEmpty()

  /**
   * Les perturbations propres à [call] : celles que le bandeau de la course n'annonce pas déjà.
   *
   * `/api/v6/trip` rend des `alerts` dans le `place` de **chaque** arrêt, et pas seulement au niveau
   * de la course : c'est là qu'un réseau annonce qu'un arrêt précis n'est pas desservi. Les
   * mélanger au bandeau de tête les noierait ; les taire perdrait l'information.
   *
   * Le tri est dans `:core` (`Disruptions.excluding`), où il se vérifie en JVM : cet écran ne fait
   * que l'afficher.
   */
  fun alertsAt(call: StopVisit): List<Disruption> = Disruptions.excluding(call.place.alerts, alerts)
}
