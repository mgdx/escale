package io.github.mgdx.escale.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mgdx.escale.ui.about.AboutRoute
import io.github.mgdx.escale.ui.about.AboutScreen
import io.github.mgdx.escale.ui.about.LicenseRoute
import io.github.mgdx.escale.ui.about.LicenseScreen
import io.github.mgdx.escale.ui.home.HomeRoute
import io.github.mgdx.escale.ui.home.HomeScreen
import io.github.mgdx.escale.ui.server.ServerSettingsRoute
import io.github.mgdx.escale.ui.server.ServerSettingsScreen
import io.github.mgdx.escale.ui.settings.SettingsRoute
import io.github.mgdx.escale.ui.settings.SettingsScreen

/**
 * Le graphe de navigation, en routes typées (docs/architecture.md § 3, règle 4).
 *
 * C'est le seul fichier partagé entre les lots d'interface : un lot n'y ajoute que le
 * `composable<XRoute> { }` de son écran, jamais une réorganisation.
 */
@Composable
fun EscaleNavHost(navController: NavHostController = rememberNavController()) {
  NavHost(navController = navController, startDestination = HomeRoute) {
    composable<HomeRoute> {
      HomeScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
    }
    composable<SettingsRoute> {
      SettingsScreen(
        onBack = navController::popBackStack,
        onOpenServerSettings = { navController.navigate(ServerSettingsRoute) },
        onOpenAbout = { navController.navigate(AboutRoute) },
      )
    }
    composable<ServerSettingsRoute> {
      ServerSettingsScreen(onBack = navController::popBackStack)
    }
    composable<AboutRoute> {
      AboutScreen(
        onBack = navController::popBackStack,
        onOpenLicense = { navController.navigate(LicenseRoute) },
      )
    }
    composable<LicenseRoute> {
      LicenseScreen(onBack = navController::popBackStack)
    }
  }
}
