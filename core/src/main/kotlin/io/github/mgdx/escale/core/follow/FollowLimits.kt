package io.github.mgdx.escale.core.follow

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import java.time.Duration
import java.time.Instant

/**
 * Les bornes du suivi de trajet (SPEC.md § 5.3.1) : quand il peut commencer, quand il doit finir.
 *
 * Elles vivent ici parce qu'elles sont arithmétiques et qu'un service ne se teste pas en JVM.
 */
object FollowLimits {

  private const val START_WINDOW_MINUTES = 60L
  private const val GRACE_MINUTES = 30L

  /** « Démarre dans moins de 60 minutes » : au-delà, le bouton n'est pas proposé. */
  val START_WINDOW: Duration = Duration.ofMinutes(START_WINDOW_MINUTES)

  /** « Au plus tard 30 minutes après l'arrivée connue au lancement » : passée cette borne, le suivi s'arrête. */
  val GRACE_AFTER_ARRIVAL: Duration = Duration.ofMinutes(GRACE_MINUTES)

  /**
   * Le bouton « Suivre ce trajet » est-il proposé pour [journey] à l'instant [now] ?
   *
   * Au moins une portion en transport en commun, aucune portion annulée, un trajet qui n'est pas
   * terminé, et un départ dans moins d'une heure — ou déjà passé : un suivi peut commencer en route.
   */
  fun canStart(journey: Journey, now: Instant): Boolean = journey.legs.any { it is JourneyLeg.Transit } &&
    !journey.hasCancelledLeg &&
    journey.endTime.isAfter(now) &&
    journey.startTime.isBefore(now.plus(START_WINDOW))

  /** L'instant au-delà duquel le service s'arrête quoi qu'il arrive, fixé une fois au lancement. */
  fun deadline(plan: FollowPlan): Instant = plan.end.plus(GRACE_AFTER_ARRIVAL)
}
