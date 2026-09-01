package io.github.mgdx.escale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.data.net.MotisClient
import io.github.mgdx.escale.data.prefs.ServerRepositoryImpl
import io.github.mgdx.escale.data.repository.PlanCache
import io.github.mgdx.escale.data.repository.PlanRepositoryImpl

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
   * Cache mémoire des résultats de recherche (SPEC.md § 7.5). Exposé à part du dépôt parce que
   * l'écran « Serveur MOTIS » doit pouvoir le vider au changement de serveur (SPEC.md § 4.1).
   */
  val planCache: PlanCache by lazy { PlanCache() }

  /** Recherche d'itinéraire : une requête par onglet, mise en cache pour la durée de la recherche. */
  val planRepository: PlanRepository by lazy {
    PlanRepositoryImpl(motisClient, serverRepository, planCache)
  }

  private companion object {
    const val PREFERENCES_NAME = "escale"
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
