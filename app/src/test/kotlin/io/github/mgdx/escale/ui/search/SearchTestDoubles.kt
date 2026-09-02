package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
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

  val reversed: MutableList<LatLon> = mutableListOf()

  override suspend fun autocomplete(
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> {
    requests += text
    return suggestions
  }

  override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> {
    reversed += point
    return reverse
  }

  override suspend fun clearGeocodeCache(): Outcome<Unit> = Outcome.Success(Unit)
}

/** Domicile et travail, pilotables — ce que le jalon 10 branchera sur `FavoritesRepository`. */
class FakeSavedPlacesSource(home: Location? = null, work: Location? = null) : SavedPlacesSource {
  private val homeState = MutableStateFlow(home)
  private val workState = MutableStateFlow(work)

  override val home: Flow<Location?> = homeState
  override val work: Flow<Location?> = workState
}

/** L'historique, pilotable — ce que le jalon 10 branchera sur `HistoryRepository`. */
class FakeRecentSearchesSource(searches: List<RecentSearch> = emptyList()) : RecentSearchesSource {
  override val recentSearches: Flow<List<RecentSearch>> = MutableStateFlow(searches)
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
