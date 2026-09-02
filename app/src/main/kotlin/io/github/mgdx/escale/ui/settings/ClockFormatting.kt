package io.github.mgdx.escale.ui.settings

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.format.uses24Hour
import io.github.mgdx.escale.core.model.DisplayPreferences

/**
 * Le format d'heure en vigueur : le réglage de SPEC.md § 5.6 s'il est explicite, celui de
 * l'appareil sinon.
 *
 * **C'est le point de lecture unique du réglage `clockFormat`.** Tout écran qui écrit une heure
 * passe par ici plutôt que par `DateFormat.is24HourFormat(context)` : sans quoi le choix de
 * l'usager s'appliquerait à un écran et pas à l'autre.
 *
 * La règle elle-même — trois valeurs réduites à un booléen — vit dans `:core.format`, avec ses
 * tests. Cette fonction n'est que la plomberie : la préférence d'un côté, le réglage du système de
 * l'autre.
 */
@Composable
fun uses24HourClock(): Boolean {
  val context = LocalContext.current
  val display by appContainer().preferencesRepository.displayPreferences
    .collectAsStateWithLifecycle(DisplayPreferences())
  return display.clockFormat.uses24Hour(DateFormat.is24HourFormat(context))
}
