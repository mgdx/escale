package io.github.mgdx.escale.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un trajet mis en favori : un couple départ / arrivée et l'onglet consulté (SPEC.md § 5.5).
 *
 * La catégorie est la trace des « préférences de modes » que la spec autorise à joindre au favori.
 * Les autres réglages de recherche ne sont pas recopiés ici : ce sont des préférences globales de
 * SPEC.md § 5.6, et les figer par trajet ferait diverger un favori des réglages de l'usager sans
 * que rien ne l'annonce.
 */
@Entity(tableName = "favorite_journeys")
internal data class FavoriteJourneyEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** Nom donné par l'usager, nul pour laisser l'interface composer « Départ → Arrivée ». */
  val label: String?,
  @Embedded(prefix = "from_") val from: LocationColumns,
  @Embedded(prefix = "to_") val to: LocationColumns,
  /** Nom de la valeur de `JourneyCategory`. */
  val category: String,
  val createdAt: Long,
)
