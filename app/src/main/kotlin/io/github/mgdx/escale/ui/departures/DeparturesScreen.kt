package io.github.mgdx.escale.ui.departures

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.stableKey
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.results.DisruptionBanner
import io.github.mgdx.escale.ui.results.ForegroundEffect

/**
 * Les prochains départs à un arrêt (SPEC.md § 5.4).
 *
 * L'écran s'ouvre depuis trois endroits : l'infobulle d'un arrêt de la carte (§ 5.7), le choix d'un
 * arrêt dans la recherche, et un favori (jalon 10). Il ne connaît aucun des trois : la navigation
 * lui remet un identifiant d'arrêt, et c'est tout.
 *
 * @param onOpenTrip ouvre la desserte complète d'une course (§ 5.3).
 */
@Composable
fun DeparturesScreen(onBack: () -> Unit, onOpenTrip: (tripId: String) -> Unit, modifier: Modifier = Modifier) {
  val container = appContainer()
  val viewModel: DeparturesViewModel = viewModel(factory = DeparturesViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // SPEC.md § 7.4 : au retour au premier plan, et seulement si les horaires affichés ont plus de
  // 60 secondes. Le bouton « Actualiser » de la barre, lui, rafraîchit sans condition.
  ForegroundEffect(viewModel::onForeground)
  DeparturesContent(
    state = state,
    onBack = onBack,
    onRefresh = viewModel::onRefresh,
    onFilterSelected = viewModel::onFilterSelected,
    onPage = viewModel::onPage,
    onOpenTrip = onOpenTrip,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeparturesContent(
  state: DeparturesUiState,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onFilterSelected: (DepartureModeFilter?) -> Unit,
  onPage: (DeparturesPage) -> Unit,
  onOpenTrip: (tripId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { DeparturesTitle(state) },
        // La barre grandit avec les polices. Sa hauteur est figée à 64 dp par Material, ce qui
        // coupe le nom de l'arrêt dès 150 % : c'est le seul endroit qui dit où l'on est
        // (SPEC.md § 9).
        expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight * titleScale(),
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        actions = {
          IconButton(onClick = onRefresh) {
            Icon(
              painter = painterResource(R.drawable.ic_refresh),
              contentDescription = stringResource(R.string.departures_action_refresh),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      // Le rafraîchissement laisse la liste en place : SPEC.md § 8 interdit de remplacer des
      // horaires déjà lus par une page blanche.
      if (state.refreshing || state.paging != null) {
        val loading = stringResource(R.string.action_loading)
        LinearProgressIndicator(
          // Sans nom, la barre n'est qu'une animation : le lecteur d'écran passe devant sans rien
          // dire, alors que la liste change sous le doigt (SPEC.md § 9).
          modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading },
        )
      }
      ModeFilters(state = state, onFilterSelected = onFilterSelected)
      state.error?.let { ErrorMessage(error = it, onRetry = onRefresh) }
      DeparturesBody(state = state, onPage = onPage, onOpenTrip = onOpenTrip)
    }
  }
}

/**
 * Le nom de l'arrêt, et le nombre de lignes qui le desservent quand il est connu.
 *
 * Le nom gagne une ligne quand les polices sont agrandies, et la barre gagne la hauteur
 * correspondante : à 200 %, deux lignes tronquées valaient mieux qu'une, trois valent mieux que
 * deux (SPEC.md § 9). La coupure reste le dernier recours d'un nom démesuré, jamais le cas normal.
 */
@Composable
private fun DeparturesTitle(state: DeparturesUiState) {
  val name = state.stopName.ifBlank { stringResource(R.string.departures_stop_unnamed) }
  val enlarged = titleScale() > 1f
  Column {
    Text(
      text = name,
      maxLines = if (enlarged) TITLE_LINES_ENLARGED else TITLE_LINES,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleLarge,
    )
    if (state.lines.isNotEmpty()) {
      Text(
        text = linesSummary(state.lines),
        style = MaterialTheme.typography.bodySmall,
        maxLines = if (enlarged) TITLE_LINES else 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * L'agrandissement des polices, plafonné à 200 % — le maximum que SPEC.md § 9 demande de tenir.
 *
 * Au-delà, la barre cesserait de grandir plutôt que de manger l'écran : la liste des départs, elle,
 * a plus besoin de place que le titre.
 */
@Composable
private fun titleScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE)

/**
 * « 30 lignes · 1, 4, 7, 11, 14 » : le compte d'abord, puis autant de numéros que la place permet.
 *
 * Le compte est un pluriel de la langue, pas une concaténation : SPEC.md § 2 l'impose, et « 1
 * lignes » se remarque tout de suite.
 */
@Composable
private fun linesSummary(lines: List<StopLine>): String {
  val count = pluralStringResource(R.plurals.departures_lines_served, lines.size, lines.size)
  val labels = lines.mapNotNull { it.label.takeIf(String::isNotBlank) }
  if (labels.isEmpty()) return count
  return stringResource(R.string.departures_lines_summary, count, labels.joinToString(LINE_SEPARATOR))
}

/**
 * Les puces de filtre par mode (SPEC.md § 5.4).
 *
 * Elles n'apparaissent que si l'arrêt est desservi par plus d'un mode : c'est `DepartureFilters`,
 * dans `:core`, qui en décide, et il rend une liste vide dans le cas contraire.
 *
 * **Ce que ces puces envoient au serveur, ce sont des feuilles.** Une puce « train » recouvre les
 * cinq modes ferrés de l'API, jamais le parapluie `RAIL` — qui embarquerait le métro avec, alors
 * qu'il a sa propre puce (docs/architecture.md § 11.5).
 */
@Composable
private fun ModeFilters(state: DeparturesUiState, onFilterSelected: (DepartureModeFilter?) -> Unit) {
  if (state.filters.isEmpty()) return
  val label = stringResource(R.string.departures_filter_label)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = ScreenPadding, vertical = ChipSpacing)
      .semantics { contentDescription = label },
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    ModeChip(
      label = stringResource(R.string.departures_filter_all),
      icon = null,
      selected = state.filter == null,
      onClick = { onFilterSelected(null) },
    )
    state.filters.forEach { filter ->
      ModeChip(
        label = stringResource(filter.labelRes()),
        icon = filter.iconRes(),
        selected = state.filter == filter,
        onClick = { onFilterSelected(filter) },
      )
    }
  }
}

@Composable
private fun ModeChip(label: String, icon: Int?, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text = label) },
    // Le pictogramme double le libellé, il ne le remplace pas : le mot reste lisible et énoncé
    // (SPEC.md § 9).
    leadingIcon = icon?.let {
      {
        Icon(
          painter = painterResource(it),
          contentDescription = null,
          modifier = Modifier.size(ChipIconSize),
        )
      }
    },
    // La coche que Material pose d'ordinaire à l'état sélectionné a cédé sa place au pictogramme
    // de mode : sans elle, la puce active ne se distinguait plus que par sa couleur de fond, ce
    // que SPEC.md § 9 interdit. Elle revient à droite, où elle ne prend la place de rien.
    trailingIcon = if (selected) {
      {
        Icon(
          painter = painterResource(R.drawable.ic_check_circle),
          contentDescription = null,
          modifier = Modifier.size(ChipIconSize),
        )
      }
    } else {
      null
    },
  )
}

/** La liste, l'état de chargement, ou l'état vide — jamais rien du tout (SPEC.md § 8). */
@Composable
private fun DeparturesBody(
  state: DeparturesUiState,
  onPage: (DeparturesPage) -> Unit,
  onOpenTrip: (tripId: String) -> Unit,
) {
  when {
    state.loading -> Loading()
    state.isEmpty -> EmptyState(filtered = state.filter != null)
    else -> DeparturesList(state = state, onPage = onPage, onOpenTrip = onOpenTrip)
  }
}

@Composable
private fun Loading() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

/**
 * L'état vide, avec sa suggestion (SPEC.md § 8 : « état vide illustré, avec suggestions »).
 *
 * Un filtre actif a sa propre phrase : « aucun départ » et « aucun tramway » ne demandent pas la
 * même chose à l'usager.
 */
@Composable
private fun EmptyState(filtered: Boolean) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(ScreenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_schedule),
      contentDescription = null,
      modifier = Modifier.size(EmptyIconSize),
    )
    Text(
      text = stringResource(if (filtered) R.string.departures_empty_filtered else R.string.departures_empty),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = ScreenPadding),
    )
    Text(
      text = stringResource(
        if (filtered) R.string.departures_empty_filtered_hint else R.string.departures_empty_hint,
      ),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun DeparturesList(
  state: DeparturesUiState,
  onPage: (DeparturesPage) -> Unit,
  onOpenTrip: (tripId: String) -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScreenPadding)) {
    // Le bandeau des perturbations **en vigueur** pour l'arrêt (SPEC.md § 5.4). Le tri et le
    // dédoublonnage reviennent à `Disruptions`, dans `:core` ; l'instant de référence est celui du
    // dernier chargement, pour que le bandeau ne change pas au fil des secondes.
    if (state.alerts.isNotEmpty()) {
      item(key = ALERTS_KEY) {
        DisruptionBanner(
          alerts = state.alerts,
          at = state.loadedAt,
          modifier = Modifier.padding(horizontal = ScreenPadding, vertical = ChipSpacing),
        )
      }
    }
    if (state.canPage(DeparturesPage.EARLIER)) {
      item(key = EARLIER_KEY) {
        PageButton(
          label = stringResource(R.string.departures_earlier),
          enabled = state.paging == null,
          onClick = { onPage(DeparturesPage.EARLIER) },
        )
      }
    }
    items(items = state.entries, key = { it.stableKey() }) { entry ->
      DepartureRow(
        entry = entry,
        // Une course sans identifiant ne mène nulle part : la ligne reste, l'appui disparaît.
        onOpen = entry.tripId.takeIf(String::isNotBlank)?.let { id -> { onOpenTrip(id) } },
      )
      HorizontalDivider()
    }
    if (state.canPage(DeparturesPage.LATER)) {
      item(key = LATER_KEY) {
        PageButton(
          label = stringResource(R.string.departures_later),
          enabled = state.paging == null,
          onClick = { onPage(DeparturesPage.LATER) },
        )
      }
    }
  }
}

@Composable
private fun PageButton(label: String, enabled: Boolean, onClick: () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    TextButton(onClick = onClick, enabled = enabled) {
      Text(text = label)
    }
  }
}

private const val LINE_SEPARATOR = " · "
private const val ALERTS_KEY = "departures.alerts"
private const val EARLIER_KEY = "departures.earlier"
private const val LATER_KEY = "departures.later"

private val ScreenPadding: Dp = 16.dp
private val ChipSpacing: Dp = 8.dp
private val ChipIconSize: Dp = 18.dp

/** SPEC.md § 9 demande la lisibilité jusqu'à 200 % : au-delà, la barre cesse de grandir. */
private const val MAX_FONT_SCALE = 2f
private const val TITLE_LINES = 2
private const val TITLE_LINES_ENLARGED = 3
private val EmptyIconSize: Dp = 48.dp
