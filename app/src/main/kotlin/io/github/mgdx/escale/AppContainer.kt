package io.github.mgdx.escale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.repository.MapRepository
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.net.GeocodeApi
import io.github.mgdx.escale.data.net.MapApi
import io.github.mgdx.escale.data.net.MotisClient
import io.github.mgdx.escale.data.net.RentalsApi
import io.github.mgdx.escale.data.net.StopsApi
import io.github.mgdx.escale.data.net.TripApi
import io.github.mgdx.escale.data.prefs.PreferencesRepositoryImpl
import io.github.mgdx.escale.data.prefs.ServerRepositoryImpl
import io.github.mgdx.escale.data.repository.FavoritesRepositoryImpl
import io.github.mgdx.escale.data.repository.GeocodeRepositoryImpl
import io.github.mgdx.escale.data.repository.HistoryRepositoryImpl
import io.github.mgdx.escale.data.repository.MapRepositoryImpl
import io.github.mgdx.escale.data.repository.PlanCache
import io.github.mgdx.escale.data.repository.PlanRepositoryImpl
import io.github.mgdx.escale.data.repository.RentalsRepositoryImpl
import io.github.mgdx.escale.data.repository.StopsRepositoryImpl
import io.github.mgdx.escale.data.repository.TripRepositoryImpl
import io.github.mgdx.escale.data.repository.WatchedJourneysRepositoryImpl
import io.github.mgdx.escale.ui.map.DeviceLocationSource
import io.github.mgdx.escale.ui.map.MapCameraStore
import io.github.mgdx.escale.ui.map.MapInstance
import io.github.mgdx.escale.ui.map.MapSelection
import io.github.mgdx.escale.ui.map.MapStyles
import io.github.mgdx.escale.ui.map.StopDepartureRequests
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.server.CleartextConsentStore
import io.github.mgdx.escale.ui.server.DataStoreCleartextConsentStore
import io.github.mgdx.escale.ui.session.SearchSession
import io.github.mgdx.escale.ui.watch.DataStoreWatchSettingsStore
import io.github.mgdx.escale.ui.watch.WatchOpenRequests
import io.github.mgdx.escale.ui.watch.WatchSettingsStore
import io.github.mgdx.escale.work.WatchAlarms
import io.github.mgdx.escale.work.WatchNotifications
import io.github.mgdx.escale.work.WatchNotifier
import io.github.mgdx.escale.work.WatchScheduler
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
   * Réglages de recherche et d'affichage (SPEC.md § 5.6).
   *
   * Il partage le fichier DataStore du serveur configuré : c'est le même stockage privé, et il ne
   * possède que ses propres clés — « rétablir les valeurs par défaut » n'efface pas le serveur.
   */
  val preferencesRepository: PreferencesRepository by lazy {
    PreferencesRepositoryImpl(preferences)
  }

  /**
   * La base locale : favoris, historique et trajets surveillés (SPEC.md § 5.5).
   *
   * Elle est **privée**. C'est la donnée la plus sensible de l'application — le domicile, le lieu
   * de travail, tout ce qui a été cherché — et rien n'y accède autrement que par les trois dépôts
   * ci-dessous. Exposer la base laisserait un écran écrire une requête à lui, hors de tout contrat.
   */
  private val database: EscaleDatabase by lazy { EscaleDatabase.create(appContext) }

  /** Domicile, travail, lieux, arrêts et trajets favoris (SPEC.md § 5.5). */
  val favoritesRepository: FavoritesRepository by lazy { FavoritesRepositoryImpl(database) }

  /**
   * Les dernières recherches (SPEC.md § 5.5).
   *
   * Il reçoit [preferencesRepository] parce que la bascule « historique désactivé » y vit déjà :
   * `DisplayPreferences.historyEnabled`. Réglage éteint, `record()` n'écrit rien.
   */
  val historyRepository: HistoryRepository by lazy {
    HistoryRepositoryImpl(database, preferencesRepository)
  }

  /**
   * Les trajets surveillés (SPEC.md § 5.5.1), **persistance seulement**.
   *
   * Aucune tâche `WorkManager`, aucune notification, aucune permission : le lot qui les écrira se
   * branchera ici. La limite de cinq est appliquée à l'écriture, d'après `JourneyWatchLimit`.
   */
  val watchedJourneysRepository: WatchedJourneysRepository by lazy {
    WatchedJourneysRepositoryImpl(database)
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

  /**
   * Les arrêts affichés sur la carte (SPEC.md § 5.7).
   *
   * Le dépôt porte le cache par emprise et par palier de la règle 4 : une emprise déjà couverte
   * n'est pas redemandée, et une réponse vaut dix minutes.
   */
  val stopsRepository: StopsRepository by lazy {
    StopsRepositoryImpl(StopsApi(versionName = BuildConfig.VERSION_NAME), serverRepository)
  }

  /**
   * Les stations et véhicules en libre-service (SPEC.md § 5.3 et § 5.7).
   *
   * Le dépôt porte le cache de la règle 4 de SPEC.md § 5.7, mais réglé à **soixante secondes** et
   * non à dix minutes : une disponibilité est une donnée volatile, et l'afficher périmée revient à
   * envoyer quelqu'un devant une station vide.
   */
  val rentalsRepository: RentalsRepository by lazy {
    RentalsRepositoryImpl(RentalsApi(versionName = BuildConfig.VERSION_NAME), serverRepository)
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
   * L'arrêt dont l'usager a demandé les prochains départs depuis la carte (SPEC.md § 5.7).
   *
   * **Point d'accroche du jalon 9** : l'écran des prochains départs n'existe pas encore, et la
   * carte n'a pas à le connaître. Elle dépose sa demande ici, la navigation la consommera — même
   * dispositif que [mapSelection] pour l'écran de recherche.
   */
  val stopDepartureRequests: StopDepartureRequests by lazy { StopDepartureRequests() }

  /**
   * La recherche en cours — départ, arrivée, heure (SPEC.md § 5.1).
   *
   * Elle est détenue ici parce qu'elle n'appartient à aucun des deux écrans qui s'en servent : la
   * carte de recherche la remplit, la feuille de résultats la lit, et « dès que Départ et Arrivée
   * sont renseignés, la recherche se lance ». Les deux lots la partagent sans se connaître
   * (docs/architecture.md § 11.4).
   */
  val searchSession: SearchSession by lazy { SearchSession() }

  /**
   * Les prochains départs à un arrêt et la desserte d'une course (SPEC.md § 5.4 et § 5.3).
   *
   * **Aucun cache**, contrairement à [stopsRepository] : un horaire temps réel se périme en
   * secondes, et le servir depuis une mémoire annoncerait un retard qui n'est plus vrai. La
   * fraîcheur est réglée par `RealtimeRefreshPolicy` et par le geste de l'usager (SPEC.md § 7.4).
   */
  val tripRepository: TripRepository by lazy {
    TripRepositoryImpl(TripApi(versionName = BuildConfig.VERSION_NAME), serverRepository)
  }

  /**
   * Les réglages de la surveillance et le résultat de la dernière vérification (SPEC.md § 5.5.1).
   *
   * Il partage le fichier DataStore des autres réglages et ne possède que ses propres clés, comme
   * [mapCameraStore] et [cleartextConsentStore].
   */
  val watchSettingsStore: WatchSettingsStore by lazy { DataStoreWatchSettingsStore(preferences) }

  /**
   * La programmation des trajets surveillés (SPEC.md § 5.5.1), **seul travail de fond autorisé**.
   *
   * Il n'émet que des tâches à **exécution unique**, replanifiées après chaque exécution : SPEC.md
   * § 7.7 interdit toute tâche périodique, tout service et toute synchronisation.
   */
  val watchScheduler: WatchAlarms by lazy { WatchScheduler(appContext) }

  /** Le canal dédié et silencieux des trajets surveillés (SPEC.md § 5.5.1). */
  val watchNotifications: WatchNotifier by lazy { WatchNotifications(appContext) }

  /**
   * Le trajet surveillé qu'un appui sur une notification demande à ouvrir (SPEC.md § 5.5.1).
   *
   * Partagé ici pour que la notification et la navigation n'aient pas à se connaître, comme
   * [stopDepartureRequests] et [mapSelection] (docs/architecture.md § 11.4).
   */
  val watchOpenRequests: WatchOpenRequests by lazy { WatchOpenRequests() }

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
