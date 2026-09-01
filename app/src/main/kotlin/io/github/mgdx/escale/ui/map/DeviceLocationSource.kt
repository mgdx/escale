package io.github.mgdx.escale.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.mgdx.escale.core.model.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * La position de l'appareil, lue par le `LocationManager` de la plateforme.
 *
 * **Jamais les services de localisation Google** : SPEC.md § 3 et CLAUDE.md excluent toute
 * dépendance aux services Play, c'est rédhibitoire pour F-Droid. Le `LocationManager` de l'AOSP
 * suffit largement pour centrer une carte.
 *
 * Aucune position n'est journalisée, en débogage comme en production (SPEC.md § 8 et § 11), et
 * aucune n'est écrite ailleurs que dans la mémoire de l'écran qui la demande.
 *
 * La permission n'est jamais demandée ici : c'est l'interface qui la demande à l'usage, au premier
 * appui sur le bouton de position (SPEC.md § 5.1). Toutes les fonctions de cette classe rendent
 * simplement « rien » quand la permission manque.
 */
interface LocationSource {
  /** Vrai si l'application peut déjà lire une position approchée, sans rien demander à l'usager. */
  fun hasCoarsePermission(): Boolean

  /** Vrai si l'application peut lire une position précise. */
  fun hasFinePermission(): Boolean

  /** La dernière position connue du système, **sans aucune demande de permission**. */
  fun lastKnownLocation(): LatLon?

  /** Les positions successives de l'appareil, tant que le flux est collecté. */
  fun locations(): Flow<LatLon>
}

class DeviceLocationSource(context: Context) : LocationSource {

  private val appContext: Context = context.applicationContext

  private val locationManager: LocationManager?
    get() = ContextCompat.getSystemService(appContext, LocationManager::class.java)

  override fun hasCoarsePermission(): Boolean = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

  override fun hasFinePermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

  /**
   * La dernière position connue du système, **sans aucune demande de permission**.
   *
   * C'est la deuxième source de cadrage initial de SPEC.md § 5.1 : « à défaut la position de
   * l'utilisateur si elle est déjà connue sans demande de permission ». Rend `null` si la
   * permission manque, si aucun fournisseur n'en a, ou si le système la refuse.
   */
  override fun lastKnownLocation(): LatLon? {
    if (!hasCoarsePermission()) return null
    val manager = locationManager ?: return null
    return PROVIDERS
      .mapNotNull { provider -> lastKnownFrom(manager, provider) }
      .maxByOrNull { it.time }
      ?.let { LatLon(it.latitude, it.longitude) }
  }

  /**
   * Les positions successives de l'appareil, tant que le flux est collecté.
   *
   * Le flux se termine immédiatement si la permission manque : l'application reste utilisable sans
   * localisation (SPEC.md § 11), le bouton se contente de rester dans son état « position
   * inconnue ». Arrêter la collecte coupe les mises à jour : rien ne tourne en fond
   * (SPEC.md § 7.7).
   */
  override fun locations(): Flow<LatLon> = callbackFlow {
    val manager = locationManager
    if (!hasCoarsePermission() || manager == null) {
      close()
      return@callbackFlow
    }

    val listener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        trySend(LatLon(location.latitude, location.longitude))
      }

      // Rappels abstraits avant l'API 30 : sans eux, l'écouteur n'est pas instanciable.
      @Deprecated("Retiré à l'API 30, mais toujours abstrait en deçà du minSdk 26 du projet.")
      override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

      override fun onProviderEnabled(provider: String) = Unit

      override fun onProviderDisabled(provider: String) = Unit
    }

    val registered = PROVIDERS.count { provider -> requestUpdates(manager, provider, listener) }
    if (registered == 0) {
      close()
      return@callbackFlow
    }

    // Une première valeur tout de suite : attendre le premier point GPS laisserait le bouton
    // inerte plusieurs secondes.
    lastKnownLocation()?.let(::trySend)

    awaitClose { manager.removeUpdates(listener) }
  }.flowOn(Dispatchers.Main.immediate)

  private fun lastKnownFrom(manager: LocationManager, provider: String): Location? = try {
    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
  } catch (_: SecurityException) {
    // La permission a pu être révoquée entre la vérification et l'appel.
    null
  } catch (_: IllegalArgumentException) {
    // Fournisseur absent de cet appareil.
    null
  }

  private fun requestUpdates(manager: LocationManager, provider: String, listener: LocationListener): Boolean = try {
    if (!manager.isProviderEnabled(provider)) {
      false
    } else {
      manager.requestLocationUpdates(
        provider,
        MIN_INTERVAL_MILLIS,
        MIN_DISTANCE_METERS,
        listener,
        Looper.getMainLooper(),
      )
      true
    }
  } catch (_: SecurityException) {
    // Avec la seule permission approchée, certains appareils refusent le fournisseur GPS.
    false
  } catch (_: IllegalArgumentException) {
    false
  }

  private fun isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

  private companion object {
    /**
     * Les deux fournisseurs de l'AOSP. Le réseau suffit à la permission approchée ; le GPS n'est
     * atteignable qu'avec la permission précise sur une partie des appareils, et son échec est
     * simplement ignoré.
     */
    val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

    /** Une position toutes les deux secondes au plus : suffisant pour suivre un piéton. */
    const val MIN_INTERVAL_MILLIS = 2_000L

    /** Sous cinq mètres, le point ne bouge pas visiblement à l'écran. */
    const val MIN_DISTANCE_METERS = 5f
  }
}

/**
 * La permission du manifeste correspondante.
 *
 * Elle vit ici, avec la lecture de la position, et non dans l'état d'écran : `android.Manifest`
 * n'a rien à faire dans un `data class` d'interface.
 */
fun LocationPermission.manifestPermission(): String = when (this) {
  LocationPermission.COARSE -> Manifest.permission.ACCESS_COARSE_LOCATION
  LocationPermission.FINE -> Manifest.permission.ACCESS_FINE_LOCATION
}
