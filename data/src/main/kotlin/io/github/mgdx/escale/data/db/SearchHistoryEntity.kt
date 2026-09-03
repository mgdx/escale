package io.github.mgdx.escale.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une recherche passée (SPEC.md § 5.5), horodatée et effaçable une par une ou en bloc.
 *
 * La table est plafonnée à 50 lignes **par le stockage lui-même** : la purge a lieu à l'insertion,
 * dans la même transaction (voir `HistoryDao.record`). Laisser le plafond à l'écran qui affiche
 * reviendrait à conserver indéfiniment des lieux que l'usager croit oubliés — ce que SPEC.md § 11
 * interdit de faire de sa donnée la plus sensible.
 */
@Entity(tableName = "search_history")
internal data class SearchHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @Embedded(prefix = "from_") val from: LocationColumns,
  @Embedded(prefix = "to_") val to: LocationColumns,
  /** Millisecondes depuis l'époque Unix. */
  val searchedAt: Long,
)
