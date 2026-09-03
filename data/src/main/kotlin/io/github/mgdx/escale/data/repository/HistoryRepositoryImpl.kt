package io.github.mgdx.escale.data.repository

import android.database.SQLException
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.SearchHistoryEntity
import io.github.mgdx.escale.data.db.toColumns
import io.github.mgdx.escale.data.db.toHistoryEntry
import io.github.mgdx.escale.data.db.toMillisColumn
import io.github.mgdx.escale.data.db.toModeColumn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * L'historique des recherches, persisté en Room (SPEC.md § 5.5).
 *
 * Deux garanties, toutes deux tenues ici et non par l'écran qui affichera :
 *
 * - **Le plafond de 50** est appliqué à l'insertion, dans la transaction de `HistoryDao.record`.
 * - **La désactivation de l'historique** est lue avant chaque écriture : réglage éteint, `record`
 *   n'écrit rien. Le réglage est celui qui existe déjà, `DisplayPreferences.historyEnabled` ; il
 *   n'y en a pas un second.
 *
 * Une écriture ignorée n'est pas un échec : elle rend `Outcome.Success`. Faire remonter une erreur
 * obligerait chaque appelant à distinguer un cas parfaitement normal, et finirait par afficher un
 * message à quelqu'un qui a justement demandé qu'on ne garde rien.
 */
class HistoryRepositoryImpl(
  database: EscaleDatabase,
  private val preferencesRepository: PreferencesRepository,
  private val clock: () -> Instant = Instant::now,
) : HistoryRepository {

  private val dao = database.historyDao()

  override val recentSearches: Flow<List<SearchHistoryEntry>> =
    dao.observeRecent(HistoryRepository.MAX_ENTRIES).map { rows -> rows.map { it.toHistoryEntry() } }

  override suspend fun record(from: Location, to: Location, time: TimeChoice): Outcome<Unit> {
    if (!preferencesRepository.displayPreferences.first().historyEnabled) return Outcome.Success(Unit)
    val entry = SearchHistoryEntity(
      from = from.toColumns(),
      to = to.toColumns(),
      timeMode = time.toModeColumn(),
      timeMillis = time.toMillisColumn(),
      searchedAt = clock().toEpochMilli(),
    )
    return write { dao.record(entry, HistoryRepository.MAX_ENTRIES) }
  }

  override suspend fun delete(id: Long): Outcome<Unit> = write { dao.delete(id) }

  override suspend fun clear(): Outcome<Unit> = write { dao.clear() }

  /** Voir `FavoritesRepositoryImpl` : le nom de l'exception, et rien de son contexte. */
  private inline fun write(block: () -> Unit): Outcome<Unit> = try {
    Outcome.Success(block())
  } catch (failure: SQLException) {
    Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
  }
}
