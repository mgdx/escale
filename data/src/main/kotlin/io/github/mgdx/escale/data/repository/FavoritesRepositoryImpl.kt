package io.github.mgdx.escale.data.repository

import android.database.SQLException
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.FavoriteJourneyEntity
import io.github.mgdx.escale.data.db.FavoritePlaceEntity
import io.github.mgdx.escale.data.db.NamedLocationEntity
import io.github.mgdx.escale.data.db.NamedLocationSlot
import io.github.mgdx.escale.data.db.toColumns
import io.github.mgdx.escale.data.db.toEntity
import io.github.mgdx.escale.data.db.toFavoriteJourney
import io.github.mgdx.escale.data.db.toLocation
import io.github.mgdx.escale.data.db.toStop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Domicile, travail, lieux, arrêts et trajets favoris, persistés en Room (SPEC.md § 5.5).
 *
 * Écrit sur le modèle des dépôts de `data.prefs` : aucune exception ne sort d'ici, tout échec
 * devient un [Outcome.Failure], et **rien n'est journalisé** — ni un nom de lieu, ni une
 * coordonnée, pas même le nom de la table touchée (SPEC.md § 11).
 *
 * Domicile et travail sont facultatifs : leur flux rend `null` tant qu'aucune ligne n'existe, et
 * c'est un état normal. L'application ne les réclame jamais d'elle-même.
 *
 * [clock] est injectable pour que les tests fixent l'horodatage ; en production c'est l'horloge du
 * système.
 */
class FavoritesRepositoryImpl(database: EscaleDatabase, private val clock: () -> Instant = Instant::now) :
  FavoritesRepository {

  private val dao = database.favoritesDao()

  override val home: Flow<Location?> = observeNamedLocation(NamedLocationSlot.HOME)

  override val work: Flow<Location?> = observeNamedLocation(NamedLocationSlot.WORK)

  override val places: Flow<List<Location>> = dao.observePlaces().map { rows -> rows.map { it.toLocation() } }

  override val stops: Flow<List<Stop>> = dao.observeStops().map { rows -> rows.map { it.toStop() } }

  override val journeys: Flow<List<FavoriteJourney>> =
    dao.observeJourneys().map { rows -> rows.map { it.toFavoriteJourney() } }

  override suspend fun setHome(location: Location?): Outcome<Unit> = setNamedLocation(NamedLocationSlot.HOME, location)

  override suspend fun setWork(location: Location?): Outcome<Unit> = setNamedLocation(NamedLocationSlot.WORK, location)

  override suspend fun addPlace(location: Location, label: String?): Outcome<Unit> = write {
    dao.insertPlace(
      FavoritePlaceEntity(label = label, location = location.toColumns(), createdAt = clock().toEpochMilli()),
    )
  }

  override suspend fun removePlace(location: Location): Outcome<Unit> = write {
    dao.deletePlace(
      stopId = location.id,
      name = location.name,
      lat = location.coordinates.lat,
      lon = location.coordinates.lon,
    )
  }

  override suspend fun addStop(stop: Stop): Outcome<Unit> = write { dao.upsertStop(stop.toEntity(clock())) }

  override suspend fun removeStop(stopId: String): Outcome<Unit> = write { dao.deleteStop(stopId) }

  override suspend fun addJourney(
    from: Location,
    to: Location,
    category: JourneyCategory,
    label: String?,
  ): Outcome<Long> = writeValue {
    dao.insertJourney(
      FavoriteJourneyEntity(
        label = label,
        from = from.toColumns(),
        to = to.toColumns(),
        category = category.name,
        createdAt = clock().toEpochMilli(),
      ),
    )
  }

  /** La surveillance éventuelle part avec le trajet, par la cascade de la clé étrangère. */
  override suspend fun removeJourney(id: Long): Outcome<Unit> = write { dao.deleteJourney(id) }

  private fun observeNamedLocation(slot: NamedLocationSlot): Flow<Location?> =
    dao.observeNamedLocation(slot.name).map { row -> row?.location?.toLocation() }

  /** Un [location] nul supprime la ligne : « non renseigné » redevient l'absence de ligne. */
  private suspend fun setNamedLocation(slot: NamedLocationSlot, location: Location?): Outcome<Unit> = write {
    if (location == null) {
      dao.deleteNamedLocation(slot.name)
    } else {
      dao.upsertNamedLocation(
        NamedLocationEntity(
          slot = slot.name,
          location = location.toColumns(),
          savedAt = clock().toEpochMilli(),
        ),
      )
    }
  }

  /**
   * Exécute une écriture sans valeur de retour ; ce que le DAO rend est délibérément ignoré.
   *
   * `FavoritesRepository.addPlace` ne rend pas l'identifiant attribué, là où `addJourney` le rend :
   * l'écart vient du contrat de `:core`, pas d'ici.
   */
  private inline fun write(block: () -> Unit): Outcome<Unit> = writeValue(block)

  /**
   * Exécute une écriture et traduit toute défaillance de SQLite en [EscaleError.Unknown].
   *
   * Seul le nom de la classe d'exception est retenu : un message de SQLite cite volontiers la
   * ligne fautive, donc une adresse (SPEC.md § 11, docs/architecture.md § 6).
   */
  private inline fun <T> writeValue(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
  } catch (failure: SQLException) {
    Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
  }
}
