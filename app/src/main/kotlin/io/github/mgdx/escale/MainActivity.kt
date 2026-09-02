package io.github.mgdx.escale

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.ThemeChoice
import io.github.mgdx.escale.nav.EscaleNavHost
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'unique activité de l'application.
 *
 * Plein écran (`enableEdgeToEdge`) parce que l'écran d'accueil deviendra la carte, du bord haut au
 * bord bas, tout le reste flottant par-dessus (SPEC.md § 5.1).
 *
 * C'est ici que le choix de thème de SPEC.md § 5.6 devient effectif : l'activité observe la
 * préférence et en déduit le thème, plutôt que de suivre le système sans condition. Tant que
 * DataStore n'a pas émis, la valeur par défaut du domaine s'applique — « comme le système », donc
 * exactement le comportement d'avant ce réglage.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val preferences = (application as EscaleApplication).container.preferencesRepository
      val display by preferences.displayPreferences.collectAsStateWithLifecycle(DisplayPreferences())
      val darkTheme = when (display.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
      }
      // Les icônes des barres système suivent le thème choisi et non celui de l'appareil : sans
      // cela, un thème sombre forcé sur un téléphone en clair afficherait des icônes noires sur
      // fond noir. Les fonds restent transparents, la carte passe dessous (SPEC.md § 5.1).
      LaunchedEffect(darkTheme) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
          navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        )
      }
      EscaleTheme(darkTheme = darkTheme) {
        EscaleNavHost()
      }
    }
  }
}
