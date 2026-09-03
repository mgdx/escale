package io.github.mgdx.escale.work

import io.github.mgdx.escale.core.model.WatchNotice

/**
 * L'émission des notifications de surveillance, vue par ceux qui la demandent (SPEC.md § 5.5.1).
 *
 * Même motif que [WatchAlarms] : l'écran d'activation et la tâche de fond n'ont pas à connaître le
 * gestionnaire de notifications d'Android pour être vérifiables. L'unique implémentation est
 * [WatchNotifications].
 */
interface WatchNotifier {
  /** Vrai si une notification peut être émise : permission accordée **et** canal actif. */
  fun canNotify(): Boolean

  /**
   * Émet la notification de [notice] pour le trajet [journeyId].
   *
   * @return faux quand elle n'a pas pu partir, ce qui n'est pas une erreur : le résultat est alors
   *   montré dans l'application (SPEC.md § 5.5.1).
   */
  fun notify(journeyId: Long, notice: WatchNotice, use24Hour: Boolean): Boolean

  /** Retire la notification d'un trajet dont la surveillance vient d'être arrêtée. */
  fun cancel(journeyId: Long)
}
