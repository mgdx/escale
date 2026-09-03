package io.github.mgdx.escale.ui.departures

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import java.time.Instant

/** Une requête de départs, telle que le `ViewModel` l'a émise. */
internal data class DeparturesCall(
  val stopId: String,
  val time: Instant,
  val modes: Set<TransitMode>,
  val cursor: String?,
)

/**
 * Dépôt de course qui note ce qu'on lui demande, pour éprouver les deux `ViewModel` sans réseau.
 *
 * Il note les **modes envoyés** : c'est là que se vérifie qu'un filtre « train » part en feuilles
 * et jamais en parapluie (docs/architecture.md § 11.5).
 */
internal class RecordingTripRepository : TripRepository {

  val departureCalls = mutableListOf<DeparturesCall>()
  val tripCalls = mutableListOf<Pair<String, Boolean>>()

  var departuresAnswer: Outcome<StopTimePage> = Outcome.Failure(EscaleError.NoNetwork)
  var tripAnswer: Outcome<Journey> = Outcome.Failure(EscaleError.NoNetwork)

  override suspend fun trip(tripId: String, detailedLegs: Boolean): Outcome<Journey> {
    tripCalls += tripId to detailedLegs
    return tripAnswer
  }

  override suspend fun departures(
    stopId: String,
    time: Instant,
    count: Int,
    modes: Set<TransitMode>,
    arriveBy: Boolean,
    cursor: String?,
  ): Outcome<StopTimePage> {
    departureCalls += DeparturesCall(stopId, time, modes, cursor)
    return departuresAnswer
  }
}

/** Dépôt d'arrêts qui rend une desserte figée, ou rien du tout. */
internal class FakeStopsRepository(private val answer: Outcome<Stop>) : StopsRepository {

  var calls = 0
    private set

  override suspend fun stopsIn(
    area: io.github.mgdx.escale.core.model.BoundingBox,
    modes: Set<TransitMode>,
    grouped: Boolean,
  ): Outcome<List<Stop>> = Outcome.Success(emptyList())

  override suspend fun stop(stopId: String): Outcome<Stop> {
    calls += 1
    return answer
  }
}

internal fun stopOf(name: String, modes: List<TransitMode>, lines: List<io.github.mgdx.escale.core.model.StopLine>) =
  Stop(id = "arret", name = name, coordinates = LatLon(53.55, 10.0), modes = modes, lines = lines)
