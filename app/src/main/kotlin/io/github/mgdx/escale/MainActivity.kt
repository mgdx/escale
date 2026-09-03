package io.github.mgdx.escale

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.ThemeChoice
import io.github.mgdx.escale.nav.EscaleNavHost
import io.github.mgdx.escale.ui.map.MapInstance
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
      val container = (application as EscaleApplication).container
      val display by container.preferencesRepository.displayPreferences
        .collectAsStateWithLifecycle(DisplayPreferences())
      ReportMapDrawn(container.mapInstance)
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

  /**
   * Signale au système que l'application est réellement affichée, **au sens de SPEC.md § 5.7** :
   * « démarrage à froid jusqu'à la première image de carte : moins de 1,5 s ».
   *
   * **Instrument de mesure, pas fonctionnalité.** Ce code ne change rien à ce qui s'affiche ; il
   * n'existe que pour rendre l'objectif vérifiable. Sans lui, la seule mesure disponible est celle
   * d'`am start -W`, qui rend la première image de l'**activité** — la carte, elle, s'initialise
   * après, et l'objectif de la spec reste alors non mesuré. `reportFullyDrawn()` fait journaliser
   * au système une ligne `Fully drawn …: +XXXms` comptée depuis le lancement du processus.
   *
   * L'état vient de la carte et non de l'activité, parce que la carte lui survit (règle 8 du
   * § 5.7) : au second lancement, l'image est déjà rendue et le témoin vaut `true` d'emblée, si
   * bien que l'appel part aussitôt. `reportFullyDrawn()` appelé deux fois est ignoré par le
   * système, et seul le démarrage à froid produit une mesure ayant un sens.
   */
  @Composable
  private fun ReportMapDrawn(mapInstance: MapInstance) {
    val drawn by mapInstance.firstFrameRendered.collectAsStateWithLifecycle()
    LaunchedEffect(drawn) {
      if (drawn) reportFullyDrawn()
    }
  }
}
