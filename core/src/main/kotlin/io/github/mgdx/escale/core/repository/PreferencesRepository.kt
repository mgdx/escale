package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** Réglages persistés (SPEC.md § 5.6), stockés en DataStore Preferences. */
interface PreferencesRepository {
  /** Préférences envoyées au serveur avec chaque requête `plan`. */
  val searchPreferences: Flow<SearchPreferences>

  /** Préférences purement locales : thème, format d'heure, couches de la carte. */
  val displayPreferences: Flow<DisplayPreferences>

  suspend fun updateSearchPreferences(preferences: SearchPreferences): Outcome<Unit>

  suspend fun updateDisplayPreferences(preferences: DisplayPreferences): Outcome<Unit>

  /** Rétablit toutes les valeurs par défaut. */
  suspend fun resetToDefaults(): Outcome<Unit>
}
