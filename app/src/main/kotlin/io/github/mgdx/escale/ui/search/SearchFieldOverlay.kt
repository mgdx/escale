package io.github.mgdx.escale.ui.search

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.AutocompleteRules
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Le champ actif, **en plein écran**, avec sa liste d'autocomplétion (SPEC.md § 5.1).
 *
 * Ce n'est pas une liste déroulante flottante, et c'est une décision d'ergonomie explicite de la
 * spec : « le champ actif passe en plein écran avec la liste d'autocomplétion, pour ne pas
 * superposer un clavier et une liste au-dessus de la carte ».
 *
 * Le plein écran passe par une `Dialog` et non par une simple surface : l'emplacement de la carte
 * de recherche est composé *avant* le bouton de position et la feuille de résultats
 * (docs/architecture.md § 11.4), et une surface posée là resterait sous eux.
 */
@Composable
fun SearchFieldOverlay(field: SearchField, state: SearchUiState, actions: SearchActions) {
  Dialog(
    onDismissRequest = actions.onCloseField,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    SearchFieldContent(field = field, state = state, actions = actions)
  }
}

/**
 * Le contenu du plein écran, hors fenêtre : la barre de saisie et la liste.
 *
 * Séparé de la `Dialog` pour être visible en aperçu — une fenêtre ne se rend pas dans un aperçu, et
 * c'est justement cet écran, dense et plein de texte, qu'il faut regarder à 200 %.
 */
@Composable
internal fun SearchFieldContent(field: SearchField, state: SearchUiState, actions: SearchActions) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .imePadding(),
    ) {
      SearchInputBar(field = field, query = state.query, actions = actions)
      HorizontalDivider()
      SuggestionList(state = state, actions = actions)
    }
  }
}

/** La barre de saisie : retour, champ, effacement. */
@Composable
private fun SearchInputBar(field: SearchField, query: String, actions: SearchActions) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(field) { focusRequester.requestFocus() }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = BarPadding, vertical = BarSpacing),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(BarSpacing),
  ) {
    IconButton(onClick = actions.onCloseField, modifier = Modifier.size(MinTouchTarget)) {
      Icon(
        painter = painterResource(R.drawable.ic_arrow_back),
        contentDescription = stringResource(R.string.action_back),
      )
    }
    TextField(
      value = query,
      onValueChange = actions.onQueryChange,
      modifier = Modifier.weight(1f).focusRequester(focusRequester),
      label = { Text(text = stringResource(field.labelRes())) },
      placeholder = { Text(text = stringResource(field.hintRes())) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { actions.onQueryChange("") }, modifier = Modifier.size(MinTouchTarget)) {
            Icon(
              painter = painterResource(R.drawable.ic_cancel),
              contentDescription = stringResource(R.string.search_clear),
            )
          }
        }
      },
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
      ),
    )
  }
}

/**
 * Les entrées de tête, puis les résultats du serveur (SPEC.md § 5.1).
 *
 * Aucun anti-rebond ici, et aucun compte de caractères : ces règles vivent dans
 * `autocompleteStream`, dans `:core` (SPEC.md § 7.1). Cette liste ne fait qu'afficher l'état
 * qu'elle reçoit.
 */
@Composable
private fun SuggestionList(state: SearchUiState, actions: SearchActions) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(items = state.shortcuts, key = { it.name }) { shortcut ->
      ListItem(
        headlineContent = { Text(text = stringResource(shortcut.labelRes())) },
        modifier = Modifier.fillMaxWidth().clickable { actions.onShortcutSelected(shortcut) },
        leadingContent = {
          Icon(painter = painterResource(shortcut.iconRes()), contentDescription = null)
        },
      )
    }
    item { HorizontalDivider() }
    when (val suggestions = state.suggestions) {
      AutocompleteState.Idle -> item { IdleMessage(query = state.query) }

      AutocompleteState.Loading -> item { LoadingRow() }

      is AutocompleteState.Suggestions -> if (suggestions.locations.isEmpty()) {
        item { MessageRow(text = stringResource(R.string.search_no_results)) }
      } else {
        itemsIndexed(items = suggestions.locations, key = ::suggestionKey) { _, location ->
          SuggestionRow(location = location, onClick = { actions.onSuggestionSelected(location) })
        }
      }

      is AutocompleteState.Failed -> item {
        ErrorMessage(
          error = suggestions.error,
          modifier = Modifier.padding(all = BarPadding),
          onRetry = actions.onRetryQuery,
        )
      }
    }
  }
}

/**
 * Un résultat d'autocomplétion.
 *
 * Le pictogramme distingue l'adresse, l'arrêt et le lieu ; les modes desservis par un arrêt sont
 * écrits **en toutes lettres**, faute de pictogrammes de mode, qui appartiennent à l'écran des
 * résultats (SPEC.md § 5.1 et § 9).
 */
@Composable
private fun SuggestionRow(location: Location, onClick: () -> Unit) {
  val kindLabel = stringResource(location.kind.labelRes())
  val modes = servedModesLabel(location.servedModes)
  ListItem(
    headlineContent = { Text(text = location.name) },
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    supportingContent = {
      val supporting = listOfNotNull(location.description?.takeIf { it.isNotBlank() }, modes.takeIf { it.isNotEmpty() })
      if (supporting.isNotEmpty()) {
        Column {
          supporting.forEach { line -> Text(text = line, style = MaterialTheme.typography.bodyMedium) }
        }
      }
    },
    leadingContent = {
      Icon(painter = painterResource(location.kind.iconRes()), contentDescription = kindLabel)
    },
  )
}

/** Ce que la liste montre tant qu'aucune requête n'a été émise. */
@Composable
private fun IdleMessage(query: String) {
  if (query.isEmpty()) return
  MessageRow(
    text = pluralStringResource(
      R.plurals.search_min_length,
      AutocompleteRules.MIN_TEXT_LENGTH,
      AutocompleteRules.MIN_TEXT_LENGTH,
    ),
  )
}

@Composable
private fun LoadingRow() {
  val label = stringResource(R.string.search_loading)
  Row(
    modifier = Modifier.fillMaxWidth().padding(all = BarPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(BarSpacing),
  ) {
    CircularProgressIndicator(
      modifier = Modifier
        .size(ProgressSize)
        .clearAndSetSemantics { contentDescription = label },
    )
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun MessageRow(text: String) {
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth().padding(all = BarPadding),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * Une clé stable par suggestion, **et unique quoi que rende le serveur**.
 *
 * L'identifiant d'arrêt quand il existe, le nom et les coordonnées sinon : deux adresses homonymes
 * de deux villes différentes ne se confondent pas. Le rang y est joint parce que la liste vient
 * d'ailleurs : rien ne garantit qu'un serveur de géocodage ne rendra pas deux fois le même arrêt,
 * ni deux adresses de même nom au même point — deux clés égales ne dégraderaient pas la liste,
 * elles la feraient lever (`IllegalArgumentException`). Le rang ne coûte rien ici : la liste est
 * remplacée en entier à chaque réponse, aucune identité n'a à lui survivre.
 */
internal fun suggestionKey(index: Int, location: Location): String = index.toString() + "-" +
  (location.id ?: (location.name + "@" + location.coordinates.lat + "," + location.coordinates.lon))

private val MinTouchTarget: Dp = 48.dp
private val BarPadding: Dp = 12.dp
private val BarSpacing: Dp = 8.dp
private val ProgressSize: Dp = 24.dp

// --- Aperçus -----------------------------------------------------------------------------------
//
// Les noms de lieux sont des données d'exemple, telles que le serveur les renverrait ; toutes les
// chaînes d'interface viennent de `strings_search.xml`.

@Preview(showBackground = true, name = "Autocomplétion, thème clair")
@Preview(showBackground = true, name = "Autocomplétion, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Autocomplétion, texte à 200 %", fontScale = 2f)
@Composable
private fun SearchFieldContentPreview() {
  EscaleTheme(dynamicColor = false) {
    SearchFieldContent(
      field = SearchField.TO,
      state = SearchUiState(
        query = "gare",
        shortcuts = listOf(SearchShortcut.MY_LOCATION, SearchShortcut.HOME, SearchShortcut.PICK_ON_MAP),
        suggestions = AutocompleteState.Suggestions(
          listOf(
            Location(
              id = "de:0800:1234",
              name = "Gare de Lyon",
              description = "Paris 12e",
              coordinates = LatLon(lat = 48.8443, lon = 2.3737),
              kind = PlaceKind.STOP,
              servedModes = listOf(TransitMode.RAIL, TransitMode.SUBWAY),
            ),
            Location(
              id = null,
              name = "Rue de la Gare",
              description = "Vincennes",
              coordinates = LatLon(lat = 48.8478, lon = 2.4370),
              kind = PlaceKind.ADDRESS,
            ),
          ),
        ),
      ),
      actions = SearchActions(),
    )
  }
}
