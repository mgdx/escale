package io.github.mgdx.escale.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.map.AttributionBar
import io.github.mgdx.escale.ui.map.AttributionDialog
import io.github.mgdx.escale.ui.map.LocateButton
import io.github.mgdx.escale.ui.map.LocateState
import io.github.mgdx.escale.ui.map.LocationPermissionDialog
import io.github.mgdx.escale.ui.map.MIN_TOUCH_TARGET
import io.github.mgdx.escale.ui.map.MapCanvas
import io.github.mgdx.escale.ui.map.MapCanvasActions
import io.github.mgdx.escale.ui.map.MapInstance
import io.github.mgdx.escale.ui.map.MapOverlayColors
import io.github.mgdx.escale.ui.map.MapUiState
import io.github.mgdx.escale.ui.map.MapViewModel
import io.github.mgdx.escale.ui.map.TileWarning
import io.github.mgdx.escale.ui.map.manifestPermission

/**
 * L'écran d'accueil : **la carte, en plein écran, du bord haut au bord bas** (SPEC.md § 5.1).
 * Tout le reste flotte par-dessus.
 *
 * Trois lots se superposent ici, et cette composition est ce qui les empêche de se marcher dessus
 * (docs/architecture.md § 11.4) :
 *
 * - le lot **carte** possède cet écran et l'instance MapLibre ;
 * - le lot **recherche** remplira [searchCard] ;
 * - le lot **résultats** remplira [resultsSheet].
 *
 * Les deux emplacements ont une valeur par défaut vide : l'écran compile et s'affiche avant que
 * ces lots existent. Le `PaddingValues` transmis porte les encarts système, et la hauteur de la
 * feuille de résultats est mesurée ici pour devenir le `padding` de la caméra — c'est ce qui
 * permettra à un cadrage de trajet de tenir compte de la feuille ouverte (SPEC.md § 5.7, règle 9).
 *
 * @param onOpenSettings chemin vers les réglages. Tant que la carte de recherche est vide, c'est
 *   le seul accès aux réglages ; le lot « recherche » pourra le reprendre à son compte.
 */
@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  onOpenSettings: () -> Unit = {},
  searchCard: @Composable (PaddingValues) -> Unit = {},
  resultsSheet: @Composable (PaddingValues) -> Unit = {},
) {
  val container = appContainer()
  val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

  val darkTheme = isSystemInDarkTheme()
  LaunchedEffect(darkTheme) { viewModel.onThemeChanged(darkTheme) }

  LocationPermissionEffect(state, viewModel)

  val systemInsets = WindowInsets.safeDrawing.asPaddingValues()
  var sheetHeightPx by remember { mutableIntStateOf(0) }
  val sheetHeight = with(LocalDensity.current) { sheetHeightPx.toDp() }
  val bottomInset = maxOf(systemInsets.calculateBottomPadding(), sheetHeight)
  val actions = remember(viewModel) { viewModel.canvasActions() }

  Box(modifier = modifier.fillMaxSize()) {
    FullScreenMap(
      state = state,
      mapInstance = container.mapInstance,
      actions = actions,
      systemInsets = systemInsets,
      bottomInset = bottomInset,
    )

    // Emplacement du lot « recherche » : il se place lui-même sous la barre d'état.
    searchCard(systemInsets)

    if (state.tilesUnavailable) {
      TileWarning(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = systemInsets.calculateTopPadding() + WarningTopMargin)
          .padding(horizontal = ScreenMargin),
      )
    }

    MapControls(
      locateState = state.locateState,
      onLocateClick = viewModel::onLocateClick,
      onOpenSettings = onOpenSettings,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = ScreenMargin, bottom = bottomInset + ScreenMargin),
    )

    AttributionBar(
      onClick = viewModel::onShowAttribution,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = ScreenMargin, bottom = bottomInset + ScreenMargin),
    )

    // Emplacement du lot « résultats ». Sa hauteur est mesurée ici, et non demandée au lot :
    // c'est ce qui permet de le brancher sans qu'il ait à connaître la carte.
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .onSizeChanged { sheetHeightPx = it.height },
    ) {
      resultsSheet(systemInsets)
    }
  }

  MapDialogs(state = state, viewModel = viewModel, uriHandler = uriHandler, context = context)
}

/**
 * La carte, du bord haut au bord bas (SPEC.md § 5.1).
 *
 * Le `padding` de caméra reprend les encarts système et [bottomInset], qui vaut la hauteur de la
 * feuille de résultats quand elle est ouverte : c'est ce qui permettra à un cadrage de trajet de
 * ne pas se faire manger par la feuille (SPEC.md § 5.7, règle 9).
 */
@Composable
private fun FullScreenMap(
  state: MapUiState,
  mapInstance: MapInstance,
  actions: MapCanvasActions,
  systemInsets: PaddingValues,
  bottomInset: Dp,
) {
  val direction = LocalLayoutDirection.current
  MapCanvas(
    state = state,
    mapInstance = mapInstance,
    actions = actions,
    colors = MapOverlayColors(
      position = MaterialTheme.colorScheme.primary,
      picked = MaterialTheme.colorScheme.tertiary,
      onOverlay = MaterialTheme.colorScheme.surface,
    ),
    contentPadding = PaddingValues(
      start = systemInsets.calculateStartPadding(direction),
      top = systemInsets.calculateTopPadding(),
      end = systemInsets.calculateEndPadding(direction),
      bottom = bottomInset,
    ),
    modifier = Modifier.fillMaxSize(),
  )
}

/**
 * Les commandes flottantes du bas de l'écran (SPEC.md § 5.1).
 *
 * Le bouton de position fait 56 dp et se tient au-dessus de la barre de navigation ; l'accès aux
 * réglages se glisse juste au-dessus, à 48 dp, la cible tactile minimale de SPEC.md § 9.
 */
@Composable
private fun MapControls(
  locateState: LocateState,
  onLocateClick: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(ScreenMargin),
  ) {
    FloatingActionButton(
      onClick = onOpenSettings,
      modifier = Modifier.size(MIN_TOUCH_TARGET),
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_settings),
        contentDescription = stringResource(R.string.action_settings),
      )
    }
    LocateButton(state = locateState, onClick = onLocateClick)
  }
}

/** Les deux fiches modales de la carte : permission refusée, et sources des données. */
@Composable
private fun MapDialogs(state: MapUiState, viewModel: MapViewModel, uriHandler: UriHandler, context: Context) {
  if (state.permissionExplanationVisible) {
    LocationPermissionDialog(
      onOpenSettings = {
        viewModel.onDismissPermissionExplanation()
        context.openApplicationSettings()
      },
      onDismiss = viewModel::onDismissPermissionExplanation,
    )
  }
  if (state.attributionVisible) {
    AttributionDialog(
      onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
      onDismiss = viewModel::onDismissAttribution,
    )
  }
}

/**
 * Demande la permission de localisation **à l'usage**, jamais au démarrage (SPEC.md § 5.1).
 *
 * Le `ViewModel` décide *quand* et *laquelle* ; ce composable ne fait que passer la demande au
 * système, parce que seul un composable peut le faire.
 */
@Composable
private fun LocationPermissionEffect(state: MapUiState, viewModel: MapViewModel) {
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = viewModel::onPermissionResult,
  )
  LaunchedEffect(state.permissionRequest?.token) {
    val request = state.permissionRequest ?: return@LaunchedEffect
    viewModel.onPermissionRequestLaunched()
    launcher.launch(request.permission.manifestPermission())
  }
}

/** Les rappels de la carte, regroupés pour ne pas rebrancher les écouteurs à chaque recomposition. */
private fun MapViewModel.canvasActions() = MapCanvasActions(
  onCameraIdle = ::onCameraIdle,
  onUserMovedCamera = ::onUserMovedCamera,
  onCameraTargetApplied = ::onCameraTargetApplied,
  onLongClick = ::onMapLongClick,
  onLoadFailed = ::onMapLoadFailed,
  onPick = ::onPick,
  onDismissPick = ::onDismissLongPress,
)

/** La fiche de l'application dans les réglages système, d'où la permission peut être rendue. */
private fun Context.openApplicationSettings() {
  val target = Uri.fromParts("package", packageName, null)
  startActivitySafely(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
  )
}

private fun Context.startActivitySafely(intent: Intent) {
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // Un appareil sans écran de réglages n'a pas à faire planter la carte.
  }
}

/** Marges de SPEC.md § 5.1 : 12 dp entre les commandes flottantes et le bord de l'écran. */
private val ScreenMargin: Dp = 12.dp
private val WarningTopMargin: Dp = 8.dp
