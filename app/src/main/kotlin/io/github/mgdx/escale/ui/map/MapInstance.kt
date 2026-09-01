package io.github.mgdx.escale.ui.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.MainThread
import androidx.core.content.res.ResourcesCompat
import io.github.mgdx.escale.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineManager
import kotlin.coroutines.resume

/**
 * L'unique carte de l'application, pour toute la durée de vie du processus.
 *
 * **C'est la règle 8 de SPEC.md § 5.7, et c'est la contrainte la plus structurante du jalon :** « La
 * carte n'est jamais détruite puis recréée lors d'un changement d'écran : une seule instance pour
 * toute la durée de vie de l'application, à laquelle on ajoute et retire des couches. » Recréer un
 * `MapView` coûte le chargement du moteur natif, la relecture du style, la retraduction des tuiles
 * et une image blanche à chaque navigation.
 *
 * D'où cette classe, détenue par l'`AppContainer` — donc par l'`Application` — et non par un
 * composable. Le `MapView` est construit **avec le contexte d'application** : le retenir avec un
 * contexte d'activité fuirait l'activité à chaque rotation. Les écrans ne font que l'attacher, le
 * détacher, et poser ou retirer des couches sur son style.
 *
 * Le cycle de vie suit celui de l'écran affiché (`onStart` / `onStop` mettent le rendu en pause),
 * mais [destroy] n'est appelée nulle part en fonctionnement normal : détruire l'instance est
 * précisément ce que la règle 8 interdit.
 */
class MapInstance(context: Context) {

  private val appContext: Context = context.applicationContext

  private val mapState = MutableStateFlow<MapLibreMap?>(null)

  /** La carte, dès que le moteur natif l'a rendue prête. Nulle avant la première image. */
  val map: StateFlow<MapLibreMap?> = mapState.asStateFlow()

  private var mapView: MapView? = null
  private var memoryCallbacks: ComponentCallbacks2? = null

  /**
   * La vue de carte, créée au premier appel et réutilisée pour toujours.
   *
   * À appeler depuis le fil principal : un `MapView` est une vue Android.
   */
  @MainThread
  fun view(): MapView = mapView ?: create().also { mapView = it }

  /**
   * Vide le cache disque des tuiles (SPEC.md § 7.10).
   *
   * Nommée pour être appelée depuis l'écran des réglages, qui doit proposer cette purge. Rend
   * `false` si le cache n'a pas pu être vidé, sans jamais lever.
   */
  suspend fun purgeTileCache(): Boolean = suspendCancellableCoroutine { continuation ->
    OfflineManager.getInstance(appContext).clearAmbientCache(
      object : OfflineManager.FileSourceCallback {
        override fun onSuccess() {
          continuation.resume(true)
        }

        override fun onError(message: String) {
          // Le message vient de MapLibre et ne porte aucune donnée d'usager, mais SPEC.md § 8
          // interdit de journaliser quoi que ce soit de la carte : on rend un échec, c'est tout.
          continuation.resume(false)
        }
      },
    )
  }

  /**
   * Détruit l'instance. **Aucun changement d'écran ne doit l'appeler** (règle 8) : elle n'existe
   * que pour un arrêt volontaire du processus, et pour que la libération des ressources natives
   * soit écrite quelque part plutôt que laissée au ramasse-miettes.
   */
  @MainThread
  fun destroy() {
    memoryCallbacks?.let(appContext::unregisterComponentCallbacks)
    memoryCallbacks = null
    mapView?.onDestroy()
    mapView = null
    mapState.value = null
  }

  private fun create(): MapView {
    // À appeler avant toute construction de MapView. Aucune clé d'API : les tuiles viennent du
    // serveur MOTIS configuré, pas d'un fournisseur commercial.
    MapLibre.getInstance(appContext)

    val options = MapLibreMapOptions.createFromAttributes(appContext)
      // Attribution et logo sont dessinés par l'application, pour respecter les encarts système
      // et le thème Material (SPEC.md § 4.2 et § 5.7).
      .logoEnabled(false)
      .attributionEnabled(false)
      // La boussole ne s'affiche qu'une fois la carte tournée, et permet de revenir au nord.
      .compassEnabled(true)
      .compassFadesWhenFacingNorth(true)
      // L'inclinaison n'apporte rien à une carte de déplacement et rend les libellés illisibles.
      .tiltGesturesEnabled(false)
      .minZoomPreference(MIN_ZOOM)
      .maxZoomPreference(MAX_ZOOM)
      // Évite le flash blanc au démarrage en mode sombre, avant la première image.
      .foregroundLoadColor(loadingColor())

    val created = MapView(appContext, options)
    created.onCreate(null)
    created.getMapAsync { ready -> mapState.value = ready }
    limitTileCache()
    registerMemoryCallbacks()
    return created
  }

  /** SPEC.md § 7.10 : « plafond de 100 Mo, purgeable depuis les réglages ». */
  private fun limitTileCache() {
    OfflineManager.getInstance(appContext).setMaximumAmbientCacheSize(
      MAX_TILE_CACHE_BYTES,
      object : OfflineManager.FileSourceCallback {
        override fun onSuccess() = Unit

        override fun onError(message: String) = Unit
      },
    )
  }

  /**
   * Le système avertit d'une pression mémoire par l'`Application`, jamais par une vue. Comme la
   * carte survit à tous les écrans, c'est ici qu'on s'abonne, et non dans `EscaleApplication`.
   */
  private fun registerMemoryCallbacks() {
    val callbacks = object : ComponentCallbacks2 {
      override fun onConfigurationChanged(newConfig: Configuration) = Unit

      @Deprecated("Conservée pour ComponentCallbacks2, que le système appelle encore avant API 34.")
      override fun onLowMemory() {
        mapView?.onLowMemory()
      }

      override fun onTrimMemory(level: Int) {
        // Les niveaux `TRIM_MEMORY_*` sont dépréciés depuis l'API 35, mais le système continue
        // d'appeler ce rappel. `MapView.onLowMemory()` se contente de vider les tuiles en cache :
        // le faire à chaque avertissement, quel qu'en soit le niveau, est sans risque et rend la
        // carte plus sobre quand l'application passe en arrière-plan.
        mapView?.onLowMemory()
      }
    }
    appContext.registerComponentCallbacks(callbacks)
    memoryCallbacks = callbacks
  }

  private fun loadingColor(): Int =
    ResourcesCompat.getColor(appContext.resources, R.color.map_loading_background, appContext.theme)

  private companion object {
    /** Sous le zoom 1, la planète se répète et le rendu n'apporte plus rien. */
    const val MIN_ZOOM = 1.0

    /** Le jeu de tuiles MOTIS s'arrête au zoom 20 ; au-delà, MapLibre agrandit la dernière tuile. */
    const val MAX_ZOOM = 20.0

    /** SPEC.md § 7.10 : 100 Mo. */
    const val MAX_TILE_CACHE_BYTES = 100L * 1024 * 1024
  }
}
