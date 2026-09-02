package io.github.mgdx.escale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * L'écran de réglages (SPEC.md § 5.6).
 *
 * Les préférences sont **écrites immédiatement** : un réglage choisi est un réglage enregistré, il
 * n'y a ni bouton « Appliquer » ni état intermédiaire à perdre en cas de rotation. L'état volatil
 * — le dialogue ouvert, le message à afficher une fois — est le seul que ce `ViewModel` détienne.
 *
 * Il ne journalise rien : les préférences de recherche décrivent des habitudes de déplacement
 * (allure de marche, fauteuil roulant, véhicules refusés), et SPEC.md § 11 les veut confinées au
 * stockage privé.
 */
class SettingsViewModel(
  serverRepository: ServerRepository,
  private val preferencesRepository: PreferencesRepository,
  private val maintenance: SettingsMaintenance,
) : ViewModel() {

  private val volatileState = MutableStateFlow(VolatileState())

  val uiState: StateFlow<SettingsUiState> = combine(
    serverRepository.current,
    preferencesRepository.searchPreferences,
    preferencesRepository.displayPreferences,
    volatileState,
  ) { server, search, display, volatile ->
    SettingsUiState(
      serverUrl = server.baseUrl,
      search = search,
      display = display,
      dialog = volatile.dialog,
      message = volatile.message,
      canClearHistory = maintenance.canClearHistory,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    initialValue = SettingsUiState(canClearHistory = maintenance.canClearHistory),
  )

  fun openDialog(dialog: SettingsDialog) = volatileState.update { it.copy(dialog = dialog) }

  fun dismissDialog() = volatileState.update { it.copy(dialog = null) }

  /** Le message a été montré : on l'oublie, pour qu'une rotation ne le rejoue pas. */
  fun messageShown() = volatileState.update { it.copy(message = null) }

  fun updateSearchPreferences(preferences: SearchPreferences) {
    viewModelScope.launch {
      if (preferencesRepository.updateSearchPreferences(preferences) is Outcome.Failure) {
        show(SettingsMessage.SAVE_FAILED)
      }
    }
  }

  fun updateDisplayPreferences(preferences: DisplayPreferences) {
    viewModelScope.launch {
      if (preferencesRepository.updateDisplayPreferences(preferences) is Outcome.Failure) {
        show(SettingsMessage.SAVE_FAILED)
      }
    }
  }

  /** Rétablit les réglages de cet écran, et eux seuls : le serveur configuré n'est pas touché. */
  fun resetToDefaults() {
    viewModelScope.launch {
      val outcome = preferencesRepository.resetToDefaults()
      show(if (outcome is Outcome.Success) SettingsMessage.SETTINGS_RESET else SettingsMessage.SAVE_FAILED)
    }
  }

  fun confirmClear(target: ClearTarget) {
    viewModelScope.launch {
      val cleared = maintenance.clear(target)
      show(if (cleared) clearedMessage(target) else SettingsMessage.CLEAR_FAILED)
    }
  }

  private fun show(message: SettingsMessage) = volatileState.update { it.copy(dialog = null, message = message) }

  private fun clearedMessage(target: ClearTarget): SettingsMessage = when (target) {
    ClearTarget.HISTORY -> SettingsMessage.HISTORY_CLEARED
    ClearTarget.TILE_CACHE -> SettingsMessage.TILE_CACHE_CLEARED
    ClearTarget.RESULTS_CACHE -> SettingsMessage.RESULTS_CACHE_CLEARED
    ClearTarget.GEOCODE_CACHE -> SettingsMessage.GEOCODE_CACHE_CLEARED
  }

  /** L'état qui n'appartient à aucun dépôt : ce qui est ouvert, ce qui reste à dire. */
  private data class VolatileState(val dialog: SettingsDialog? = null, val message: SettingsMessage? = null)

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        SettingsViewModel(
          serverRepository = container.serverRepository,
          preferencesRepository = container.preferencesRepository,
          maintenance = SettingsMaintenance(
            planRepository = container.planRepository,
            geocodeRepository = container.geocodeRepository,
            // Paresseux : ouvrir les réglages ne doit pas construire la carte.
            tileCacheCleaner = { container.mapInstance.purgeTileCache() },
            // Jalon 10 : `HistoryCleaner { container.historyRepository.clear() }` (SettingsMaintenance).
            historyCleaner = null,
          ),
        )
      }
    }
  }
}
