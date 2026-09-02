package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.Location

/**
 * Les actions de la carte de recherche, groupées : une signature de composable à quinze paramètres
 * ne se relit pas, et le contrat de l'écran tient ainsi en un seul type.
 */
data class SearchActions(
  val onOpenField: (SearchField) -> Unit = {},
  val onCloseField: () -> Unit = {},
  val onQueryChange: (String) -> Unit = {},
  val onSuggestionSelected: (Location) -> Unit = {},
  val onShortcutSelected: (SearchShortcut) -> Unit = {},
  val onChipSelected: (QuickChip) -> Unit = {},
  val onClearField: (SearchField) -> Unit = {},
  val onSwap: () -> Unit = {},
  val onMapPickCancelled: () -> Unit = {},
  val onRetryQuery: () -> Unit = {},
  val onOpenTimePicker: () -> Unit = {},
  val onDismissTimePicker: () -> Unit = {},
  val onTimeNowSelected: () -> Unit = {},
  val onTimeModeSelected: (TimeMode) -> Unit = {},
  val onDateSelected: (Long) -> Unit = {},
  val onTimeSelected: (Int, Int) -> Unit = { _, _ -> },
)
