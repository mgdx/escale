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
 * L'effacement en bloc de l'historique de recherche (SPEC.md § 5.5 et § 5.6).
 *
 * Déclarée ici, et non prise directement sur `HistoryRepository`, pour la même raison que
 * [TileCacheCleaner] : l'écran de réglages n'a pas à connaître les dépôts qu'il ne fait que vider.
 * Elle reste facultative — `null` masque l'entrée plutôt que de l'afficher sans effet —, ce qui
 * permet aux aperçus et aux tests de composer l'écran sans base de données.
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

  /** Vrai quand l'historique peut réellement être effacé, donc quand l'entrée s'affiche. */
  val canClearHistory: Boolean = historyCleaner != null

  /** Rend `false` quand l'effacement a échoué : l'écran le dit, sans détailler la cause. */
  suspend fun clear(target: ClearTarget): Boolean = when (target) {
    ClearTarget.HISTORY -> historyCleaner?.clearHistory() is Outcome.Success
    ClearTarget.TILE_CACHE -> tileCacheCleaner.purgeTileCache()
    ClearTarget.RESULTS_CACHE -> planRepository.clearCache() is Outcome.Success
    ClearTarget.GEOCODE_CACHE -> geocodeRepository.clearGeocodeCache() is Outcome.Success
  }
}
