package io.github.mgdx.escale.ui.favorites

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.mgdx.escale.core.repository.AutocompleteRules
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.search.iconRes
import io.github.mgdx.escale.ui.search.labelRes
import io.github.mgdx.escale.ui.search.servedModesLabel
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Le choix d'un lieu à mettre en favori, **en plein écran** (SPEC.md § 5.5).
 *
 * C'est la même autocomplétion que la carte de recherche, et pour la même raison qu'elle prend tout
 * l'écran : ne pas superposer un clavier et une liste (SPEC.md § 5.1). Les règles de sobriété du
 * § 7.1 — anti-rebond, longueur minimale, annulation — vivent dans `autocompleteStream`, dans
 * `:core` ; il n'y a rien de tout cela ici.
 */
@Composable
internal fun PlacePickerDialog(picker: PlacePickerUi, actions: FavoritesActions) {
  Dialog(
    onDismissRequest = actions.onDismissPicker,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    PlacePickerContent(picker = picker, actions = actions)
  }
}

/**
 * Le contenu du plein écran, hors fenêtre : le nom éventuel, la recherche, la liste.
 *
 * Séparé de la `Dialog` pour être visible en aperçu — une fenêtre ne se rend pas dans un aperçu.
 */
@Composable
internal fun PlacePickerContent(picker: PlacePickerUi, actions: FavoritesActions) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
      PickerBar(picker = picker, actions = actions)
      // Le nom du lieu se saisit **avant** de le choisir : la suggestion choisie valide tout, et il
      // n'y a pas de second écran où l'usager pourrait perdre ce qu'il vient d'écrire.
      if (picker.target == PickerTarget.Place) {
        OutlinedTextField(
          value = picker.label,
          onValueChange = actions.onPickerLabelChange,
          modifier = Modifier.fillMaxWidth().padding(horizontal = BarPadding),
          label = { Text(text = stringResource(R.string.favorites_picker_label)) },
          singleLine = true,
        )
      }
      HorizontalDivider(modifier = Modifier.padding(top = BarSpacing))
      PickerSuggestions(picker = picker, actions = actions)
    }
  }
}

/** La barre de saisie : retour, champ de recherche, effacement. */
@Composable
private fun PickerBar(picker: PlacePickerUi, actions: FavoritesActions) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(picker.target) { focusRequester.requestFocus() }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = BarPadding, vertical = BarSpacing),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(BarSpacing),
  ) {
    IconButton(onClick = actions.onDismissPicker, modifier = Modifier.size(MinTouchTarget)) {
      Icon(
        painter = painterResource(R.drawable.ic_arrow_back),
        contentDescription = stringResource(R.string.action_back),
      )
    }
    TextField(
      value = picker.query,
      onValueChange = actions.onPickerQueryChange,
      modifier = Modifier.weight(1f).focusRequester(focusRequester),
      label = { Text(text = stringResource(picker.target.titleRes())) },
      placeholder = { Text(text = stringResource(picker.target.hintRes())) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
      ),
    )
  }
}

/**
 * Les suggestions du serveur.
 *
 * Pour un favori d'arrêt, la liste est **réduite aux arrêts** : enregistrer une adresse comme arrêt
 * produirait un favori dont les prochains départs n'existent pas. Le filtre est une fonction pure
 * de `FavoritesViewModel`, vérifiée en JVM.
 */
@Composable
private fun PickerSuggestions(picker: PlacePickerUi, actions: FavoritesActions) {
  val suggestions = if (picker.target == PickerTarget.Stop) {
    FavoritesViewModel.stopSuggestions(picker.suggestions)
  } else {
    picker.suggestions
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    when (suggestions) {
      AutocompleteState.Idle -> item { IdleMessage(query = picker.query, target = picker.target) }

      AutocompleteState.Loading -> item { LoadingRow() }

      is AutocompleteState.Suggestions -> if (suggestions.locations.isEmpty()) {
        item { MessageRow(text = stringResource(R.string.search_no_results)) }
      } else {
        items(items = suggestions.locations, key = ::suggestionKey) { location ->
          SuggestionRow(location = location, onClick = { actions.onPickerSelected(location) })
        }
      }

      is AutocompleteState.Failed -> item {
        ErrorMessage(
          error = suggestions.error,
          modifier = Modifier.padding(all = BarPadding),
          // Une frappe de plus relance la requête : réémettre le même texte suffit.
          onRetry = { actions.onPickerQueryChange(picker.query) },
        )
      }
    }
  }
}

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
private fun IdleMessage(query: String, target: PickerTarget) {
  if (query.isEmpty()) {
    if (target == PickerTarget.Stop) MessageRow(text = stringResource(R.string.favorites_picker_stops_only))
    return
  }
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
      modifier = Modifier.size(ProgressSize).clearAndSetSemantics { contentDescription = label },
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

/** L'identifiant d'arrêt quand il existe, les coordonnées sinon : deux homonymes ne se confondent pas. */
private fun suggestionKey(location: Location): String =
  location.id ?: (location.name + "@" + location.coordinates.lat + "," + location.coordinates.lon)

private val MinTouchTarget: Dp = 48.dp
private val BarPadding: Dp = 12.dp
private val BarSpacing: Dp = 8.dp
private val ProgressSize: Dp = 20.dp

@Preview(showBackground = true, name = "Choix d'un lieu, thème clair")
@Preview(showBackground = true, name = "Choix d'un lieu, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Choix d'un lieu, texte à 200 %", fontScale = 2f)
@Composable
private fun PlacePickerPreview() {
  EscaleTheme(dynamicColor = false) {
    PlacePickerContent(
      picker = PlacePickerUi(
        target = PickerTarget.Place,
        query = "Gare",
        label = "Chez Maman",
        suggestions = AutocompleteState.Suggestions(
          listOf(
            Location(
              id = "de:06:1234",
              name = "Gare de Lyon",
              description = "Paris 12e",
              coordinates = LatLon(lat = 48.8443, lon = 2.3735),
              kind = PlaceKind.STOP,
            ),
          ),
        ),
      ),
      actions = FavoritesActions(),
    )
  }
}
