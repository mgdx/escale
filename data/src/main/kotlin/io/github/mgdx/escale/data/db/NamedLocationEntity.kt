package io.github.mgdx.escale.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Domicile et travail (SPEC.md § 5.5), deux emplacements nommés distincts des autres favoris.
 *
 * **« Non renseigné » se lit à l'absence de ligne**, jamais à une valeur convenue. C'est ce qui
 * distingue sans ambiguïté un domicile jamais saisi d'un domicile enregistré dont le libellé serait
 * vide : le premier n'a pas de ligne, le second en a une. La spec en dépend directement — « tant
 * que Domicile ou Travail n'est pas renseigné, aucune puce ne lui correspond » — et une sentinelle
 * du genre `name = ""` obligerait chaque lecteur à connaître la convention pour ne pas afficher
 * une puce vide.
 *
 * Il y a au plus deux lignes dans cette table, [NamedLocationSlot.HOME] et [NamedLocationSlot.WORK],
 * garanties par la clé primaire.
 */
@Entity(tableName = "named_locations")
internal data class NamedLocationEntity(
  @PrimaryKey val slot: String,
  @Embedded val location: LocationColumns,
  /** Date d'enregistrement, en millisecondes depuis l'époque Unix. */
  val savedAt: Long,
)

/** Les deux emplacements nommés que SPEC.md § 5.5 distingue des autres favoris. */
internal enum class NamedLocationSlot {
  HOME,
  WORK,
}
