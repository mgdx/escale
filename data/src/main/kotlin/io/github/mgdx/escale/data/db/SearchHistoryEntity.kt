package io.github.mgdx.escale.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une recherche passée (SPEC.md § 5.5), horodatée et effaçable une par une ou en bloc.
 *
 * **L'heure demandée est enregistrée avec la recherche** : une puce de dernière recherche la
 * relance (SPEC.md § 5.1), et « Bastille → Gare de Lyon » rejoué sans son « arriver avant 9 h 00 »
 * n'est pas la même recherche. Elle tient en deux colonnes plutôt qu'en une : [timeMode] dit
 * laquelle des trois formes de `TimeChoice` a été choisie, [timeMillis] porte l'instant des deux
 * qui en ont un. Un seul entier signé n'aurait pas su distinguer « partir à » de « arriver avant ».
 *
 * **Un couple départ / arrivée n'occupe qu'une ligne**, la plus récente, avec son horodatage et son
 * heure demandée. Chercher deux fois le même trajet est le cas d'usage le plus banal qui soit :
 * SPEC.md § 5.1 promet « les dernières recherches », et en montrer deux fois la même ne sert
 * personne ; SPEC.md § 5.5 plafonne à 50 entrées, et les gaspiller en répétitions réduit d'autant
 * la mémoire utile. L'heure demandée ne fait **pas** partie de la clé : c'est la dernière recherche
 * qui fait foi, avec l'heure qu'elle portait.
 *
 * L'index reprend le nom et les coordonnées des deux points, jamais leur `stopId` : celui-ci est
 * nul pour une adresse, et deux `NULL` ne se comparent jamais égaux en SQL — un index qui
 * l'inclurait laisserait passer tous les doublons d'adresses.
 *
 * La table est plafonnée à 50 lignes **par le stockage lui-même** : la purge a lieu à l'insertion,
 * dans la même transaction (voir `HistoryDao.record`). Laisser le plafond à l'écran qui affiche
 * reviendrait à conserver indéfiniment des lieux que l'usager croit oubliés — ce que SPEC.md § 11
 * interdit de faire de sa donnée la plus sensible.
 */
@Entity(
  tableName = "search_history",
  indices = [
    Index(
      value = ["from_name", "from_lat", "from_lon", "to_name", "to_lat", "to_lon"],
      unique = true,
    ),
  ],
)
internal data class SearchHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @Embedded(prefix = "from_") val from: LocationColumns,
  @Embedded(prefix = "to_") val to: LocationColumns,
  /**
   * Nom de la forme de `TimeChoice` : `NOW`, `DEPART_AT` ou `ARRIVE_BY`.
   *
   * La valeur par défaut est déclarée ici et pas seulement dans la migration 1 → 2 : sans elle, le
   * schéma exporté et la table migrée divergeraient, et Room refuserait d'ouvrir la base migrée.
   */
  @ColumnInfo(defaultValue = TIME_MODE_NOW) val timeMode: String = TIME_MODE_NOW,
  /** L'instant demandé, en millisecondes depuis l'époque Unix. Nul pour « maintenant ». */
  val timeMillis: Long? = null,
  /** Millisecondes depuis l'époque Unix. */
  val searchedAt: Long,
)

/** « Partir maintenant », la seule forme sans instant — donc le défaut d'une ligne d'avant la v2. */
internal const val TIME_MODE_NOW = "NOW"

internal const val TIME_MODE_DEPART_AT = "DEPART_AT"

internal const val TIME_MODE_ARRIVE_BY = "ARRIVE_BY"
