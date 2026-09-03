package io.github.mgdx.escale.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Accès à l'historique des recherches, plafonné par le stockage lui-même (SPEC.md § 5.5). */
@Dao
internal abstract class HistoryDao {

  @Query("SELECT * FROM search_history ORDER BY searchedAt DESC, id DESC LIMIT :limit")
  abstract fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

  @Insert
  abstract suspend fun insert(entity: SearchHistoryEntity): Long

  /**
   * Ne garde que les [limit] entrées les plus récentes.
   *
   * Le tri retenu est celui de la lecture — date décroissante puis identifiant décroissant — pour
   * que la ligne effacée soit toujours celle que l'écran n'affichait plus, y compris quand deux
   * recherches partagent la même milliseconde.
   */
  @Query(
    """
    DELETE FROM search_history
    WHERE id NOT IN (SELECT id FROM search_history ORDER BY searchedAt DESC, id DESC LIMIT :limit)
    """,
  )
  abstract suspend fun trimTo(limit: Int)

  /**
   * Enregistre une recherche et applique le plafond **dans la même transaction**.
   *
   * C'est ici que le plafond de 50 est tenu, et nulle part ailleurs : un écran qui se contenterait
   * d'afficher les cinquante premières laisserait grossir indéfiniment un fichier plein de lieux
   * que l'usager croit oubliés (SPEC.md § 11).
   */
  @Transaction
  open suspend fun record(entity: SearchHistoryEntity, limit: Int) {
    insert(entity)
    trimTo(limit)
  }

  @Query("DELETE FROM search_history WHERE id = :id")
  abstract suspend fun delete(id: Long)

  /** « Tout effacer » : la table est vidée, sans exception ni conservation d'un reliquat. */
  @Query("DELETE FROM search_history")
  abstract suspend fun clear()
}
