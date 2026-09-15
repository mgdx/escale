package io.github.mgdx.escale.follow

import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.core.model.Journey
import kotlinx.coroutines.flow.StateFlow

/** Le trajet suivi et où en est l'usager, tels que la notification et l'écran de détail les voient. */
data class FollowSession(val plan: FollowPlan, val state: FollowState) {

  /**
   * Vrai si [journey] est le trajet suivi.
   *
   * L'identifiant d'itinéraire fait foi quand il existe : un repli sur `plan` (SPEC.md § 5.5.1)
   * peut en changer, et c'est alors le plan, remplacé sous la même clé, qui porte le nouveau.
   */
  fun covers(journey: Journey): Boolean {
    val itineraryId = plan.itineraryId
    return if (itineraryId != null) itineraryId == journey.id else plan.key == FollowPlan.keyOf(journey)
  }
}

/**
 * Ce que l'écran de détail sait du suivi de trajet (SPEC.md § 5.3.1), et rien de plus.
 *
 * L'interface existe pour que `DetailViewModel` se teste en JVM avec un double : la vraie
 * implémentation, [JourneyFollower], démarre un service Android.
 */
interface JourneyFollowing {
  /** Le suivi en cours, `null` quand il n'y en a pas. */
  val session: StateFlow<FollowSession?>

  /** Suit [journey], en remplaçant tout suivi en cours. */
  fun start(journey: Journey)

  /**
   * Met à jour le trajet suivi sous la clé [key] avec les heures de [journey], rafraîchies.
   *
   * Ne fait rien si ce n'est pas ce trajet-là qui est suivi : le suivi ne change de trajet que par
   * [start], jamais par un rafraîchissement.
   */
  fun replace(key: String, journey: Journey)

  fun stop()

  /**
   * L'identifiant d'itinéraire du trajet suivi, **une seule fois**, quand l'écran de détail s'ouvre
   * depuis la notification alors que le trajet lui-même n'est plus en mémoire — le processus est
   * mort entre-temps. `null` dans tous les autres cas : l'écran a alors le trajet sous la main.
   */
  fun takeReopenItineraryId(): String?
}
