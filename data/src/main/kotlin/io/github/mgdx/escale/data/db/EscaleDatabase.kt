package io.github.mgdx.escale.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La base locale d'Escale : favoris, historique et trajets surveillés (SPEC.md § 5.5).
 *
 * **C'est la donnée la plus sensible de l'application** : les lieux où l'usager vit et travaille,
 * et tout ce qu'il a cherché. Trois règles en découlent, et elles ne se négocient pas (SPEC.md § 11) :
 *
 * 1. Le fichier vit dans le stockage privé de l'application, atteint par
 *    [Context.getDatabasePath] via [Room.databaseBuilder]. Aucune exportation, aucune
 *    synchronisation, aucun `ContentProvider`.
 * 2. **Rien n'est journalisé.** Room ne trace aucune requête par défaut, et
 *    `RoomDatabase.Builder.setQueryCallback` n'est appelé nulle part — pas même sous
 *    `BuildConfig.DEBUG`. Un journal de requêtes contiendrait ici des adresses.
 * 3. Aucune donnée ne quitte la base par une autre voie que les dépôts de `data.repository`.
 *
 * **`version = 4`, et aucun `fallbackToDestructiveMigration`.** Une base de favoris qui s'efface
 * toute seule à la mise à jour est un défaut, pas une simplification : chaque version fournit donc
 * sa `Migration`, et les schémas exportés dans `data/schemas` sont ce qui permet de les vérifier.
 */
@Database(
  entities = [
    NamedLocationEntity::class,
    FavoritePlaceEntity::class,
    FavoriteStopEntity::class,
    FavoriteJourneyEntity::class,
    SearchHistoryEntity::class,
    WatchedJourneyEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
abstract class EscaleDatabase : RoomDatabase() {

  internal abstract fun favoritesDao(): FavoritesDao

  internal abstract fun historyDao(): HistoryDao

  internal abstract fun watchedJourneysDao(): WatchedJourneysDao

  companion object {
    /**
     * Version 1 → 2 : l'heure demandée rejoint la recherche enregistrée.
     *
     * `search_history` gagne deux colonnes, et rien d'autre ne bouge. Les lignes déjà là gardent
     * leur départ, leur arrivée et leur horodatage ; leur heure demandée est inconnue, et vaut donc
     * « maintenant » — c'est ce que dit le `DEFAULT`, aligné sur celui que déclare
     * `SearchHistoryEntity`. **Aucune donnée n'est effacée** : une mise à jour qui viderait les
     * favoris ou l'historique serait une perte, pas une migration.
     */
    val MIGRATION_1_2 = object : Migration(startVersion = 1, endVersion = 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE search_history ADD COLUMN timeMode TEXT NOT NULL DEFAULT '$TIME_MODE_NOW'")
        db.execSQL("ALTER TABLE search_history ADD COLUMN timeMillis INTEGER")
      }
    }

    /**
     * Version 2 → 3 : un même trajet ne peut plus être mis en favori deux fois.
     *
     * **Une base déjà polluée est le cas normal, pas le cas limite** : la version 2 laissait
     * l'écran de détail insérer autant de lignes qu'il y avait d'appuis sur l'étoile. Créer l'index
     * unique sur une telle base échouerait — et une migration qui échoue laisse l'application
     * incapable d'ouvrir sa base, donc morte. Les doublons sont donc **fusionnés d'abord**, dans la
     * même transaction que la création de l'index : soit les deux réussissent, soit rien n'a lieu.
     *
     * La ligne conservée par groupe n'est pas prise au hasard : c'est **celle qui porte une
     * surveillance** s'il y en a une (SPEC.md § 5.5.1 — la bascule « me prévenir avant le départ »
     * s'était attachée à l'un des doublons, et la perdre en silence serait une régression visible),
     * et à défaut la plus ancienne, celle que l'usager a créée délibérément. Les autres partent
     * avec leur éventuelle surveillance, par la cascade de la clé étrangère.
     */
    val MIGRATION_2_3 = object : Migration(startVersion = 2, endVersion = 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          DELETE FROM favorite_journeys WHERE id NOT IN (
            SELECT (
              SELECT keep.id FROM favorite_journeys AS keep
              WHERE keep.from_name = f.from_name AND keep.from_lat = f.from_lat
                AND keep.from_lon = f.from_lon AND keep.to_name = f.to_name
                AND keep.to_lat = f.to_lat AND keep.to_lon = f.to_lon
                AND keep.category = f.category
              ORDER BY
                (SELECT COUNT(*) FROM watched_journeys AS w WHERE w.journeyId = keep.id) DESC,
                keep.id ASC
              LIMIT 1
            )
            FROM favorite_journeys AS f
          )
          """.trimIndent(),
        )
        db.execSQL(UNIQUE_JOURNEY_INDEX)
      }
    }

    /**
     * L'index unique de `favorite_journeys`, **recopié tel que Room le décrit** dans
     * `schemas/…/3.json`. Room compare cette définition à celle de la base ouverte, au caractère
     * près : la recopier ici est ce qui garantit qu'une base migrée et une base créée de zéro sont
     * indiscernables.
     */
    private const val UNIQUE_JOURNEY_INDEX =
      "CREATE UNIQUE INDEX IF NOT EXISTS " +
        "`index_favorite_journeys_from_name_from_lat_from_lon_to_name_to_lat_to_lon_category` " +
        "ON `favorite_journeys` (`from_name`, `from_lat`, `from_lon`, `to_name`, `to_lat`, `to_lon`, `category`)"

    /**
     * Version 3 → 4 : une même paire cherchée dix fois n'occupe plus dix lignes.
     *
     * Là encore, **une base déjà pleine de répétitions est le cas normal** : les versions
     * précédentes enregistraient une ligne par recherche. Les doublons sont donc fusionnés avant la
     * création de l'index, dans la même transaction — soit les deux réussissent, soit rien n'a
     * lieu, jamais une base que l'application ne saurait plus ouvrir.
     *
     * La ligne conservée par couple est **la plus récente**, celle que l'usager reconnaîtra, avec
     * son horodatage et son heure demandée. L'identifiant départage les ex æquo à la milliseconde,
     * exactement comme le tri de lecture.
     */
    val MIGRATION_3_4 = object : Migration(startVersion = 3, endVersion = 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          DELETE FROM search_history WHERE id NOT IN (
            SELECT (
              SELECT keep.id FROM search_history AS keep
              WHERE keep.from_name = h.from_name AND keep.from_lat = h.from_lat
                AND keep.from_lon = h.from_lon AND keep.to_name = h.to_name
                AND keep.to_lat = h.to_lat AND keep.to_lon = h.to_lon
              ORDER BY keep.searchedAt DESC, keep.id DESC
              LIMIT 1
            )
            FROM search_history AS h
          )
          """.trimIndent(),
        )
        db.execSQL(UNIQUE_HISTORY_INDEX)
      }
    }

    /** Voir [UNIQUE_JOURNEY_INDEX] : recopié tel que Room le décrit dans `schemas/…/4.json`. */
    private const val UNIQUE_HISTORY_INDEX =
      "CREATE UNIQUE INDEX IF NOT EXISTS " +
        "`index_search_history_from_name_from_lat_from_lon_to_name_to_lat_to_lon` " +
        "ON `search_history` (`from_name`, `from_lat`, `from_lon`, `to_name`, `to_lat`, `to_lon`)"

    /** Nom du fichier dans le répertoire privé `databases/` de l'application. */
    const val FILE_NAME = "escale.db"

    /**
     * Ouvre la base de l'application, dans le répertoire privé `databases/`.
     *
     * Aucun `fallbackToDestructiveMigration`, aucun `setQueryCallback`, aucun
     * `openHelperFactory` : le constructeur reste nu à dessein, chacune de ces options coûtant
     * soit les favoris de l'usager, soit sa confidentialité. Room active de lui-même
     * `PRAGMA foreign_keys`, ce dont dépend la cascade de `watched_journeys`.
     */
    fun create(context: Context): EscaleDatabase =
      Room.databaseBuilder(context.applicationContext, EscaleDatabase::class.java, FILE_NAME)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
  }
}
