package io.github.mgdx.escale.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un arrêt mis en favori (SPEC.md § 5.5).
 *
 * L'identifiant d'arrêt sert de clé primaire — deux fois le même arrêt n'a pas de sens — mais
 * [lat] et [lon] sont enregistrées **en plus** et non à sa place : un identifiant que le nouveau
 * serveur ne reconnaît plus laisse un favori affichable, que rien ne supprime automatiquement
 * (SPEC.md § 5.6.1).
 *
 * Les lignes desservant l'arrêt ne sont pas persistées : elles demandent une requête par arrêt et
 * changent avec l'horaire du serveur. Le domaine les rend vides, l'infobulle de SPEC.md § 5.7 les
 * charge à l'appui.
 */
@Entity(tableName = "favorite_stops")
internal data class FavoriteStopEntity(
  @PrimaryKey val stopId: String,
  val name: String,
  val lat: Double,
  val lon: Double,
  /** Noms des valeurs de `TransitMode`, séparés par des virgules. */
  val modes: String,
  val createdAt: Long,
)
