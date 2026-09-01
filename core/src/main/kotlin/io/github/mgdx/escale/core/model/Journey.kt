package io.github.mgdx.escale.core.model

import java.time.Duration
import java.time.Instant

/**
 * Un trajet proposé, décalque du schéma `Itinerary` de l'OpenAPI MOTIS.
 *
 * L'API ne donne pas d'horaire théorique au niveau de l'itinéraire : [scheduledStartTime] et
 * [scheduledEndTime] se déduisent de la première et de la dernière portion. C'est ce couple qui
 * permet d'afficher le retard global (SPEC.md § 5.2).
 */
data class Journey(
  /**
   * Identifiant opaque de l'itinéraire, à repasser à `/api/v6/refresh-itinerary`. Marqué
   * « expérimental » côté MOTIS : son format peut changer, et il peut devenir invalide après une
   * mise à jour d'horaires. Nul si le serveur ne l'a pas fourni.
   */
  val id: String?,
  val startTime: Instant,
  val endTime: Instant,
  val scheduledStartTime: Instant,
  val scheduledEndTime: Instant,
  val duration: Duration,
  /** Nombre de correspondances, tel que compté par le serveur. */
  val transfers: Int,
  val legs: List<JourneyLeg>,
) {
  /** Vrai dès qu'une portion est annulée : le trajet ne doit alors pas être présenté comme faisable. */
  val hasCancelledLeg: Boolean
    get() = legs.any { it.cancelled }

  /** Toutes les perturbations du trajet, dans l'ordre des portions. */
  val alerts: List<Disruption>
    get() = legs.flatMap { it.alerts }
}
