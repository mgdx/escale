package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Des réglages en mémoire, pour éprouver la bascule « historique désactivé » de SPEC.md § 5.5. */
internal class FakePreferencesRepository(historyEnabled: Boolean = true) : PreferencesRepository {

  private val display = MutableStateFlow(DisplayPreferences(historyEnabled = historyEnabled))

  override val searchPreferences: Flow<SearchPreferences> = MutableStateFlow(SearchPreferences())

  override val displayPreferences: Flow<DisplayPreferences> = display

  fun setHistoryEnabled(enabled: Boolean) {
    display.value = display.value.copy(historyEnabled = enabled)
  }

  override suspend fun updateSearchPreferences(preferences: SearchPreferences): Outcome<Unit> = Outcome.Success(Unit)

  override suspend fun updateDisplayPreferences(preferences: DisplayPreferences): Outcome<Unit> {
    display.value = preferences
    return Outcome.Success(Unit)
  }

  override suspend fun resetToDefaults(): Outcome<Unit> = Outcome.Success(Unit)
}
