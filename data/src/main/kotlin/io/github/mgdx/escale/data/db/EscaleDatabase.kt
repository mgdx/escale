package io.github.mgdx.escale.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
 * **`version = 1`, et aucun `fallbackToDestructiveMigration`.** Une base de favoris qui s'efface
 * toute seule à la mise à jour est un défaut, pas une simplification : la version 2 devra fournir
 * une `Migration`, et le schéma exporté dans `data/schemas` est ce qui permettra de la vérifier.
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
  version = 1,
  exportSchema = true,
)
abstract class EscaleDatabase : RoomDatabase() {

  internal abstract fun favoritesDao(): FavoritesDao

  internal abstract fun historyDao(): HistoryDao

  internal abstract fun watchedJourneysDao(): WatchedJourneysDao

  companion object {
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
        .build()
  }
}
