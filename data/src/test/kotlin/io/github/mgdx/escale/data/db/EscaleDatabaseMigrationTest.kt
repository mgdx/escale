package io.github.mgdx.escale.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.github.mgdx.escale.core.model.TimeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La migration 1 → 2, vérifiée sur une base réellement écrite en version 1.
 *
 * **Elle ne peut pas être vérifiée par `MigrationTestHelper`** : `androidx.room:room-testing`
 * n'est pas dans le catalogue de versions, qui est figé (docs/architecture.md § 3, règle 1). Le
 * dispositif retenu fait le même travail avec ce qui est déjà là : la base de version 1 est
 * construite **à partir du schéma exporté** `schemas/…/1.json` — celui qui est commité, donc celui
 * qui est réellement installé chez l'usager, et non une copie recopiée à la main qui pourrait en
 * diverger —, puis Room ouvre la base migrée. Room valide alors lui-même chaque table, chaque
 * colonne et chaque valeur par défaut contre `2.json` : une migration incomplète ne passe pas.
 */
@RunWith(RobolectricTestRunner::class)
class EscaleDatabaseMigrationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    context.deleteDatabase(FILE_NAME)
  }

  @After
  fun tearDown() {
    context.deleteDatabase(FILE_NAME)
  }

  @Test
  fun `la migration 1 vers 2 ajoute l heure demandee sans perdre une recherche`() {
    writeVersion(1) { db ->
      db.execSQL(
        """
        INSERT INTO search_history (
          searchedAt,
          from_stopId, from_name, from_description, from_lat, from_lon, from_kind, from_servedModes,
          to_stopId, to_name, to_description, to_lat, to_lon, to_kind, to_servedModes
        ) VALUES (
          1740816600000,
          NULL, '12 rue des Lilas', 'Paris', 48.8566, 2.3522, 'ADDRESS', '',
          'de:06:1234', 'Gare de Lyon', NULL, 48.8443, 2.3735, 'STOP', 'SUBWAY'
        )
        """.trimIndent(),
      )
    }

    val database = openMigrated()
    try {
      // L'ouverture seule vaut vérification : Room compare la base migrée au schéma de la version 2
      // et refuse de s'ouvrir au moindre écart, valeur par défaut comprise.
      val relue = runBlocking { database.historyDao().observeRecent(50).first().single() }
      assertEquals("12 rue des Lilas", relue.from.name)
      assertEquals("Gare de Lyon", relue.to.name)
      // Une recherche enregistrée avant la version 2 n'a pas d'heure demandée : elle vaut
      // « maintenant », et la puce qui la rejoue partira de l'heure qu'il est.
      assertEquals(TimeChoice.Now, relue.toHistoryEntry().time)

      // Et la colonne neuve sert vraiment : une recherche datée écrite après la migration se relit.
      val instant = 1_772_352_000_000L
      runBlocking {
        database.historyDao().record(
          SearchHistoryEntity(
            from = relue.from,
            to = relue.to,
            timeMode = TIME_MODE_ARRIVE_BY,
            timeMillis = instant,
            searchedAt = 1_740_816_660_000L,
          ),
          50,
        )
      }
      val derniere = runBlocking { database.historyDao().observeRecent(50).first().first() }
      assertEquals(TIME_MODE_ARRIVE_BY, derniere.timeMode)
      assertEquals(instant, derniere.timeMillis)
    } finally {
      database.close()
    }
  }

  @Test
  fun `la migration 2 vers 3 fusionne les doublons deja crees, sans perdre la surveillance`() {
    writeVersion(2) { db ->
      // Quatre fois le même trajet, comme l'écran de détail savait en créer avant la version 3.
      repeat(4) { rang -> db.execSQL(insertJourney(id = rang + 1, name = "Nation")) }
      // Un autre trajet, qui n'a rien à voir et doit survivre entier.
      db.execSQL(insertJourney(id = 5, name = "Opéra"))
      // La surveillance s'était attachée au troisième doublon : c'est lui qu'il faut garder.
      db.execSQL(
        "INSERT INTO watched_journeys (journeyId, departureMinuteOfDay, daysOfWeek, createdAt) " +
          "VALUES (3, 490, 'MONDAY', 1740816600000)",
      )
    }

    val database = openMigrated()
    try {
      val journeys = runBlocking { database.favoritesDao().observeJourneys().first() }
      assertEquals(2, journeys.size)
      // Le doublon conservé est celui qui portait la surveillance, et non le plus ancien.
      assertEquals(listOf(3L, 5L), journeys.map { it.id }.sorted())
      val watched = runBlocking { database.watchedJourneysDao().observeWatched().first() }
      assertEquals(listOf(3L), watched.map { it.journeyId })

      // Et l'index unique tient désormais : un cinquième essai n'ajoute plus rien.
      val existing = runBlocking {
        database.favoritesDao().findJourney("Bastille", 48.85, 2.37, "Nation", 48.84, 2.39, "TRANSIT")
      }
      assertEquals(3L, existing)
    } finally {
      database.close()
    }
  }

  /** Une ligne de `favorite_journeys` en version 2, écrite à la main. */
  private fun insertJourney(id: Int, name: String): String = """
    INSERT INTO favorite_journeys (
      id, label, category, createdAt,
      from_stopId, from_name, from_description, from_lat, from_lon, from_kind, from_servedModes,
      to_stopId, to_name, to_description, to_lat, to_lon, to_kind, to_servedModes
    ) VALUES (
      $id, NULL, 'TRANSIT', 1740816600000,
      NULL, 'Bastille', NULL, 48.85, 2.37, 'ADDRESS', '',
      NULL, '$name', NULL, 48.84, 2.39, 'ADDRESS', ''
    )
  """.trimIndent()

  /**
   * Ouvre la base avec toutes les migrations.
   *
   * L'ouverture vaut vérification : Room compare la base migrée au schéma de la version courante —
   * tables, colonnes, valeurs par défaut **et index** — et refuse de s'ouvrir au moindre écart.
   */
  private fun openMigrated(): EscaleDatabase = Room.databaseBuilder(context, EscaleDatabase::class.java, FILE_NAME)
    .addMigrations(EscaleDatabase.MIGRATION_1_2, EscaleDatabase.MIGRATION_2_3)
    .allowMainThreadQueries()
    .build()

  /**
   * Écrit une base de version [version] conforme au schéma exporté, puis y laisse écrire [seed].
   *
   * `room_master_table` et `PRAGMA user_version` sont posés comme Room les pose : sans eux, Room
   * croirait à une base créée de zéro et n'exécuterait aucune migration — le test passerait sans
   * rien avoir vérifié.
   */
  private fun writeVersion(version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
    val schema = JSONObject(
      context.assets.open("$SCHEMA_DIRECTORY/$version.json").bufferedReader().use {
        it.readText()
      },
    )
      .getJSONObject("database")
    val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(FILE_NAME)
      .callback(
        object : SupportSQLiteOpenHelper.Callback(version) {
          override fun onCreate(db: SupportSQLiteDatabase) = Unit

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
      )
      .build()
    val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    try {
      val db = helper.writableDatabase
      val entities = schema.getJSONArray("entities")
      for (index in 0 until entities.length()) {
        val entity = entities.getJSONObject(index)
        val table = "`" + entity.getString("tableName") + "`"
        db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
        val indices = entity.optJSONArray("indices") ?: continue
        for (position in 0 until indices.length()) {
          db.execSQL(indices.getJSONObject(position).getString("createSql").replace(TABLE_PLACEHOLDER, table))
        }
      }
      val setup = schema.getJSONArray("setupQueries")
      for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
      db.execSQL("PRAGMA user_version = $version")
      seed(db)
    } finally {
      helper.close()
    }
  }

  private companion object {
    const val FILE_NAME = "migration-test.db"
    const val SCHEMA_DIRECTORY = "io.github.mgdx.escale.data.db.EscaleDatabase"

    /** Le jeton que Room laisse dans le schéma exporté à la place du nom de table. */
    const val TABLE_PLACEHOLDER = "`\${TABLE_NAME}`"
  }
}
