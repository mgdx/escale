package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

  /** Combien de fois la position a été consultée : c'est lui qui prouve qu'elle ne l'a pas été. */
  var lastKnownCalls = 0
    private set

  override fun hasCoarsePermission(): Boolean = coarseGranted

  override fun hasFinePermission(): Boolean = fineGranted

  override fun lastKnownLocation(): LatLon? {
    lastKnownCalls += 1
    return lastKnown.takeIf { coarseGranted }
  }

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

/**
 * Géocodage inverse, dans ses deux formes.
 *
 * L'écran de carte les emploie toutes les deux, et pour des raisons opposées : l'appui long veut le
 * **lieu** le plus proche, la fiche veut l'**adresse**. Le faux dépôt les distingue donc lui aussi,
 * sans quoi un cas d'essai ne prouverait rien de ce partage.
 */
class FakeGeocodeRepository(var label: Location? = null, var addressLabel: Location? = null) : GeocodeRepository {
  val reversed: MutableList<LatLon> = mutableListOf()

  /** Les positions dont l'**adresse** a été demandée, dans l'ordre. */
  val addressed: MutableList<LatLon> = mutableListOf()

  /**
   * Combien de temps la réponse se fait attendre.
   *
   * C'est ce délai qui permet d'éprouver l'annulation de SPEC.md § 7, règle 11 : une fiche refermée
   * avant la réponse ne doit rien écrire après coup.
   */
  var delayMillis: Long = 0

  /** Combien de fois la réponse est réellement parvenue à son appelant. */
  var completed = 0
    private set

  override suspend fun autocomplete(
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> = Outcome.Success(emptyList())

  override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> {
    reversed += point
    if (delayMillis > 0) delay(delayMillis)
    completed += 1
    return Outcome.Success(label)
  }

  override suspend fun reverseGeocodeAddress(point: LatLon, language: String?): Outcome<Location?> {
    addressed += point
    if (delayMillis > 0) delay(delayMillis)
    completed += 1
    return Outcome.Success(addressLabel)
  }

  override suspend fun clearGeocodeCache(): Outcome<Unit> = Outcome.Success(Unit)
}

/**
 * Arrêts de carte, entièrement pilotés par le cas d'essai.
 *
 * Le compteur d'appels est ce qui prouve les règles de sobriété de SPEC.md § 5.7 : c'est lui, et
 * non l'état affiché, qui dit si une requête est partie.
 */
class FakeStopsRepository(
  var stops: List<Stop> = emptyList(),
  var detail: Outcome<Stop> = Outcome.Failure(EscaleError.NoNetwork),
) : StopsRepository {

  /** Chaque emprise et chaque jeu de modes demandés, dans l'ordre. */
  val requests: MutableList<Pair<BoundingBox, Set<TransitMode>>> = mutableListOf()
  val detailRequests: MutableList<String> = mutableListOf()

  /** Combien de temps la réponse se fait attendre : de quoi éprouver l'annulation. */
  var delayMillis: Long = 0

  /** Non nulle : la prochaine emprise échoue, comme le ferait un réseau coupé. */
  var failure: EscaleError? = null

  override suspend fun stopsIn(area: BoundingBox, modes: Set<TransitMode>, grouped: Boolean): Outcome<List<Stop>> {
    requests += area to modes
    if (delayMillis > 0) delay(delayMillis)
    return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(stops)
  }

  override suspend fun stop(stopId: String): Outcome<Stop> {
    detailRequests += stopId
    return detail
  }
}

/**
 * Stations et véhicules en libre-service, entièrement pilotés par le cas d'essai.
 *
 * Le compteur d'appels est ce qui prouve les règles de sobriété de SPEC.md § 5.7 : c'est lui, et
 * non l'état affiché, qui dit si une requête est partie.
 */
class FakeRentalsRepository(var availabilities: List<RentalAvailability> = emptyList()) : RentalsRepository {

  /** Chaque emprise demandée à `/api/v1/rentals`, dans l'ordre. */
  val requests: MutableList<BoundingBox> = mutableListOf()

  /** Combien de temps la réponse se fait attendre : de quoi éprouver l'annulation. */
  var delayMillis: Long = 0

  /** Non nulle : la prochaine emprise échoue, comme le ferait un réseau coupé. */
  var failure: EscaleError? = null

  override suspend fun stationsIn(area: BoundingBox): Outcome<List<RentalAvailability>> {
    requests += area
    if (delayMillis > 0) delay(delayMillis)
    return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(availabilities)
  }

  /** La carte n'appelle jamais ce chemin : il sert la portion de trajet de SPEC.md § 5.3. */
  override suspend fun availabilityNear(
    point: LatLon,
    radiusMeters: Int,
    fresh: Boolean,
  ): Outcome<List<RentalAvailability>> = Outcome.Success(emptyList())
}

/** Réglages d'affichage en mémoire : les trois bascules de couches de SPEC.md § 5.6. */
class FakePreferencesRepository(display: DisplayPreferences = DisplayPreferences()) : PreferencesRepository {

  private val displayState = MutableStateFlow(display)

  override val searchPreferences: Flow<SearchPreferences> = MutableStateFlow(SearchPreferences())

  override val displayPreferences: Flow<DisplayPreferences> = displayState

  override suspend fun updateSearchPreferences(preferences: SearchPreferences): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun updateDisplayPreferences(preferences: DisplayPreferences): Outcome<Unit> {
    displayState.value = preferences
    return Outcome.Success(Unit)
  }

  override suspend fun resetToDefaults(): Outcome<Unit> = Outcome.Success(Unit)
}
