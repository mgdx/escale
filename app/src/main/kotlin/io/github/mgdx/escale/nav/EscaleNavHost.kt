package io.github.mgdx.escale.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.about.AboutRoute
import io.github.mgdx.escale.ui.about.AboutScreen
import io.github.mgdx.escale.ui.about.LicenseRoute
import io.github.mgdx.escale.ui.about.LicenseScreen
import io.github.mgdx.escale.ui.departures.DeparturesRoute
import io.github.mgdx.escale.ui.departures.DeparturesScreen
import io.github.mgdx.escale.ui.detail.DetailRoute
import io.github.mgdx.escale.ui.detail.DetailScreen
import io.github.mgdx.escale.ui.favorites.FavoritesRoute
import io.github.mgdx.escale.ui.favorites.FavoritesScreen
import io.github.mgdx.escale.ui.home.HomeRoute
import io.github.mgdx.escale.ui.home.HomeScreen
import io.github.mgdx.escale.ui.results.ResultsSheetSlot
import io.github.mgdx.escale.ui.search.SearchCardSlot
import io.github.mgdx.escale.ui.server.ServerSettingsRoute
import io.github.mgdx.escale.ui.server.ServerSettingsScreen
import io.github.mgdx.escale.ui.settings.CategoryOrderRoute
import io.github.mgdx.escale.ui.settings.CategoryOrderScreen
import io.github.mgdx.escale.ui.settings.SettingsRoute
import io.github.mgdx.escale.ui.settings.SettingsScreen
import io.github.mgdx.escale.ui.trip.TripRoute
import io.github.mgdx.escale.ui.trip.TripScreen

/**
 * Le graphe de navigation, en routes typées (docs/architecture.md § 3, règle 4).
 *
 * C'est le seul fichier partagé entre les lots d'interface : un lot n'y ajoute que le
 * `composable<XRoute> { }` de son écran, jamais une réorganisation.
 */
@Composable
fun EscaleNavHost(navController: NavHostController = rememberNavController()) {
  PendingRequestNavigation(navController)
  NavHost(navController = navController, startDestination = HomeRoute) {
    composable<HomeRoute> {
      // Les deux emplacements de l'écran d'accueil (docs/architecture.md § 11.4). Ils sont branchés
      // ici, une fois pour toutes : le lot « recherche » et le lot « résultats » remplissent chacun
      // le corps de son propre fichier et n'ont jamais à revenir dans ce fichier ni dans
      // `HomeScreen`. C'est ce qui leur évite de se marcher dessus.
      HomeScreen(
        onOpenSettings = { navController.navigate(SettingsRoute) },
        searchCard = { padding ->
          // L'appui long sur une puce Domicile ou Travail propose de la modifier : le choix d'un
          // lieu vit sur l'écran des favoris, et c'est ici que les deux lots se rejoignent
          // (SPEC.md § 5.5).
          SearchCardSlot(padding = padding, onOpenFavorites = { navController.navigate(FavoritesRoute) })
        },
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
    composable<SettingsRoute> { SettingsDestination(navController) }
    composable<CategoryOrderRoute> { CategoryOrderScreen(onBack = navController::popBackStack) }
    composable<FavoritesRoute> { FavoritesDestination(navController) }
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
      DetailScreen(
        onBack = navController::popBackStack,
        // Le point d'accroche laissé par le lot « détail » : une portion en transport en commun
        // mène à la desserte complète de sa course (SPEC.md § 5.3). Il est branché ici, et non
        // dans `ui/detail`, pour que les deux lots n'aient pas à se connaître.
        onTripSelected = { tripId ->
          navController.navigate(TripRoute(tripId)) { launchSingleTop = true }
        },
      )
    }
    composable<DeparturesRoute> {
      DeparturesScreen(
        onBack = navController::popBackStack,
        onOpenTrip = { tripId ->
          navController.navigate(TripRoute(tripId)) { launchSingleTop = true }
        },
      )
    }
    composable<TripRoute> {
      TripScreen(onBack = navController::popBackStack)
    }
  }
}

/**
 * L'écran de réglages et les quatre écrans qu'il ouvre (SPEC.md § 5.6).
 *
 * Sorti du graphe pour la même raison que [FavoritesDestination] : cinq destinations empilées dans
 * `EscaleNavHost` en feraient une fonction que plus personne ne lit d'un coup d'œil.
 */
@Composable
private fun SettingsDestination(navController: NavHostController) {
  SettingsScreen(
    onBack = navController::popBackStack,
    onOpenServerSettings = { navController.navigate(ServerSettingsRoute) },
    onOpenFavorites = { navController.navigate(FavoritesRoute) },
    onOpenAbout = { navController.navigate(AboutRoute) },
    onOpenCategoryOrder = { navController.navigate(CategoryOrderRoute) },
  )
}

/**
 * L'écran des favoris et de l'historique (SPEC.md § 5.5), et ses deux issues.
 *
 * Il est écrit ici, hors de [EscaleNavHost], pour ne pas allonger la seule fonction que tous les
 * lots se partagent (docs/architecture.md § 3, règle 4).
 */
@Composable
private fun FavoritesDestination(navController: NavHostController) {
  FavoritesScreen(
    onBack = navController::popBackStack,
    // Un favori rejoué remplit la recherche partagée ; il ne reste qu'à revenir à la carte, où la
    // feuille de résultats a déjà commencé à chercher (SPEC.md § 5.1). `inclusive = false` : c'est
    // l'accueil qu'on retrouve, et tout ce qui a été empilé par-dessus qui s'en va — l'écran des
    // réglages compris, d'où l'usager a pu venir.
    onSearchStarted = { navController.popBackStack(HomeRoute, inclusive = false) },
    onOpenStop = { stopId, stopName ->
      // `launchSingleTop` : deux appuis très rapprochés ouvrent un seul écran de départs.
      navController.navigate(DeparturesRoute(stopId = stopId, stopName = stopName)) {
        launchSingleTop = true
      }
    },
  )
}

/**
 * Les demandes déposées **hors de la navigation**, consommées ici (docs/architecture.md § 11.4).
 *
 * La carte y dépose ce qu'elle ne sait pas ouvrir elle-même : l'arrêt dont l'usager veut les
 * départs. Passer par ce point unique garde `EscaleNavHost` à la taille d'une table des matières,
 * comme le fait déjà `FavoritesDestination` pour son écran.
 */
@Composable
private fun PendingRequestNavigation(navController: NavHostController) {
  StopDepartureNavigation(navController)
}

/**
 * L'infobulle d'un arrêt de la carte mène aux prochains départs (SPEC.md § 5.7 et § 5.4).
 *
 * Le lot « carte » n'a pas à connaître cet écran : il dépose sa demande dans
 * `AppContainer.stopDepartureRequests`, et c'est la navigation qui la consomme — même dispositif
 * que `MapSelection` pour l'écran de recherche (docs/architecture.md § 11.4). L'infobulle n'a donc
 * pas changé d'une ligne.
 *
 * La demande porte un jeton qui change à chaque appui : sans lui, redemander deux fois le même
 * arrêt ne déclencherait rien la seconde fois. `consume()` l'acquitte aussitôt, pour qu'un retour
 * en arrière ne rouvre pas l'écran tout seul.
 */
@Composable
private fun StopDepartureNavigation(navController: NavHostController) {
  val requests = appContainer().stopDepartureRequests
  val request by requests.request.collectAsStateWithLifecycle()
  LaunchedEffect(request?.token) {
    val pending = request ?: return@LaunchedEffect
    // `launchSingleTop` : deux appuis très rapprochés ouvrent un seul écran, jamais deux
    // exemplaires empilés l'un sur l'autre.
    navController.navigate(DeparturesRoute(stopId = pending.stopId, stopName = pending.stopName)) {
      launchSingleTop = true
    }
    requests.consume()
  }
}
