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
   * Efface la recherche déjà enregistrée pour ce couple départ / arrivée, s'il y en a une.
   *
   * Les colonnes comparées sont **exactement celles de l'index unique**. Effacer puis réinsérer,
   * plutôt que mettre à jour, donne à la recherche rejouée une ligne neuve : son horodatage et son
   * heure demandée sont ceux de la dernière fois, ce que SPEC.md § 5.1 promet en parlant des
   * « dernières recherches ».
   */
  @Query(
    """
    DELETE FROM search_history
    WHERE from_name = :fromName AND from_lat = :fromLat AND from_lon = :fromLon
      AND to_name = :toName AND to_lat = :toLat AND to_lon = :toLon
    """,
  )
  abstract suspend fun deleteSameRoute(
    fromName: String,
    fromLat: Double,
    fromLon: Double,
    toName: String,
    toLat: Double,
    toLon: Double,
  )

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
   * Enregistre une recherche, **déduplique** et applique le plafond, le tout dans une seule
   * transaction.
   *
   * Deux garanties y sont tenues, et nulle part ailleurs :
   *
   * - le couple départ / arrivée déjà enregistré cède la place au nouveau, si bien que les
   *   cinquante entrées sont cinquante **trajets** distincts et non cinquante lignes (SPEC.md
   *   § 5.1 et § 5.5). L'écran des puces n'a donc rien à filtrer, et ne peut pas oublier de le
   *   faire ;
   * - le plafond de 50 est appliqué à l'insertion : un écran qui se contenterait d'afficher les
   *   cinquante premières laisserait grossir indéfiniment un fichier plein de lieux que l'usager
   *   croit oubliés (SPEC.md § 11).
   */
  @Transaction
  open suspend fun record(entity: SearchHistoryEntity, limit: Int) {
    deleteSameRoute(
      fromName = entity.from.name,
      fromLat = entity.from.lat,
      fromLon = entity.from.lon,
      toName = entity.to.name,
      toLat = entity.to.lat,
      toLon = entity.to.lon,
    )
    insert(entity)
    trimTo(limit)
  }

  @Query("DELETE FROM search_history WHERE id = :id")
  abstract suspend fun delete(id: Long)

  /** « Tout effacer » : la table est vidée, sans exception ni conservation d'un reliquat. */
  @Query("DELETE FROM search_history")
  abstract suspend fun clear()
}
