package io.github.mgdx.escale.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
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
  OpenSelectedJourney(navController)
  NavHost(navController = navController, startDestination = HomeRoute) {
    composable<HomeRoute> {
      // Les deux emplacements de l'écran d'accueil (docs/architecture.md § 11.4). Ils sont branchés
      // ici, une fois pour toutes : le lot « recherche » et le lot « résultats » remplissent chacun
      // le corps de son propre fichier et n'ont jamais à revenir dans ce fichier ni dans
      // `HomeScreen`. C'est ce qui leur évite de se marcher dessus.
      HomeScreen(
        onOpenSettings = { navController.navigate(SettingsRoute) },
        searchCard = { padding -> SearchCardSlot(padding = padding) },
        resultsSheet = { padding -> ResultsSheetSlot(padding = padding) },
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
      DetailScreen(
        onBack = {
          // Quitter le détail, c'est ne plus avoir de trajet choisi. Sans cette remise à zéro,
          // `SelectedJourneyStore` garderait la même valeur et un second appui sur la même carte
          // de résultat n'émettrait rien : la StateFlow ne republie pas une valeur égale.
          SelectedJourneyStore.shared.select(null)
          navController.popBackStack()
        },
      )
    }
  }
}

/**
 * Ouvre l'écran de détail dès qu'un trajet est choisi dans la feuille de résultats (SPEC.md § 5.3).
 *
 * Le geste appartient au lot « résultats », qui publie le trajet dans `SelectedJourneyStore` sans
 * rien connaître de la navigation ; le branchement se fait donc ici, dans le seul fichier partagé
 * (docs/architecture.md § 3, règle 4). Le repère de passage est mémorisé par `rememberSaveable`
 * pour qu'une rotation ne réempile pas une seconde fois le même écran.
 */
@Composable
private fun OpenSelectedJourney(navController: NavHostController) {
  val selected by SelectedJourneyStore.shared.selected.collectAsStateWithLifecycle()
  var opened by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(selected) {
    when {
      selected == null -> opened = false

      !opened -> {
        opened = true
        navController.navigate(DetailRoute)
      }
    }
  }
}
