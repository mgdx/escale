package io.github.mgdx.escale.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un lieu mis en favori, autre que le domicile et le travail (SPEC.md § 5.5). */
@Entity(tableName = "favorite_places")
internal data class FavoritePlaceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /**
   * Nom donné par l'usager, distinct du nom du lieu rendu par le serveur.
   *
   * Il est conservé tel quel et n'écrase pas [LocationColumns.name] : renommer « Gare de Lyon » en
   * « Chez Maman » ne doit pas faire perdre le nom réel de l'arrêt, dont dépend le repli de
   * SPEC.md § 5.6.1.
   */
  val label: String?,
  @Embedded val location: LocationColumns,
  val createdAt: Long,
)
