package io.github.mgdx.escale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mgdx.escale.nav.EscaleNavHost
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'unique activité de l'application.
 *
 * Plein écran (`enableEdgeToEdge`) parce que l'écran d'accueil deviendra la carte, du bord haut au
 * bord bas, tout le reste flottant par-dessus (SPEC.md § 5.1).
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      EscaleTheme {
        EscaleNavHost()
      }
    }
  }
}
