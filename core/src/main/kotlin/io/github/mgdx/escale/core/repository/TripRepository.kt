package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.Outcome
import java.time.Instant

/** Détail d'une course et prochains départs à un arrêt (`/api/v6/trip`, `/api/v6/stoptimes`). */
interface TripRepository {
  /**
   * Desserte complète d'une course, rendue par le serveur sous la forme d'un itinéraire à une
   * seule portion.
   */
  suspend fun trip(tripId: String, detailedLegs: Boolean = true): Outcome<Journey>

  /**
   * Prochains départs (ou arrivées) à un arrêt (SPEC.md § 5.4).
   *
   * @param time instant autour duquel chercher. Ignoré si [cursor] est fourni.
   * @param count nombre d'événements demandés (`n`).
   * @param modes filtre par mode. Vide : tous les modes.
   * @param arriveBy `true` pour lister des arrivées plutôt que des départs.
   * @param cursor `previousPageCursor` ou `nextPageCursor` d'une page déjà obtenue.
   */
  suspend fun departures(
    stopId: String,
    time: Instant,
    count: Int = DEFAULT_EVENT_COUNT,
    modes: Set<TransitMode> = emptySet(),
    arriveBy: Boolean = false,
    cursor: String? = null,
  ): Outcome<StopTimePage>

  companion object {
    const val DEFAULT_EVENT_COUNT = 20
  }
}
