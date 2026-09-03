package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.TripApi
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Les prochains départs et la desserte d'une course (SPEC.md § 5.4 et § 5.3).
 *
 * **Aucun cache**, contrairement au dépôt des arrêts de la carte : un horaire temps réel se périme
 * en secondes, et le servir depuis une mémoire ferait exactement ce que SPEC.md § 5.2 interdit —
 * annoncer un retard qui n'est plus vrai. La fraîcheur est réglée en amont, par
 * `RealtimeRefreshPolicy` et par le geste de l'usager (§ 7.4), et jamais par du polling.
 *
 * Le serveur n'est pas retenu à la construction mais relu à chaque appel : l'usager peut en changer
 * pendant que l'écran est ouvert (SPEC.md § 5.6.1).
 *
 * Rien n'est journalisé : un identifiant d'arrêt est une donnée de localisation (SPEC.md § 8, § 11).
 */
class TripRepositoryImpl(private val api: TripApi, private val servers: ServerRepository) : TripRepository {

  override suspend fun trip(tripId: String, detailedLegs: Boolean): Outcome<Journey> =
    api.trip(baseUrl(), tripId, detailedLegs)

  override suspend fun departures(
    stopId: String,
    time: Instant,
    count: Int,
    modes: Set<TransitMode>,
    arriveBy: Boolean,
    cursor: String?,
  ): Outcome<StopTimePage> = api.stopTimes(
    baseUrl = baseUrl(),
    stopId = stopId,
    // L'heure est ignorée par le serveur quand un curseur est fourni ; ne pas l'envoyer du tout
    // évite qu'une page suivante dépende de deux ancrages contradictoires.
    time = time.takeIf { cursor == null },
    count = count,
    modes = modes,
    arriveBy = arriveBy,
    cursor = cursor,
  )

  private suspend fun baseUrl(): String = servers.current.first().baseUrl
}
