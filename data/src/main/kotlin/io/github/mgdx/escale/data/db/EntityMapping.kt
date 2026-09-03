package io.github.mgdx.escale.data.db

import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import java.time.Instant

/**
 * Traduction entre les entités de la base et les types de domaine.
 *
 * Le passage est explicite, comme pour les DTO du réseau : les entités sont des types de `:data` et
 * ne remontent jamais dans l'interface (docs/architecture.md § 1). Ce fichier vit dans `data.db`
 * plutôt que dans `data.mapper`, réservé au mapping des DTO de l'API.
 *
 * **Aucune valeur illisible ne fait échouer une lecture.** Une base écrite par une version future,
 * ou par un serveur dont les modes ont changé de nom, doit rendre un favori utilisable plutôt
 * qu'une exception : perdre l'accès à ses favoris parce qu'un nom d'énumération a bougé serait
 * exactement la perte de données que le schéma exporté sert à éviter.
 */

private const val VALUE_SEPARATOR = ","

internal fun LocationColumns.toLocation(): Location = Location(
  id = stopId,
  name = name,
  description = description,
  coordinates = LatLon(lat, lon),
  kind = decodeKind(kind, stopId),
  servedModes = decodeModes(servedModes),
)

internal fun Location.toColumns(): LocationColumns = LocationColumns(
  stopId = id,
  name = name,
  description = description,
  lat = coordinates.lat,
  lon = coordinates.lon,
  kind = kind.name,
  servedModes = encodeModes(servedModes),
)

internal fun FavoritePlaceEntity.toFavoritePlace(): FavoritePlace = FavoritePlace(
  id = id,
  label = label,
  location = location.toLocation(),
  createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun FavoriteStopEntity.toStop(): Stop = Stop(
  id = stopId,
  name = name,
  coordinates = LatLon(lat, lon),
  modes = decodeModes(modes),
  // Les lignes desservies ne sont pas persistées : elles demandent une requête par arrêt et
  // dépendent de l'horaire du serveur (SPEC.md § 5.7).
  lines = emptyList(),
)

internal fun Stop.toEntity(createdAt: Instant): FavoriteStopEntity = FavoriteStopEntity(
  stopId = id,
  name = name,
  lat = coordinates.lat,
  lon = coordinates.lon,
  modes = encodeModes(modes),
  createdAt = createdAt.toEpochMilli(),
)

internal fun FavoriteJourneyEntity.toFavoriteJourney(): FavoriteJourney = FavoriteJourney(
  id = id,
  label = label,
  from = from.toLocation(),
  to = to.toLocation(),
  category = JourneyCategory.entries.firstOrNull { it.name == category } ?: JourneyCategory.TRANSIT,
  createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun SearchHistoryEntity.toHistoryEntry(): SearchHistoryEntry = SearchHistoryEntry(
  id = id,
  from = from.toLocation(),
  to = to.toLocation(),
  time = decodeTime(timeMode, timeMillis),
  searchedAt = Instant.ofEpochMilli(searchedAt),
)

/**
 * L'heure demandée, en deux colonnes.
 *
 * Une forme datée dont l'instant manque — ligne écrite par une version future, ou colonne effacée à
 * la main — retombe sur « maintenant » : rejouer la recherche à l'heure qu'il est vaut mieux que ne
 * pas pouvoir la rejouer du tout.
 */
internal fun decodeTime(mode: String, millis: Long?): TimeChoice {
  val instant = millis?.let(Instant::ofEpochMilli) ?: return TimeChoice.Now
  return when (mode) {
    TIME_MODE_DEPART_AT -> TimeChoice.DepartAt(instant)
    TIME_MODE_ARRIVE_BY -> TimeChoice.ArriveBy(instant)
    else -> TimeChoice.Now
  }
}

internal fun TimeChoice.toModeColumn(): String = when (this) {
  TimeChoice.Now -> TIME_MODE_NOW
  is TimeChoice.DepartAt -> TIME_MODE_DEPART_AT
  is TimeChoice.ArriveBy -> TIME_MODE_ARRIVE_BY
}

internal fun TimeChoice.toMillisColumn(): Long? = when (this) {
  TimeChoice.Now -> null
  is TimeChoice.DepartAt -> instant.toEpochMilli()
  is TimeChoice.ArriveBy -> instant.toEpochMilli()
}

/**
 * Un genre de lieu illisible se relit d'après l'identifiant plutôt que par une valeur arbitraire.
 *
 * L'écart compte : docs/architecture.md § 11.3 impose qu'un lieu de genre `STOP` parte en `stopId`
 * et non en coordonnées, faute de quoi la recherche ne rend aucun résultat autour d'une gare. Un
 * arrêt relu comme une adresse serait donc silencieusement dégradé.
 */
private fun decodeKind(kind: String, stopId: String?): PlaceKind = PlaceKind.entries.firstOrNull { it.name == kind }
  ?: if (stopId != null) PlaceKind.STOP else PlaceKind.ADDRESS

private fun encodeModes(modes: List<TransitMode>): String = modes.joinToString(VALUE_SEPARATOR) { it.name }

private fun decodeModes(stored: String): List<TransitMode> = stored
  .split(VALUE_SEPARATOR)
  .mapNotNull { name -> TransitMode.entries.firstOrNull { it.name == name } }
