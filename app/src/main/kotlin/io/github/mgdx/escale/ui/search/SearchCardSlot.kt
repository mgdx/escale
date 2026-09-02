package io.github.mgdx.escale.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.appContainer

/**
 * Emplacement de la **carte de recherche flottante** (SPEC.md § 5.1), branché dans le `NavHost` au
 * point d'appel de `HomeScreen` (docs/architecture.md § 11.4).
 *
 * Tout ce que cet écran produit — départ, arrivée, heure — vit dans `SearchSession`, exposée par
 * l'`AppContainer`, parce que la feuille de résultats le consomme (SPEC.md § 5.1 : « dès que Départ
 * et Arrivée sont renseignés, la recherche se lance »). Aucune requête `plan` ne part d'ici.
 *
 * @param padding les encarts système transmis par `HomeScreen`. La carte se place elle-même sous
 *   la barre d'état, à 12 dp des bords.
 */
@Composable
fun SearchCardSlot(padding: PaddingValues, modifier: Modifier = Modifier) {
  val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(appContainer()))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val actions = remember(viewModel) { viewModel.actions() }

  SearchCard(state = state, actions = actions, padding = padding, modifier = modifier)

  // Le champ actif occupe l'écran entier, clavier compris, plutôt que de poser une liste par-dessus
  // la carte (SPEC.md § 5.1).
  state.activeField?.let { field ->
    SearchFieldOverlay(field = field, state = state, actions = actions)
  }

  state.timePicker?.let { picker ->
    TimeChoiceDialogs(picker = picker, actions = actions)
  }
}

/** Les rappels de l'écran, regroupés une fois pour toutes plutôt qu'alloués à chaque recomposition. */
private fun SearchViewModel.actions() = SearchActions(
  onOpenField = ::onOpenField,
  onCloseField = ::onCloseField,
  onQueryChange = ::onQueryChange,
  onSuggestionSelected = ::onSuggestionSelected,
  onShortcutSelected = ::onShortcutSelected,
  onChipSelected = ::onChipSelected,
  onClearField = ::onClearField,
  onSwap = ::onSwap,
  onMapPickCancelled = ::onMapPickCancelled,
  onRetryQuery = ::onRetryQuery,
  onOpenTimePicker = ::onOpenTimePicker,
  onDismissTimePicker = ::onDismissTimePicker,
  onTimeNowSelected = ::onTimeNowSelected,
  onTimeModeSelected = ::onTimeModeSelected,
  onDateSelected = ::onDateSelected,
  onTimeSelected = ::onTimeSelected,
)
