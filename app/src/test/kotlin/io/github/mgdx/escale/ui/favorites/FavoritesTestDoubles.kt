package io.github.mgdx.escale.ui.favorites

import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.matching
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * Les favoris, entièrement en mémoire et pilotés par le cas d'essai.
 *
 * Il tient les mêmes promesses que l'implémentation Room, celles dont les écrans dépendent :
 * l'identifiant est attribué à l'insertion, un lieu se supprime par cet identifiant, « non
 * renseigné » se lit à l'absence de valeur, et **un trajet déjà en favori n'est pas enregistré une
 * seconde fois** — un double qui passerait ici ne prouverait rien de la vraie base, qui le refuse.
 */
class FakeFavoritesRepository : FavoritesRepository {
  private val homeState = MutableStateFlow<Location?>(null)
  private val workState = MutableStateFlow<Location?>(null)
  private val placesState = MutableStateFlow<List<FavoritePlace>>(emptyList())
  private val stopsState = MutableStateFlow<List<Stop>>(emptyList())
  private val journeysState = MutableStateFlow<List<FavoriteJourney>>(emptyList())

  /** Vrai pour que toute écriture échoue, comme une base pleine ou verrouillée. */
  var failing: Boolean = false

  private var nextId: Long = 1

  override val home: Flow<Location?> = homeState
  override val work: Flow<Location?> = workState
  override val places: Flow<List<FavoritePlace>> = placesState
  override val stops: Flow<List<Stop>> = stopsState
  override val journeys: Flow<List<FavoriteJourney>> = journeysState

  override suspend fun setHome(location: Location?): Outcome<Unit> = write { homeState.value = location }

  override suspend fun setWork(location: Location?): Outcome<Unit> = write { workState.value = location }

  override suspend fun addPlace(location: Location, label: String?): Outcome<Long> {
    if (failing) return failure()
    val id = nextId++
    placesState.value += FavoritePlace(id = id, label = label, location = location, createdAt = NOW)
    return Outcome.Success(id)
  }

  override suspend fun removePlace(id: Long): Outcome<Unit> = write {
    placesState.value = placesState.value.filterNot { it.id == id }
  }

  override suspend fun addStop(stop: Stop): Outcome<Unit> = write {
    stopsState.value = stopsState.value.filterNot { it.id == stop.id } + stop
  }

  override suspend fun removeStop(stopId: String): Outcome<Unit> = write {
    stopsState.value = stopsState.value.filterNot { it.id == stopId }
  }

  override suspend fun addJourney(
    from: Location,
    to: Location,
    category: JourneyCategory,
    label: String?,
  ): Outcome<Long> {
    if (failing) return failure()
    journeysState.value.matching(from, to, category)?.let { return Outcome.Success(it.id) }
    val id = nextId++
    journeysState.value += FavoriteJourney(
      id = id,
      label = label,
      from = from,
      to = to,
      category = category,
      createdAt = NOW,
    )
    return Outcome.Success(id)
  }

  override suspend fun removeJourney(id: Long): Outcome<Unit> = write {
    journeysState.value = journeysState.value.filterNot { it.id == id }
  }

  private fun write(block: () -> Unit): Outcome<Unit> {
    if (failing) return failure()
    block()
    return Outcome.Success(Unit)
  }

  private fun <T> failure(): Outcome<T> = Outcome.Failure(EscaleError.Unknown(cause = "Fake"))

  private companion object {
    val NOW: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}

/**
 * L'historique, en mémoire. [recorded] est ce qui prouve qu'une recherche a été enregistrée une fois.
 *
 * Il déduplique sur le couple départ / arrivée, comme le dépôt réel : une paire cherchée deux fois
 * n'occupe qu'une ligne, la plus récente. Un doublon qui passerait ici ne prouverait rien.
 */
class FakeHistoryRepository(enabled: Boolean = true) : HistoryRepository {
  private val entriesState = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())

  /** Faux quand l'usager a désactivé l'historique : `record` n'écrit alors rien (SPEC.md § 5.6). */
  var enabled: Boolean = enabled

  val recorded: MutableList<SearchHistoryEntry> = mutableListOf()

  var cleared: Int = 0

  private var nextId: Long = 1

  override val recentSearches: Flow<List<SearchHistoryEntry>> = entriesState

  override suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit> {
    if (!enabled) return Outcome.Success(Unit)
    val entry = SearchHistoryEntry(id = nextId++, from = from, to = to, time = time, searchedAt = NOW)
    recorded += entry
    val others = entriesState.value.filterNot { it.from.name == from.name && it.to.name == to.name }
    entriesState.value = listOf(entry) + others
    return Outcome.Success(Unit)
  }

  override suspend fun delete(id: Long): Outcome<Unit> {
    entriesState.value = entriesState.value.filterNot { it.id == id }
    return Outcome.Success(Unit)
  }

  override suspend fun clear(): Outcome<Unit> {
    cleared++
    entriesState.value = emptyList()
    return Outcome.Success(Unit)
  }

  private companion object {
    val NOW: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}

/**
 * Le dépôt des courses, réduit à ce dont la vérification des arrêts favoris a besoin.
 *
 * [answers] donne la réponse du serveur pour chaque identifiant présenté ; [asked] retient ce qui
 * lui a été demandé, ce qui permet de vérifier qu'un arrêt n'est présenté qu'une fois.
 */
class FakeStopsTripRepository(private val answers: Map<String, Outcome<StopTimePage>>) : TripRepository {
  val asked: MutableList<String> = mutableListOf()

  override suspend fun trip(tripId: String, detailedLegs: Boolean): Outcome<Journey> =
    Outcome.Failure(EscaleError.Unknown(cause = "Fake"))

  override suspend fun departures(
    stopId: String,
    time: Instant,
    count: Int,
    modes: Set<TransitMode>,
    arriveBy: Boolean,
    cursor: String?,
  ): Outcome<StopTimePage> {
    asked += stopId
    return answers[stopId] ?: Outcome.Success(StopTimePage(entries = emptyList(), stop = null))
  }
}

/** Un arrêt tel que les favoris le portent : identifiant **et** coordonnées (SPEC.md § 5.6.1). */
fun favoriteStop(id: String, name: String) = Stop(
  id = id,
  name = name,
  coordinates = LatLon(48.8443, 2.3735),
  modes = listOf(TransitMode.SUBWAY),
)

/** Une adresse, telle que l'autocomplétion la rend. */
fun favoriteAddress(name: String) = Location(
  id = null,
  name = name,
  description = null,
  coordinates = LatLon(48.8566, 2.3522),
  kind = PlaceKind.ADDRESS,
)
