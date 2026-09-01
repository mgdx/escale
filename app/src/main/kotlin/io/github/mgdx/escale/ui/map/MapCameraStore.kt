package io.github.mgdx.escale.ui.map

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.model.LatLon
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * La dernière position de caméra, mémorisée d'un lancement à l'autre (SPEC.md § 5.1).
 *
 * C'est la **première** des trois sources de cadrage initial : rouvrir l'application doit rendre la
 * carte là où on l'avait laissée, sans réseau ni permission.
 *
 * Ces trois nombres sont une donnée de localisation : ils restent dans les préférences privées de
 * l'application, ne sont jamais journalisés, et ne partent nulle part (SPEC.md § 8 et § 11).
 */
interface MapCameraMemory {
  /** Le dernier cadrage enregistré, ou `null` au premier lancement. */
  suspend fun lastCamera(): MapCamera?

  /** Enregistre le cadrage courant. */
  suspend fun save(camera: MapCamera)
}

class MapCameraStore(private val dataStore: DataStore<Preferences>) : MapCameraMemory {

  override suspend fun lastCamera(): MapCamera? {
    // Un fichier de préférences illisible ne doit pas priver la carte de son cadrage : on repart
    // simplement sur la source suivante de SPEC.md § 5.1.
    val preferences = dataStore.data
      .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
      .first()
    val lat = preferences[KEY_LATITUDE]
    val lon = preferences[KEY_LONGITUDE]
    val zoom = preferences[KEY_ZOOM]
    return if (lat != null && lon != null && zoom != null) MapCamera(LatLon(lat, lon), zoom) else null
  }

  /** Enregistre le cadrage courant. Un échec d'écriture est sans conséquence : on recadrera. */
  override suspend fun save(camera: MapCamera) {
    try {
      dataStore.edit { preferences ->
        preferences[KEY_LATITUDE] = camera.center.lat
        preferences[KEY_LONGITUDE] = camera.center.lon
        preferences[KEY_ZOOM] = camera.zoom
      }
    } catch (_: IOException) {
      // Perdre le cadrage mémorisé ne casse rien : le cadrage initial retombe sur la source
      // suivante de SPEC.md § 5.1.
    }
  }

  private companion object {
    val KEY_LATITUDE = doublePreferencesKey("map_camera_lat")
    val KEY_LONGITUDE = doublePreferencesKey("map_camera_lon")
    val KEY_ZOOM = doublePreferencesKey("map_camera_zoom")
  }
}
