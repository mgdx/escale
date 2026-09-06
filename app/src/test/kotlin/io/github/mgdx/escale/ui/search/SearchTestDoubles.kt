package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Le géocodage, entièrement piloté par le cas d'essai : aucune requête réelle, aucune trace. */
class FakeSearchGeocodeRepository(
  var suggestions: Outcome<List<Location>> = Outcome.Success(emptyList()),
  var reverse: Outcome<Location?> = Outcome.Success(null),
) : GeocodeRepository {

  /** Les textes réellement envoyés au serveur : c'est le compte qui prouve la sobriété. */
  val requests: MutableList<String> = mutableListOf()

  /** Le biais géographique reçu à chaque requête, dans le même ordre que [requests]. */
  val biases: MutableList<LatLon?> = mutableListOf()

  val reversed: MutableList<LatLon> = mutableListOf()

  override suspend fun autocomplete(
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> {
    requests += text
    biases += bias
    return suggestions
  }

  override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> {
    reversed += point
    return reverse
  }

  override suspend fun clearGeocodeCache(): Outcome<Unit> = Outcome.Success(Unit)
}

/** Domicile et travail, pilotables sans base de données. */
class FakeSavedPlacesSource(home: Location? = null, work: Location? = null) : SavedPlacesSource {
  private val homeState = MutableStateFlow(home)
  private val workState = MutableStateFlow(work)

  override val home: Flow<Location?> = homeState
  override val work: Flow<Location?> = workState

  override suspend fun clear(kind: SavedPlaceKind): Outcome<Unit> {
    when (kind) {
      SavedPlaceKind.HOME -> homeState.value = null
      SavedPlaceKind.WORK -> workState.value = null
    }
    return Outcome.Success(Unit)
  }
}

/**
 * L'historique, pilotable sans base de données.
 *
 * [recorded] est ce qui prouve qu'une recherche a été enregistrée **une fois**, et [enabled] joue
 * le rôle de la bascule des réglages, que le dépôt réel applique de son côté (SPEC.md § 5.6). La
 * déduplication sur le couple départ / arrivée, elle aussi, appartient au dépôt : cet écran ne la
 * connaît pas et n'a pas à la simuler.
 */
class FakeRecentSearchesSource(searches: List<RecentSearch> = emptyList()) : RecentSearchesSource {
  override val recentSearches: Flow<List<RecentSearch>> = MutableStateFlow(searches)

  var enabled: Boolean = true

  val recorded: MutableList<RecentSearch> = mutableListOf()

  override suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit> {
    if (enabled) recorded += RecentSearch(id = recorded.size + 1L, from = from, to = to, time = time)
    return Outcome.Success(Unit)
  }
}

/** Un arrêt tel que l'autocomplétion le rend : avec son `stopId`, son type et ses modes. */
fun stop(id: String, name: String, lat: Double = 48.84, lon: Double = 2.37) = Location(
  id = id,
  name = name,
  description = "Paris",
  coordinates = LatLon(lat, lon),
  kind = PlaceKind.STOP,
  servedModes = listOf(TransitMode.RAIL, TransitMode.SUBWAY),
)

/** Une adresse : pas d'identifiant, la requête partira en coordonnées. */
fun address(name: String, lat: Double = 48.85, lon: Double = 2.35) = Location(
  id = null,
  name = name,
  description = null,
  coordinates = LatLon(lat, lon),
  kind = PlaceKind.ADDRESS,
)
