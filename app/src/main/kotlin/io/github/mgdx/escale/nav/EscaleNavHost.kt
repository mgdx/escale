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
import io.github.mgdx.escale.ui.detail.DetailRoute
import io.github.mgdx.escale.ui.detail.DetailScreen
import io.github.mgdx.escale.ui.home.HomeRoute
import io.github.mgdx.escale.ui.home.HomeScreen
import io.github.mgdx.escale.ui.results.ResultsSheetSlot
import io.github.mgdx.escale.ui.search.SearchCardSlot
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
      // Les deux emplacements de l'écran d'accueil (docs/architecture.md § 11.4). Ils sont branchés
      // ici, une fois pour toutes : le lot « recherche » et le lot « résultats » remplissent chacun
      // le corps de son propre fichier et n'ont jamais à revenir dans ce fichier ni dans
      // `HomeScreen`. C'est ce qui leur évite de se marcher dessus.
      HomeScreen(
        onOpenSettings = { navController.navigate(SettingsRoute) },
        searchCard = { padding -> SearchCardSlot(padding = padding) },
        resultsSheet = { padding ->
          // La feuille demande l'ouverture du détail par un événement à consommation unique, et
          // non par l'observation du trajet mis en évidence : celui-ci dure, et doit durer, pour
          // que la carte continue de le tracer au retour en arrière (SPEC.md § 5.1).
          ResultsSheetSlot(
            padding = padding,
            // `launchSingleTop` : deux appuis très rapprochés ouvrent un seul écran de détail,
            // jamais deux exemplaires empilés l'un sur l'autre.
            onOpenJourney = { navController.navigate(DetailRoute) { launchSingleTop = true } },
          )
        },
      )
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
    composable<DetailRoute> {
      // Le retour ne touche pas au trajet mis en évidence : il reste tracé et cadré sur la carte,
      // et la carte de résultat correspondante reste distinguée dans la liste (SPEC.md § 5.1).
      DetailScreen(onBack = navController::popBackStack)
    }
  }
}
