package io.github.mgdx.escale.ui.settings

import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.result.Outcome

/**
 * Vidage du cache disque des tuiles de carte (SPEC.md § 7.10).
 *
 * Déclarée ici, et non prise directement sur `MapInstance`, pour que l'écran de réglages n'ait pas
 * à connaître MapLibre et reste assemblable sans carte. `AppContainer` la branche sur
 * `mapInstance.purgeTileCache()` — et le fait paresseusement, pour ne pas construire la carte à
 * l'ouverture des réglages.
 */
fun interface TileCacheCleaner {
  /** Rend `false` si le cache n'a pas pu être vidé, sans jamais lever. */
  suspend fun purgeTileCache(): Boolean
}

/**
 * **Point d'accroche du jalon 10 : l'effacement de l'historique de recherche.**
 *
 * L'historique n'existe pas encore (SPEC.md § 5.5, jalon 10). Le jour où `AppContainer` exposera un
 * `historyRepository`, il suffira de passer `HistoryCleaner { container.historyRepository.clear() }`
 * à [SettingsMaintenance] : l'entrée « Effacer l'historique » apparaîtra d'elle-même dans la
 * rubrique « Données », confirmation et message compris. Tant que rien n'est branché, l'entrée
 * n'est pas affichée — plutôt qu'affichée sans effet.
 */
fun interface HistoryCleaner {
  suspend fun clearHistory(): Outcome<Unit>
}

/**
 * Les effacements de la rubrique « Données » de SPEC.md § 5.6, réunis en un seul objet.
 *
 * Ils ne touchent que des caches et des données locales : rien n'est envoyé nulle part, et aucune
 * des données effacées n'est journalisée au passage (SPEC.md § 11).
 */
class SettingsMaintenance(
  private val planRepository: PlanRepository,
  private val geocodeRepository: GeocodeRepository,
  private val tileCacheCleaner: TileCacheCleaner,
  private val historyCleaner: HistoryCleaner? = null,
) {

  /** Vrai quand l'historique peut réellement être effacé, donc quand l'entrée doit s'afficher. */
  val canClearHistory: Boolean = historyCleaner != null

  /** Rend `false` quand l'effacement a échoué : l'écran le dit, sans détailler la cause. */
  suspend fun clear(target: ClearTarget): Boolean = when (target) {
    ClearTarget.HISTORY -> historyCleaner?.clearHistory() is Outcome.Success
    ClearTarget.TILE_CACHE -> tileCacheCleaner.purgeTileCache()
    ClearTarget.RESULTS_CACHE -> planRepository.clearCache() is Outcome.Success
    ClearTarget.GEOCODE_CACHE -> geocodeRepository.clearGeocodeCache() is Outcome.Success
  }
}
