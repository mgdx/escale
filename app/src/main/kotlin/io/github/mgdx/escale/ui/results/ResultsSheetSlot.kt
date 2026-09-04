package io.github.mgdx.escale.ui.results

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
 * d'accueil, qui appartient au lot « carte ». La feuille se dimensionne donc elle-même, entre les
 * trois positions de `ResultsSheetPosition`, et `HomeScreen` mesure ce qu'elle occupe.
 *
 * Glissée jusqu'en bas, elle ne cache que le détail : les onglets et les durées qu'ils annoncent
 * restent à l'écran, et la carte occupe tout le reste.
 */
@Composable
internal fun ResultsSheet(
  state: ResultsUiState,
  actions: ResultsActions,
  padding: PaddingValues,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // La barre de navigation du système passe sous la feuille : les onglets, qui restent à l'écran
    // même feuille repliée, doivent rester au-dessus d'elle et non sous la barre de gestes. La
    // marge basse des onglets y pourvoit déjà en partie : seul ce que l'encart ajoute au-delà est
    // réservé, sans quoi la feuille repliée traînerait une bande blanche sous les durées.
    val bottomInset = (padding.calculateBottomPadding() - TabVerticalPadding).coerceAtLeast(0.dp)
    val sheet = rememberSheetState(available = maxHeight, bottomInset = bottomInset)
    val description = stringResource(R.string.results_sheet_description)
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .sheetHeight(sheet)
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
        SheetHeader(sheet = sheet, state = state, onSelected = actions.onCategorySelected)
        ResultsContent(state = state, actions = actions, padding = padding)
      }
    }
  }
}

/**
 * Fixe la hauteur de la feuille, **lue au moment de la mesure** et non de la composition.
 *
 * Cette hauteur change à chaque image tant que le doigt glisse. La lire ici plutôt que dans un
 * `Modifier.height` évite de recomposer les onglets et la liste soixante fois par seconde alors
 * que seule la place occupée a changé.
 */
private fun Modifier.sheetHeight(sheet: ResultsSheetState): Modifier = layout { measurable, constraints ->
  val height = sheet.height.roundToPx().coerceIn(0, constraints.maxHeight)
  val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
  layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * L'entête de la feuille : la poignée et les onglets, la partie qui **ne se cache jamais**.
 *
 * Tout l'entête est saisissable, et pas seulement la poignée : la bande des onglets s'attrape et
 * s'appuie aussi bien, ce qui donne une cible large — bien au-delà des 48 dp de SPEC.md § 9. C'est
 * ce qui permet à la bande de la poignée de rester fine sans rien coûter en accessibilité : elle
 * montre où saisir, elle n'est plus la seule à recevoir le doigt.
 *
 * Un appui sur un onglet reste un appui, et le geste vertical ne l'emporte qu'une fois le seuil de
 * glissement franchi.
 *
 * La liste, elle, n'entraîne pas la feuille : tirée vers le bas, elle rafraîchit déjà le temps réel
 * (SPEC.md § 7.4), et les deux gestes se disputeraient le doigt.
 */
@Composable
private fun SheetHeader(sheet: ResultsSheetState, state: ResultsUiState, onSelected: (JourneyCategory) -> Unit) {
  val label = stringResource(
    if (sheet.position == ResultsSheetPosition.EXPANDED) {
      R.string.results_sheet_collapse
    } else {
      R.string.results_sheet_expand
    },
  )
  // Un `onClickLabel` seul ne nomme pas le nœud : le lecteur d'écran annonçait « appuyez deux fois
  // pour agrandir les résultats » sans jamais dire de quoi il s'agissait, ni où en était la
  // feuille. Le nom et l'état viennent donc en plus du libellé d'action (SPEC.md § 9).
  val handle = stringResource(R.string.results_sheet_handle)
  val position = stringResource(sheet.position.stateDescription())
  Column(
    modifier = Modifier
      .onSizeChanged { sheet.onHeaderMeasured(it.height) }
      .semantics {
        contentDescription = handle
        stateDescription = position
      }
      .clickable(onClickLabel = label, onClick = sheet::onHandleClick)
      .draggable(
        orientation = Orientation.Vertical,
        state = sheet.drag,
        onDragStopped = { velocity -> sheet.onDragStopped(velocity) },
      ),
  ) {
    SheetHandle()
    ResultsTabs(state = state, onSelected = onSelected)
  }
}

/** La poignée : le trait qui dit où saisir la feuille. L'appui et le glissement sont sur l'entête. */
@Composable
private fun SheetHandle() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(HandleBand),
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

/** Ce que le lecteur d'écran annonce de la position de la feuille (SPEC.md § 9). */
@StringRes
private fun ResultsSheetPosition.stateDescription(): Int = when (this) {
  ResultsSheetPosition.COLLAPSED -> R.string.results_sheet_state_collapsed
  ResultsSheetPosition.HALF -> R.string.results_sheet_state_half
  ResultsSheetPosition.EXPANDED -> R.string.results_sheet_state_expanded
}

/**
 * L'état de la feuille : sa position d'ancrage, sa hauteur du moment, et le geste qui la déplace.
 *
 * La hauteur est une [Animatable] et non un `animateDpAsState` : elle doit suivre le doigt au
 * pixel près pendant le glissement, puis **rejoindre son ancrage depuis l'endroit où on l'a
 * lâchée**. Une valeur animée vers une cible, elle, repartirait de la dernière position d'ancrage
 * et ferait sauter la feuille au moment du lâcher.
 */
@Stable
private class ResultsSheetState(
  private val scope: CoroutineScope,
  private val anchor: MutableState<ResultsSheetPosition>,
  private val animated: Animatable<Dp, AnimationVector1D>,
  var density: Density,
  var available: Dp,
  var bottomInset: Dp,
) {
  /** Hauteur de l'entête telle qu'elle est mesurée à l'écran : c'est elle qui borne le repli. */
  var headerHeight by mutableStateOf(0.dp)
    private set

  val position: ResultsSheetPosition get() = anchor.value
  val height: Dp get() = animated.value
  val drag = DraggableState { delta -> onDrag(delta) }

  fun onHeaderMeasured(heightPx: Int) {
    headerHeight = with(density) { heightPx.toDp() }
  }

  /** Un appui sur la poignée : la position suivante du cycle, en animation. */
  fun onHandleClick() {
    settleTo(position.next())
  }

  fun onDragStopped(velocityPx: Float) {
    // La vitesse du doigt se compte vers le bas, celle de la hauteur de la feuille vers le haut.
    val velocity = -with(density) { velocityPx.toDp() }.value
    settleTo(settledResultsSheetPosition(animated.value, velocity, headerHeight + bottomInset, available))
  }

  /** Repose la feuille sur son ancrage sans animation : premier affichage, rotation, texte agrandi. */
  suspend fun snapToAnchor() {
    animated.snapTo(heightAt(position))
  }

  /** Le doigt descend, la feuille rapetisse — sans jamais sortir de ses positions extrêmes. */
  private fun onDrag(deltaPx: Float) {
    val delta = with(density) { deltaPx.toDp() }
    val bounded = (animated.value - delta).coerceIn(
      heightAt(ResultsSheetPosition.COLLAPSED),
      heightAt(ResultsSheetPosition.EXPANDED),
    )
    scope.launch { animated.snapTo(bounded) }
  }

  private fun settleTo(target: ResultsSheetPosition) {
    anchor.value = target
    scope.launch { animated.animateTo(heightAt(target)) }
  }

  /** L'entête et l'encart système sous lui : ce que la feuille ne cache jamais. */
  private fun heightAt(at: ResultsSheetPosition): Dp = resultsSheetHeight(at, headerHeight + bottomInset, available)
}

/**
 * L'état de la feuille, conservé d'une composition à l'autre et **rétabli après rotation** : la
 * feuille se retrouve à la position où l'usager l'avait laissée.
 */
@Composable
private fun rememberSheetState(available: Dp, bottomInset: Dp): ResultsSheetState {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val anchor = rememberSaveable { mutableStateOf(ResultsSheetPosition.HALF) }
  val sheet = remember(scope) {
    ResultsSheetState(
      scope = scope,
      anchor = anchor,
      animated = Animatable(resultsSheetHeight(anchor.value, 0.dp, available), Dp.VectorConverter),
      density = density,
      available = available,
      bottomInset = bottomInset,
    )
  }
  sheet.density = density
  sheet.available = available
  sheet.bottomInset = bottomInset
  // La place disponible change à la rotation, la hauteur de l'entête au premier passage et à chaque
  // changement de taille de texte : dans les deux cas, l'ancrage courant a une nouvelle hauteur.
  LaunchedEffect(sheet, available, bottomInset, sheet.headerHeight) { sheet.snapToAnchor() }
  return sheet
}

/**
 * Les quatre onglets, dans l'ordre réglé par l'usager — celui de SPEC.md § 5.2 par défaut.
 *
 * Chaque onglet porte son pictogramme **et la durée du trajet le plus rapide de sa catégorie** ;
 * le libellé n'est plus écrit, le pictogramme suffit à reconnaître le mode. Les quatre tiennent
 * donc côte à côte sur la largeur, sans défilement horizontal ni troncature à 200 %
 * d'agrandissement (SPEC.md § 9). Le libellé reste dit en toutes lettres au lecteur d'écran.
 */
@Composable
private fun ResultsTabs(state: ResultsUiState, onSelected: (JourneyCategory) -> Unit) {
  val categories = state.categoryOrder
  PrimaryTabRow(
    selectedTabIndex = categories.indexOf(state.category),
    modifier = Modifier.fillMaxWidth(),
  ) {
    categories.forEach { category ->
      CategoryTab(
        category = category,
        headline = state.headlineOf(category),
        selected = category == state.category,
        onClick = { onSelected(category) },
      )
    }
  }
}

/**
 * Un onglet : son pictogramme, et la durée annoncée en dessous (SPEC.md § 5.2).
 *
 * Le contenu est posé à la main plutôt que par les emplacements `text` et `icon` de Material :
 * ceux-ci figent la hauteur de l'onglet, et la durée y serait tronquée dès que l'usager agrandit
 * le texte — ce que SPEC.md § 9 interdit. Ici, l'onglet prend la hauteur de ce qu'il contient, et
 * la barre suit.
 *
 * Le libellé de la catégorie n'est pas écrit : le pictogramme le dit assez. Il n'est pas perdu
 * pour autant — le lecteur d'écran entend une annonce unique qui nomme la catégorie **et** ce
 * qu'elle propose (SPEC.md § 9).
 */
@Composable
private fun CategoryTab(category: JourneyCategory, headline: TabHeadline, selected: Boolean, onClick: () -> Unit) {
  val description = tabDescription(category, headline)
  Tab(
    selected = selected,
    onClick = onClick,
    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
  ) {
    Column(
      modifier = Modifier.padding(horizontal = TabHorizontalPadding, vertical = TabVerticalPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(TabSpacing),
    ) {
      Icon(painter = painterResource(category.iconRes()), contentDescription = null)
      Text(
        text = headlineText(headline),
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
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
  // La barre de navigation du système est sous la feuille : le contenu s'arrête au-dessus d'elle,
  // ce qui laisse aussi la place aux onglets quand la feuille est repliée au plus bas.
  val fill = Modifier
    .weight(1f)
    .fillMaxWidth()
    .padding(bottom = padding.calculateBottomPadding())
  when {
    tab.error != null -> CenteredState(fill) {
      ErrorMessage(error = tab.error, onRetry = actions.onRetry)
    }

    tab.loading || tab.feed == null -> CenteredState(fill) { ResultsLoading() }

    tab.isEmpty -> CenteredState(fill) { ResultsEmpty(category = state.category) }

    else -> JourneyList(state = state, actions = actions, modifier = fill)
  }
}

/**
 * Un état court, centré dans la place disponible et **défilable** : à 200 % d'agrandissement, un
 * état vide et ses suggestions dépassent la hauteur de la feuille repliée, et rien ne doit devenir
 * illisible pour autant (SPEC.md § 9).
 */
@Composable
private fun CenteredState(modifier: Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier.verticalScroll(rememberScrollState()),
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
private fun JourneyList(state: ResultsUiState, actions: ResultsActions, modifier: Modifier = Modifier) {
  val tab = state.current
  val journeys = state.visibleJourneys
  PullToRefreshBox(
    isRefreshing = tab.refreshing,
    onRefresh = actions.onRefresh,
    modifier = modifier,
  ) {
    JourneyColumn(state = state, actions = actions, journeys = journeys, tab = tab)
  }
}

@Composable
private fun JourneyColumn(state: ResultsUiState, actions: ResultsActions, journeys: List<Journey>, tab: TabResults) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(ContentPadding),
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

private const val FILTER_KEY = "filtre"
private const val EARLIER_KEY = "plus-tot"
private const val LATER_KEY = "plus-tard"

private val SheetCorner: Dp = 16.dp
private val SheetElevation: Dp = 8.dp

/** La bande de la poignée : le trait et l'air qu'il lui faut, pas une cible tactile à elle seule. */
private val HandleBand: Dp = 24.dp

private val HandleWidth: Dp = 32.dp
private val HandleHeight: Dp = 4.dp
private val TabHorizontalPadding: Dp = 16.dp
private val TabVerticalPadding: Dp = 8.dp
private val TabSpacing: Dp = 4.dp
private val ContentPadding: Dp = 16.dp
private val ListSpacing: Dp = 8.dp
