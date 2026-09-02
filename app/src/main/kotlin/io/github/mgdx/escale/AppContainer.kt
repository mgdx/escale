package io.github.mgdx.escale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.data.net.GeocodeApi
import io.github.mgdx.escale.data.net.MapApi
import io.github.mgdx.escale.data.net.MotisClient
import io.github.mgdx.escale.data.prefs.ServerRepositoryImpl
import io.github.mgdx.escale.data.repository.GeocodeRepositoryImpl
import io.github.mgdx.escale.data.repository.MapRepositoryImpl
import io.github.mgdx.escale.data.repository.PlanCache
import io.github.mgdx.escale.data.repository.PlanRepositoryImpl
import io.github.mgdx.escale.ui.map.DeviceLocationSource
import io.github.mgdx.escale.ui.map.MapCameraStore
import io.github.mgdx.escale.ui.map.MapInstance
import io.github.mgdx.escale.ui.map.MapSelection
import io.github.mgdx.escale.ui.map.MapStyles
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.CleartextConsentStore
import io.github.mgdx.escale.ui.server.DataStoreCleartextConsentStore
import io.github.mgdx.escale.ui.session.SearchSession
import java.io.File

/**
 * Le graphe de dépendances de l'application, écrit à la main.
 *
 * **Hilt, Dagger, Koin et tout autre conteneur sont exclus** par SPEC.md § 3 : à cette échelle, le
 * graphe tient en quelques dizaines de lignes lisibles d'un bloc, là où un générateur de code
 * ajouterait une étape de compilation et des erreurs opaques pour un gain nul.
 *
 * Tout est exposé en `val` paresseux : rien n'est construit tant qu'un écran ne le demande, ce qui
 * protège le démarrage à froid visé par SPEC.md § 2.
 */
class AppContainer(context: Context) {

  private val appContext: Context = context.applicationContext

  /**
   * Client HTTP unique. La version vient de `BuildConfig`, jamais d'une constante recopiée :
   * l'en-tête `User-Agent` doit rester exact d'une version à l'autre (SPEC.md § 4.2).
   */
  val motisClient: MotisClient by lazy { MotisClient(versionName = BuildConfig.VERSION_NAME) }

  /** Réglages : serveur configuré, préférences de recherche et d'affichage. */
  val preferences: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile(PREFERENCES_NAME) }
  }

  val serverRepository: ServerRepository by lazy {
    ServerRepositoryImpl(preferences, motisClient)
  }

  /**
   * Autocomplétion et géocodage inverse.
   *
   * Le cache disque de 24 h de SPEC.md § 7.5 tient dans un sous-répertoire du cache **privé** de
   * l'application : les lieux cherchés par l'usager ne sortent pas du bac à sable (SPEC.md § 11),
   * et `clearGeocodeCache()` les efface au changement de serveur (SPEC.md § 5.6.1).
   */
  val geocodeRepository: GeocodeRepository by lazy {
    GeocodeRepositoryImpl(
      api = GeocodeApi(
        versionName = BuildConfig.VERSION_NAME,
        cacheDirectory = File(appContext.cacheDir, GEOCODE_CACHE_DIRECTORY),
      ),
      serverRepository = serverRepository,
    )
  }

  /**
   * Consentement au trafic en clair, mémorisé une fois par hôte (docs/architecture.md § 11.1).
   *
   * C'est lui, et non la `network_security_config`, qui garantit qu'aucune requête `http://` ne part
   * sans un accord explicite de l'usager.
   */
  val cleartextConsentStore: CleartextConsentStore by lazy {
    DataStoreCleartextConsentStore(preferences)
  }

  /**
   * Cache mémoire des résultats de recherche (SPEC.md § 7.5). Exposé à part du dépôt parce que
   * l'écran « Serveur MOTIS » doit pouvoir le vider au changement de serveur (SPEC.md § 4.1).
   */
  val planCache: PlanCache by lazy { PlanCache() }

  /** Recherche d'itinéraire : une requête par onglet, mise en cache pour la durée de la recherche. */
  val planRepository: PlanRepository by lazy {
    PlanRepositoryImpl(motisClient, serverRepository, planCache)
  }

  /** Cadrage initial proposé par le serveur, dernier recours du cadrage de SPEC.md § 5.1. */
  val mapRepository: MapRepository by lazy {
    MapRepositoryImpl(MapApi(versionName = BuildConfig.VERSION_NAME), serverRepository)
  }

  /**
   * **L'unique carte de l'application** (SPEC.md § 5.7, règle 8).
   *
   * Elle est détenue ici, et non par un écran, parce qu'elle ne doit jamais être détruite puis
   * recréée lors d'un changement d'écran. Le premier accès construit le `MapView` : il doit donc
   * venir du fil principal, ce qui est le cas puisque seul un composable le demande.
   */
  val mapInstance: MapInstance by lazy { MapInstance(appContext) }

  /**
   * Le trajet choisi dans la feuille de résultats, que la carte trace et cadre (SPEC.md § 5.1).
   *
   * Il est partagé ici pour que la feuille de résultats et la carte n'aient pas à se connaître,
   * comme [mapSelection] et [searchSession]. Le magasin lui-même reste dans `ui/results`, avec le
   * lot qui l'écrit ; ce conteneur n'en publie que l'unique instance, à celui qui la lit.
   */
  val selectedJourneyStore: SelectedJourneyStore by lazy { SelectedJourneyStore.shared }

  /** Les feuilles de style embarquées, en clair et en sombre (SPEC.md § 5.7). */
  val mapStyles: MapStyles by lazy { MapStyles(appContext.resources) }

  /** La dernière position de caméra, mémorisée d'un lancement à l'autre (SPEC.md § 5.1). */
  val mapCameraStore: MapCameraStore by lazy { MapCameraStore(preferences) }

  /**
   * La position de l'appareil, par le `LocationManager` de la plateforme et jamais par les
   * services Google (SPEC.md § 3 et § 5.1).
   */
  val deviceLocationSource: DeviceLocationSource by lazy { DeviceLocationSource(appContext) }

  /**
   * Le point choisi par appui long sur la carte, en attente d'être consommé par l'écran de
   * recherche (SPEC.md § 5.1). Partagé ici pour que les deux écrans n'aient pas à se connaître.
   */
  val mapSelection: MapSelection by lazy { MapSelection() }

  /**
   * La recherche en cours — départ, arrivée, heure (SPEC.md § 5.1).
   *
   * Elle est détenue ici parce qu'elle n'appartient à aucun des deux écrans qui s'en servent : la
   * carte de recherche la remplit, la feuille de résultats la lit, et « dès que Départ et Arrivée
   * sont renseignés, la recherche se lance ». Les deux lots la partagent sans se connaître
   * (docs/architecture.md § 11.4).
   */
  val searchSession: SearchSession by lazy { SearchSession() }

  private companion object {
    const val PREFERENCES_NAME = "escale"
    const val GEOCODE_CACHE_DIRECTORY = "geocode-http"
  }
}

/**
 * Le conteneur de l'application, atteint depuis un composable.
 *
 * C'est le seul point de passage vers les dépôts : un `ViewModel` les reçoit ensuite par son
 * constructeur, via la fabrique déclarée dans son propre fichier (docs/architecture.md § 3,
 * règle 3).
 */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as EscaleApplication).container
