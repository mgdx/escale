package io.github.mgdx.escale.ui.results

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.ui.common.ErrorMessage

/**
 * Emplacement de la **feuille de résultats** (SPEC.md § 5.2), branché dans le `NavHost` au point
 * d'appel de `HomeScreen` (docs/architecture.md § 11.4).
 *
 * La feuille lit la recherche en cours dans `SearchSession`, exposée par `AppContainer` : elle n'a
 * rien à demander au lot « recherche », et réciproquement. Tant que le brouillon est incomplet,
 * elle ne dessine rien du tout — la carte reste plein écran, et **aucune requête ne part**.
 *
 * `HomeScreen` mesure la hauteur occupée par cet emplacement et la reporte sur le `padding` de la
 * caméra : la feuille n'a donc pas à publier sa hauteur.
 *
 * @param padding les encarts système transmis par `HomeScreen`.
 * @param onOpenJourney ouverture de l'écran de détail (SPEC.md § 5.3), branchée par le graphe de
 *   navigation. Elle est appelée sur un **événement** de [ResultsViewModel.openDetail], et non sur
 *   l'observation du trajet mis en évidence : celui-ci reste publié tant qu'il y a des résultats,
 *   pour que la carte continue de le tracer et de le cadrer (SPEC.md § 5.1).
 */
@Composable
fun ResultsSheetSlot(padding: PaddingValues, onOpenJourney: () -> Unit, modifier: Modifier = Modifier) {
  val container = appContainer()
  val viewModel: ResultsViewModel = viewModel(factory = ResultsViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // La demande d'ouverture se consomme une fois : le canal est vidé au fil de l'eau, et le
  // rappel le plus récent est celui qui sert, sans jamais relancer la collecte.
  val open by rememberUpdatedState(onOpenJourney)
  LaunchedEffect(viewModel) {
    viewModel.openDetail.collect { open() }
  }
  // SPEC.md § 7.4 : au retour au premier plan, et seulement si les horaires ont plus de 60
  // secondes. Aucune minuterie, aucune boucle — c'est le système qui prévient.
  ForegroundEffect(viewModel::onForeground)
  if (!state.open) return
  ResultsSheet(
    state = state,
    actions = rememberResultsActions(viewModel),
    padding = padding,
    modifier = modifier,
  )
}

/** Les gestes de la feuille, regroupés pour ne pas les recréer à chaque recomposition. */
@Composable
private fun rememberResultsActions(viewModel: ResultsViewModel): ResultsActions = remember(viewModel) {
  ResultsActions(
    onCategorySelected = viewModel::onCategorySelected,
    onRetry = viewModel::onRetry,
    onEarlier = viewModel::onEarlier,
    onLater = viewModel::onLater,
    onJourneySelected = viewModel::onJourneySelected,
    onBikeFilterChanged = viewModel::onBikeFilterChanged,
    onRefresh = viewModel::onPullToRefresh,
  )
}

internal data class ResultsActions(
  val onCategorySelected: (JourneyCategory) -> Unit,
  val onRetry: () -> Unit,
  val onEarlier: () -> Unit,
  val onLater: () -> Unit,
  val onJourneySelected: (Journey) -> Unit,
  val onBikeFilterChanged: (BikeFilter) -> Unit,
  /** « Tirer pour rafraîchir » : le geste de SPEC.md § 7.4. */
  val onRefresh: () -> Unit,
)

/**
 * La feuille inférieure, **au-dessus de la carte, qui reste visible en haut** (SPEC.md § 5.1).
 *
 * Ce n'est pas un `BottomSheetScaffold` : celui-ci occuperait l'écran entier et couvrirait la carte,
 * alors que la composition de docs/architecture.md § 11.4 place cet emplacement dans l'écran
 * d'accueil, qui appartient au lot « carte ». La feuille se dimensionne donc elle-même, entre deux
 * hauteurs, et `HomeScreen` mesure ce qu'elle occupe.
 */
@Composable
internal fun ResultsSheet(
  state: ResultsUiState,
  actions: ResultsActions,
  padding: PaddingValues,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val target = maxHeight * if (expanded) EXPANDED_FRACTION else PEEK_FRACTION
    val height by animateDpAsState(targetValue = target, label = "hauteur de la feuille de résultats")
    val description = stringResource(R.string.results_sheet_description)
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(height)
        // La feuille est posée **sur** la carte : sans cet absorbeur, un appui dans une zone vide de
        // la feuille traverserait jusqu'à la carte, qui y ouvrirait son menu d'appui long. Les
        // commandes de la feuille sont touchées avant lui et consomment leurs propres gestes.
        .pointerInput(Unit) { detectTapGestures(onLongPress = {}, onTap = {}) }
        .semantics { contentDescription = description },
      shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = SheetElevation,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        SheetHandle(expanded = expanded, onToggle = { expanded = !expanded })
        ResultsTabs(selected = state.category, onSelected = actions.onCategorySelected)
        ResultsContent(state = state, actions = actions, padding = padding)
      }
    }
  }
}

/**
 * La poignée de la feuille : un appui l'agrandit ou la réduit, un glissement fait de même.
 *
 * Toute la bande fait 48 dp de haut, la cible tactile minimale de SPEC.md § 9, et porte le libellé
 * de l'action qu'elle déclenche.
 */
@Composable
private fun SheetHandle(expanded: Boolean, onToggle: () -> Unit) {
  val label = stringResource(
    if (expanded) R.string.results_sheet_collapse else R.string.results_sheet_expand,
  )
  var travel by remember { mutableFloatStateOf(0f) }
  val threshold = with(LocalDensity.current) { DragThreshold.toPx() }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(HandleTouchTarget)
      .clickable(onClickLabel = label, onClick = onToggle)
      .draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta -> travel += delta },
        onDragStopped = {
          // Vers le haut, on agrandit ; vers le bas, on réduit. En deçà du seuil, rien ne bouge :
          // un frôlement ne doit pas faire sauter la feuille.
          if (travel < -threshold && !expanded) onToggle()
          if (travel > threshold && expanded) onToggle()
          travel = 0f
        },
      ),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(width = HandleWidth, height = HandleHeight)
        .clip(RoundedCornerShape(HandleHeight))
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
  }
}

/**
 * Les quatre onglets, dans l'ordre de SPEC.md § 5.2.
 *
 * Ils défilent horizontalement : « Transport en commun » ne tient pas sur un quart d'écran, et
 * SPEC.md § 9 interdit de le tronquer à 200 % d'agrandissement. Chaque onglet porte son
 * pictogramme **et** son libellé.
 */
@Composable
private fun ResultsTabs(selected: JourneyCategory, onSelected: (JourneyCategory) -> Unit) {
  val categories = JourneyCategory.entries
  PrimaryScrollableTabRow(
    selectedTabIndex = categories.indexOf(selected),
    modifier = Modifier.fillMaxWidth(),
    edgePadding = TabEdgePadding,
  ) {
    categories.forEach { category ->
      Tab(
        selected = category == selected,
        onClick = { onSelected(category) },
        text = { Text(text = stringResource(category.labelRes())) },
        icon = {
          Icon(painter = painterResource(category.iconRes()), contentDescription = null)
        },
      )
    }
  }
}

/**
 * Les quatre états de l'onglet consulté : chargement, erreur, vide, liste (SPEC.md § 8).
 *
 * L'ordre des cas n'est pas indifférent : une erreur l'emporte sur une liste périmée, et un onglet
 * qui n'a rien demandé n'affiche ni liste vide ni erreur, mais l'attente.
 *
 * Le contenu prend **la place qui reste** sous les onglets, et pas un pixel de plus : sans ce
 * `weight`, une liste haute déborderait sous le bord de la feuille et ses dernières cartes
 * deviendraient inatteignables.
 */
@Composable
private fun ColumnScope.ResultsContent(state: ResultsUiState, actions: ResultsActions, padding: PaddingValues) {
  val tab = state.current
  val fill = Modifier
    .weight(1f)
    .fillMaxWidth()
  when {
    tab.error != null -> CenteredState(fill, padding) {
      ErrorMessage(error = tab.error, onRetry = actions.onRetry)
    }

    tab.loading || tab.feed == null -> CenteredState(fill, padding) { ResultsLoading() }

    tab.isEmpty -> CenteredState(fill, padding) { ResultsEmpty(category = state.category) }

    else -> JourneyList(state = state, actions = actions, padding = padding, modifier = fill)
  }
}

/**
 * Un état court, centré dans la place disponible et **défilable** : à 200 % d'agrandissement, un
 * état vide et ses suggestions dépassent la hauteur de la feuille repliée, et rien ne doit devenir
 * illisible pour autant (SPEC.md § 9).
 */
@Composable
private fun CenteredState(modifier: Modifier, padding: PaddingValues, content: @Composable () -> Unit) {
  Box(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(bottom = padding.calculateBottomPadding()),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/**
 * La liste des trajets, encadrée par « Plus tôt » et « Plus tard » (SPEC.md § 5.2), et **tirable
 * pour rafraîchir le temps réel** (SPEC.md § 7.4).
 *
 * Le geste est le seul déclencheur volontaire de rafraîchissement : il n'y a ni minuterie ni
 * rechargement périodique derrière cette liste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyList(
  state: ResultsUiState,
  actions: ResultsActions,
  padding: PaddingValues,
  modifier: Modifier = Modifier,
) {
  val tab = state.current
  val journeys = state.visibleJourneys
  PullToRefreshBox(
    isRefreshing = tab.refreshing,
    onRefresh = actions.onRefresh,
    modifier = modifier,
  ) {
    JourneyColumn(state = state, actions = actions, padding = padding, journeys = journeys, tab = tab)
  }
}

@Composable
private fun JourneyColumn(
  state: ResultsUiState,
  actions: ResultsActions,
  padding: PaddingValues,
  journeys: List<Journey>,
  tab: TabResults,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(
      start = ContentPadding,
      end = ContentPadding,
      top = ContentPadding,
      // La barre de navigation du système est sous la feuille : la liste doit pouvoir défiler
      // au-delà, sans quoi la dernière carte reste inatteignable.
      bottom = ContentPadding + padding.calculateBottomPadding(),
    ),
    verticalArrangement = Arrangement.spacedBy(ListSpacing),
  ) {
    if (state.bikeFilterVisible) {
      item(key = FILTER_KEY) {
        BikeFilterRow(selected = state.bikeFilter, onSelected = actions.onBikeFilterChanged)
      }
    }
    if (tab.feed?.previousPageCursor != null) {
      item(key = EARLIER_KEY) {
        PageButton(
          page = ResultsPage.EARLIER,
          loading = tab.paging == ResultsPage.EARLIER,
          onClick = actions.onEarlier,
        )
      }
    }
    items(items = journeys, key = { it.stableKey() }) { journey ->
      JourneyCard(
        journey = journey,
        isSelected = journey.stableKey() == state.selectedKey,
        // Les perturbations « en vigueur » se jugent à l'heure du chargement des horaires
        // affichés, et non à la seconde près : sans quoi la liste se recomposerait sans fin.
        at = tab.loadedAt,
        onSelect = { actions.onJourneySelected(journey) },
      )
    }
    if (tab.feed?.nextPageCursor != null) {
      item(key = LATER_KEY) {
        PageButton(
          page = ResultsPage.LATER,
          loading = tab.paging == ResultsPage.LATER,
          onClick = actions.onLater,
        )
      }
    }
  }
}

/** Hauteur de la feuille repliée : elle laisse la carte visible en haut (SPEC.md § 5.1). */
private const val PEEK_FRACTION = 0.45f

/** Hauteur de la feuille déployée : la carte reste visible, même réduite à une bande. */
private const val EXPANDED_FRACTION = 0.9f

private const val FILTER_KEY = "filtre"
private const val EARLIER_KEY = "plus-tot"
private const val LATER_KEY = "plus-tard"

private val SheetCorner: Dp = 16.dp
private val SheetElevation: Dp = 8.dp
private val HandleTouchTarget: Dp = 48.dp
private val HandleWidth: Dp = 32.dp
private val HandleHeight: Dp = 4.dp
private val TabEdgePadding: Dp = 8.dp
private val ContentPadding: Dp = 16.dp
private val ListSpacing: Dp = 8.dp
private val DragThreshold: Dp = 24.dp
