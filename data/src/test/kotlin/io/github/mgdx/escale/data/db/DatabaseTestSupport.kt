package io.github.mgdx.escale.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TransitMode

/**
 * Une base en mémoire pour les tests JVM.
 *
 * Room a besoin de SQLite, que Robolectric fournit en JVM : les tests de persistance restent donc
 * dans `src/test`, comme le reste du module, et n'exigent pas d'appareil branché.
 */
internal fun inMemoryDatabase(): EscaleDatabase =
  Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EscaleDatabase::class.java)
    .allowMainThreadQueries()
    .build()

/** Le nombre de lignes réellement présentes dans [table], sans passer par une requête plafonnée. */
internal fun EscaleDatabase.rowCount(table: String): Int =
  openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
    cursor.moveToFirst()
    cursor.getInt(0)
  }

/** Une adresse : pas d'identifiant d'arrêt, donc une requête `plan` par coordonnées. */
internal fun address(name: String, lat: Double = 48.8566, lon: Double = 2.3522) = Location(
  id = null,
  name = name,
  description = "Paris",
  coordinates = LatLon(lat, lon),
  kind = PlaceKind.ADDRESS,
  servedModes = emptyList(),
)

/** Un arrêt : identifiant **et** coordonnées, comme l'exige SPEC.md § 5.6.1. */
internal fun stopLocation(id: String, name: String, lat: Double = 48.8443, lon: Double = 2.3735) = Location(
  id = id,
  name = name,
  description = null,
  coordinates = LatLon(lat, lon),
  kind = PlaceKind.STOP,
  servedModes = listOf(TransitMode.SUBWAY, TransitMode.REGIONAL_RAIL),
)
