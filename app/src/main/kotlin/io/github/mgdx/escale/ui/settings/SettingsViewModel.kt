package io.github.mgdx.escale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.repository.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(serverRepository: ServerRepository) : ViewModel() {

  val uiState: StateFlow<SettingsUiState> = serverRepository.current
    .map { SettingsUiState(serverUrl = it.baseUrl) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = SettingsUiState(),
    )

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer { SettingsViewModel(container.serverRepository) }
    }
  }
}
