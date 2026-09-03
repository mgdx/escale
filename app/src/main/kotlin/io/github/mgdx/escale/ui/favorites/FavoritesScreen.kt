package io.github.mgdx.escale.ui.favorites

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.format.SearchTime
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.results.iconRes
import io.github.mgdx.escale.ui.search.SavedPlaceKind
import io.github.mgdx.escale.ui.search.iconRes
import io.github.mgdx.escale.ui.search.labelRes
import io.github.mgdx.escale.ui.settings.uses24HourClock
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Instant
import java.time.ZoneId

/**
 * L'écran des favoris et de l'historique (SPEC.md § 5.5).
 *
 * Il tient en une seule liste, section par section : domicile et travail, lieux, arrêts, trajets,
 * puis les dernières recherches. Chaque entrée s'ouvre **sur ce qu'elle désigne** — un lieu et un
 * trajet remplissent la recherche, qui part alors d'elle-même (SPEC.md § 5.1), un arrêt ouvre ses
 * prochains départs (SPEC.md § 5.4).
 *
 * @param onSearchStarted appelé après avoir rempli la recherche partagée : l'écran se referme sur
 *   la carte, où la feuille de résultats a déjà commencé à chercher.
 * @param onOpenStop appelé avec l'identifiant et le nom d'un arrêt favori.
 */
@Composable
fun FavoritesScreen(
  onBack: () -> Unit,
  onSearchStarted: () -> Unit,
  onOpenStop: (stopId: String, stopName: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.factory(appContainer())),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val actions = remember(viewModel, onSearchStarted, onOpenStop) {
    viewModel.actions(onSearchStarted = onSearchStarted, onOpenStop = onOpenStop)
  }
  FavoritesContent(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

/** Les gestes de l'écran, regroupés pour ne pas les recréer à chaque recomposition. */
internal data class FavoritesActions(
  val onOpenPicker: (PickerTarget) -> Unit = {},
  val onDismissPicker: () -> Unit = {},
  val onPickerQueryChange: (String) -> Unit = {},
  val onPickerLabelChange: (String) -> Unit = {},
  val onPickerSelected: (Location) -> Unit = {},
  val onRemoveNamed: (SavedPlaceKind) -> Unit = {},
  val onRemovePlace: (FavoritePlace) -> Unit = {},
  val onRemoveStop: (Stop) -> Unit = {},
  val onRemoveJourney: (FavoriteJourney) -> Unit = {},
  val onDeleteSearch: (SearchHistoryEntry) -> Unit = {},
  val onOpenDialog: (FavoritesDialog) -> Unit = {},
  val onDismissDialog: () -> Unit = {},
  val onClearHistory: () -> Unit = {},
  val onSearchPlace: (Location) -> Unit = {},
  val onSearchJourney: (FavoriteJourney) -> Unit = {},
  val onSearchAgain: (SearchHistoryEntry) -> Unit = {},
  val onOpenStop: (String, String) -> Unit = { _, _ -> },
  val onMessageShown: () -> Unit = {},
)

private fun FavoritesViewModel.actions(onSearchStarted: () -> Unit, onOpenStop: (String, String) -> Unit) =
  FavoritesActions(
    onOpenPicker = ::onOpenPicker,
    onDismissPicker = ::onDismissPicker,
    onPickerQueryChange = ::onPickerQueryChange,
    onPickerLabelChange = ::onPickerLabelChange,
    onPickerSelected = ::onPickerSelected,
    onRemoveNamed = ::onRemoveNamed,
    onRemovePlace = ::onRemovePlace,
    onRemoveStop = ::onRemoveStop,
    onRemoveJourney = ::onRemoveJourney,
    onDeleteSearch = ::onDeleteSearch,
    onOpenDialog = ::onOpenDialog,
    onDismissDialog = ::onDismissDialog,
    onClearHistory = ::onClearHistory,
    // Remplir la recherche puis rendre la main à la carte : c'est la feuille de résultats qui lance
    // la requête, dès que départ et arrivée sont connus (SPEC.md § 5.1).
    onSearchPlace = { location ->
      onSearchPlace(location)
      onSearchStarted()
    },
    onSearchJourney = { journey ->
      onSearchJourney(journey)
      onSearchStarted()
    },
    onSearchAgain = { entry ->
      onSearchAgain(entry)
      onSearchStarted()
    },
    onOpenStop = onOpenStop,
    onMessageShown = ::onMessageShown,
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesContent(
  state: FavoritesUiState,
  actions: FavoritesActions,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val messageText = state.message?.let { stringResource(it.textRes()) }
  LaunchedEffect(state.message) {
    if (messageText != null) {
      snackbarHostState.showSnackbar(messageText)
      actions.onMessageShown()
    }
  }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.favorites_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (state.empty) item { EmptyBlock() }
      namedSection(state, actions)
      placesSection(state, actions)
      stopsSection(state, actions)
      journeysSection(state, actions)
      historySection(state, actions)
    }
  }
  state.picker?.let { picker -> PlacePickerDialog(picker = picker, actions = actions) }
  if (state.dialog == FavoritesDialog.ClearHistory) {
    ClearHistoryDialog(onConfirm = actions.onClearHistory, onDismiss = actions.onDismissDialog)
  }
}

/**
 * Domicile et travail (SPEC.md § 5.5).
 *
 * Les deux emplacements sont proposés même vides — mais **seulement ici**, sur un écran que
 * l'usager a ouvert exprès. L'écran d'accueil, lui, n'affiche aucune puce tant qu'ils ne sont pas
 * renseignés, et rien ne les réclame jamais.
 */
private fun LazyListScope.namedSection(state: FavoritesUiState, actions: FavoritesActions) {
  item { SectionHeader(text = stringResource(R.string.favorites_section_named)) }
  item { NamedRow(SavedPlaceKind.HOME, state.home, R.string.favorites_set_home, actions) }
  item { NamedRow(SavedPlaceKind.WORK, state.work, R.string.favorites_set_work, actions) }
}

private fun LazyListScope.placesSection(state: FavoritesUiState, actions: FavoritesActions) {
  item { SectionHeader(text = stringResource(R.string.favorites_section_places)) }
  items(items = state.places, key = { "place-" + it.id }) { place ->
    val name = place.displayName
    FavoriteRow(
      icon = place.location.kind.iconRes(),
      title = name,
      // Le nom du serveur reste visible sous le nom donné par l'usager : « Chez Maman » ne fait
      // pas oublier quelle adresse c'est (SPEC.md § 5.5).
      supporting = place.location.name.takeIf { it != name },
      onClick = { actions.onSearchPlace(place.location) },
      delete = RowAction(stringResource(R.string.favorites_delete_named, name)) { actions.onRemovePlace(place) },
    )
  }
  item {
    AddRow(text = stringResource(R.string.favorites_action_add_place)) {
      actions.onOpenPicker(PickerTarget.Place)
    }
  }
}

private fun LazyListScope.stopsSection(state: FavoritesUiState, actions: FavoritesActions) {
  item { SectionHeader(text = stringResource(R.string.favorites_section_stops)) }
  items(items = state.stops, key = { "stop-" + it.stop.id }) { row ->
    StopRow(row = row, actions = actions)
  }
  item {
    AddRow(text = stringResource(R.string.favorites_action_add_stop)) {
      actions.onOpenPicker(PickerTarget.Stop)
    }
  }
}

/**
 * Les trajets favoris (SPEC.md § 5.5).
 *
 * La section n'apparaît que s'il y en a : un trajet se met en favori depuis son écran de détail,
 * et une section vide n'aurait ici aucun bouton à proposer.
 */
private fun LazyListScope.journeysSection(state: FavoritesUiState, actions: FavoritesActions) {
  if (state.journeys.isEmpty()) return
  item { SectionHeader(text = stringResource(R.string.favorites_section_journeys)) }
  items(items = state.journeys, key = { "journey-" + it.id }) { journey ->
    val summary = stringResource(R.string.favorites_journey_summary, journey.from.name, journey.to.name)
    val title = journey.label?.takeIf { it.isNotBlank() } ?: summary
    FavoriteRow(
      icon = journey.category.iconRes(),
      title = title,
      supporting = summary.takeIf { it != title },
      onClick = { actions.onSearchJourney(journey) },
      delete = RowAction(stringResource(R.string.favorites_delete_named, title)) { actions.onRemoveJourney(journey) },
    )
  }
}

/** Les dernières recherches, horodatées, effaçables une par une ou en bloc (SPEC.md § 5.5). */
private fun LazyListScope.historySection(state: FavoritesUiState, actions: FavoritesActions) {
  item {
    SectionHeader(
      text = stringResource(R.string.favorites_section_history),
      action = if (state.recentSearches.isEmpty()) {
        null
      } else {
        stringResource(R.string.favorites_action_clear_history)
      },
      onAction = { actions.onOpenDialog(FavoritesDialog.ClearHistory) },
    )
  }
  // Le réglage vit dans les réglages, et cet écran ne fait que le rappeler : sans cette phrase,
  // une liste vide passerait pour une panne (SPEC.md § 5.6).
  if (!state.historyEnabled) item { Note(text = stringResource(R.string.favorites_history_disabled)) }
  items(items = state.recentSearches, key = { "search-" + it.id }) { entry ->
    val summary = stringResource(R.string.favorites_journey_summary, entry.from.name, entry.to.name)
    FavoriteRow(
      icon = R.drawable.ic_history,
      title = summary,
      supporting = searchedAtLabel(entry.searchedAt),
      onClick = { actions.onSearchAgain(entry) },
      delete = RowAction(stringResource(R.string.favorites_delete_named, summary)) { actions.onDeleteSearch(entry) },
    )
  }
}

/** Le domicile ou le travail : renseigné, il s'ouvre, se modifie et se supprime ; sinon, il se pose. */
@Composable
private fun NamedRow(kind: SavedPlaceKind, location: Location?, addTextRes: Int, actions: FavoritesActions) {
  val label = stringResource(kind.labelRes())
  if (location == null) {
    AddRow(text = stringResource(addTextRes), icon = kind.iconRes()) {
      actions.onOpenPicker(PickerTarget.Named(kind))
    }
    return
  }
  FavoriteRow(
    icon = kind.iconRes(),
    title = label,
    supporting = location.name,
    onClick = { actions.onSearchPlace(location) },
    delete = RowAction(stringResource(R.string.favorites_delete_named, label)) { actions.onRemoveNamed(kind) },
    edit = RowAction(stringResource(R.string.favorites_edit_named, label)) {
      actions.onOpenPicker(PickerTarget.Named(kind))
    },
  )
}

/**
 * Un arrêt favori, et son signalement éventuel (SPEC.md § 5.6.1).
 *
 * Un identifiant que le serveur courant ne reconnaît plus **ne fait pas disparaître le favori** :
 * la ligne reste, ses coordonnées s'affichent — c'est tout ce qui reste de sûr — et une phrase le
 * dit. Le pictogramme d'alerte double le texte, jamais l'inverse : aucune information n'est portée
 * par la seule couleur (SPEC.md § 9).
 */
@Composable
private fun StopRow(row: FavoriteStopUi, actions: FavoritesActions) {
  val unrecognized = row.recognition == StopRecognition.UNRECOGNIZED
  // Le libellé d'action dit ce que l'appui fait, pas le nom de la section où l'on se trouve : le
  // lecteur d'écran annonçait « appuyez deux fois pour Arrêts » (SPEC.md § 9).
  val openLabel = stringResource(R.string.favorites_open_departures)
  ListItem(
    headlineContent = { Text(text = row.stop.name) },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClickLabel = openLabel, role = Role.Button) {
        actions.onOpenStop(row.stop.id, row.stop.name)
      },
    supportingContent = if (unrecognized) {
      { UnrecognizedStopNote(row) }
    } else {
      null
    },
    leadingContent = {
      Icon(painter = painterResource(R.drawable.ic_trip_origin), contentDescription = null)
    },
    trailingContent = {
      DeleteButton(
        label = stringResource(R.string.favorites_delete_named, row.stop.name),
        onClick = { actions.onRemoveStop(row.stop) },
      )
    },
  )
}

/**
 * Le signalement discret d'un arrêt que le serveur ne reconnaît plus (SPEC.md § 5.6.1).
 *
 * Une phrase, un pictogramme qui la double, et les coordonnées enregistrées — tout ce qui reste de
 * sûr quand l'identifiant ne vaut plus rien. Rien n'est porté par la seule couleur (SPEC.md § 9).
 */
@Composable
private fun UnrecognizedStopNote(row: FavoriteStopUi) {
  Column {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_warning),
        contentDescription = null,
        modifier = Modifier.size(BadgeIconSize),
      )
      Text(text = stringResource(R.string.favorites_stop_unknown), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
      text = stringResource(
        R.string.favorites_stop_coordinates,
        row.stop.coordinates.lat,
        row.stop.coordinates.lon,
      ),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/** Ce qu'une entrée de favori propose en plus de s'ouvrir : un libellé et un geste. */
internal data class RowAction(val label: String, val onClick: () -> Unit)

/** Une entrée de favori : un pictogramme, un titre, une ligne d'appui, et ses commandes. */
@Composable
private fun FavoriteRow(
  @DrawableRes icon: Int,
  title: String,
  supporting: String?,
  onClick: () -> Unit,
  delete: RowAction,
  edit: RowAction? = null,
) {
  ListItem(
    headlineContent = { Text(text = title) },
    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = title, role = Role.Button, onClick = onClick),
    supportingContent = supporting?.let { { Text(text = it, style = MaterialTheme.typography.bodyMedium) } },
    leadingContent = { Icon(painter = painterResource(icon), contentDescription = null) },
    trailingContent = {
      Row {
        edit?.let {
          IconButton(onClick = it.onClick, modifier = Modifier.size(MinTouchTarget)) {
            Icon(painter = painterResource(R.drawable.ic_edit), contentDescription = it.label)
          }
        }
        DeleteButton(label = delete.label, onClick = delete.onClick)
      }
    },
  )
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(MinTouchTarget)) {
    Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = label)
  }
}

/** « Ajouter un lieu », « Définir votre domicile » : une entrée qui ouvre le choix d'un lieu. */
@Composable
private fun AddRow(text: String, @DrawableRes icon: Int = R.drawable.ic_add, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(text = text) },
    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = text, role = Role.Button, onClick = onClick),
    leadingContent = { Icon(painter = painterResource(icon), contentDescription = null) },
  )
}

@Composable
private fun SectionHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
  HorizontalDivider()
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = SectionPadding, end = SectionPadding, top = SectionPadding),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    if (action != null) {
      TextButton(onClick = onAction) { Text(text = action) }
    }
  }
}

@Composable
private fun Note(text: String) {
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth().padding(all = SectionPadding),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Ce que l'écran dit quand il n'y a rien : ce qu'on peut y faire, sans rien réclamer. */
@Composable
private fun EmptyBlock() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(all = SectionPadding),
    verticalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Text(text = stringResource(R.string.favorites_empty_title), style = MaterialTheme.typography.titleMedium)
    Text(
      text = stringResource(R.string.favorites_empty_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.favorites_clear_history_title)) },
    text = { Text(text = stringResource(R.string.favorites_clear_history_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(text = stringResource(R.string.favorites_action_clear_history))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.favorites_action_cancel))
      }
    },
  )
}

/** L'heure d'une recherche passée, dans le fuseau et le format d'horloge de l'usager (§ 5.6). */
@Composable
private fun searchedAtLabel(instant: Instant): String = SearchTime.format(
  instant,
  ZoneId.systemDefault(),
  LocalConfiguration.current.locales[0],
  uses24HourClock(),
)

private val MinTouchTarget: Dp = 48.dp
private val SectionPadding: Dp = 16.dp
private val RowSpacing: Dp = 8.dp
private val BadgeIconSize: Dp = 18.dp

// --- Aperçus -----------------------------------------------------------------------------------
//
// Les noms de lieux ci-dessous sont des **données d'exemple**, pas des chaînes d'interface : toutes
// les chaînes affichées viennent de `strings_favorites.xml`. L'aperçu à 200 % existe parce que
// c'est là que cet écran, dense en listes, casse en premier (SPEC.md § 9).

private val previewHome = Location(
  id = null,
  name = "12 rue des Lilas, Paris",
  description = null,
  coordinates = LatLon(lat = 48.8566, lon = 2.3522),
  kind = PlaceKind.ADDRESS,
)

private val previewStop = Stop(
  id = "de:06:1234",
  name = "Gare de Lyon",
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  modes = listOf(TransitMode.SUBWAY),
)

private val previewState = FavoritesUiState(
  home = previewHome,
  places = listOf(
    FavoritePlace(
      id = 1,
      label = "Chez Maman",
      location = previewHome.copy(name = "8 avenue du Général-Leclerc"),
      createdAt = Instant.parse("2026-03-01T08:10:00Z"),
    ),
  ),
  stops = listOf(
    FavoriteStopUi(previewStop),
    FavoriteStopUi(previewStop.copy(id = "de:06:9999", name = "Châtelet"), StopRecognition.UNRECOGNIZED),
  ),
  recentSearches = listOf(
    SearchHistoryEntry(
      id = 1,
      from = previewHome,
      to = previewHome.copy(name = "Gare de Lyon"),
      time = TimeChoice.Now,
      searchedAt = Instant.parse("2026-03-01T08:10:00Z"),
    ),
  ),
)

@Preview(showBackground = true, name = "Favoris, thème clair")
@Preview(showBackground = true, name = "Favoris, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Favoris, texte à 200 %", fontScale = 2f)
@Composable
private fun FavoritesPreview() {
  EscaleTheme(dynamicColor = false) {
    FavoritesContent(state = previewState, actions = FavoritesActions(), onBack = {})
  }
}

@Preview(showBackground = true, name = "Favoris vides, thème clair")
@Preview(showBackground = true, name = "Favoris vides, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesEmptyPreview() {
  EscaleTheme(dynamicColor = false) {
    FavoritesContent(
      state = FavoritesUiState(historyEnabled = false),
      actions = FavoritesActions(),
      onBack = {},
    )
  }
}
