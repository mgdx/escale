package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Feuilles de style factices : ce qui compte est laquelle des deux est demandée. */
class FakeStyleSource : MapStyleSource {
  val tiledRequests: MutableList<Pair<String, Boolean>> = mutableListOf()
  var blankRequested = false

  override suspend fun tiledStyle(baseUrl: String, dark: Boolean): String {
    tiledRequests += baseUrl to dark
    return "style-tuiles:$baseUrl:${if (dark) "sombre" else "clair"}"
  }

  override suspend fun blankStyle(dark: Boolean): String {
    blankRequested = true
    return "style-neutre:${if (dark) "sombre" else "clair"}"
  }
}

/** Caméra mémorisée, en mémoire. */
class FakeCameraMemory(private var stored: MapCamera? = null) : MapCameraMemory {
  val saved: MutableList<MapCamera> = mutableListOf()

  override suspend fun lastCamera(): MapCamera? = stored

  override suspend fun save(camera: MapCamera) {
    saved += camera
    stored = camera
  }
}

/**
 * Position de l'appareil, entièrement pilotée par le cas d'essai : ni `LocationManager`, ni
 * permission réelle, ni la moindre coordonnée journalisée.
 */
class FakeLocationSource(
  var coarseGranted: Boolean = false,
  var fineGranted: Boolean = false,
  var lastKnown: LatLon? = null,
) : LocationSource {

  val emitted = MutableSharedFlow<LatLon>(extraBufferCapacity = 8)
  var collected = 0
    private set

  override fun hasCoarsePermission(): Boolean = coarseGranted

  override fun hasFinePermission(): Boolean = fineGranted

  override fun lastKnownLocation(): LatLon? = lastKnown.takeIf { coarseGranted }

  override fun locations(): Flow<LatLon> {
    collected += 1
    return emitted
  }
}

/** Cadrage initial du serveur. */
class FakeMapRepository(var outcome: Outcome<MapCamera> = Outcome.Failure(EscaleError.NoNetwork)) : MapRepository {
  var calls = 0
    private set

  override suspend fun initialCamera(): Outcome<MapCamera> {
    calls += 1
    return outcome
  }
}

/** Géocodage inverse : seul `reverseGeocode` est exercé par l'écran de carte. */
class FakeGeocodeRepository(private var label: Location? = null) : GeocodeRepository {
  val reversed: MutableList<LatLon> = mutableListOf()

  override suspend fun autocomplete(
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> = Outcome.Success(emptyList())

  override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> {
    reversed += point
    return Outcome.Success(label)
  }

  override suspend fun clearGeocodeCache(): Outcome<Unit> = Outcome.Success(Unit)
}
