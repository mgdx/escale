package io.github.mgdx.escale.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.AdditionalTransferTimeOptions
import io.github.mgdx.escale.core.model.ClockFormat
import io.github.mgdx.escale.core.model.CyclingSpeedOption
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.MaxTransfersOptions
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.PedestrianSpeedOption
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.ThemeChoice

/**
 * Les dialogues de l'écran de réglages, un par entrée.
 *
 * Chacun rappelle en tête la phrase qui dit à quoi sert le réglage : l'usager ouvre justement le
 * dialogue parce que le titre seul ne lui suffisait pas.
 *
 * Un choix est appliqué puis le dialogue se ferme : il n'y a rien à valider, donc rien à perdre.
 */
@Composable
internal fun SettingsDialogHost(uiState: SettingsUiState, actions: SettingsActions) {
  when (val dialog = uiState.dialog) {
    null -> Unit

    SettingsDialog.PedestrianSpeed -> PedestrianSpeedDialog(uiState.search, actions)

    SettingsDialog.PedestrianProfile -> PedestrianProfileDialog(uiState.search, actions)

    SettingsDialog.CyclingSpeed -> CyclingSpeedDialog(uiState.search, actions)

    SettingsDialog.ElevationCosts -> ElevationCostsDialog(uiState.search, actions)

    SettingsDialog.TransferMargin -> TransferMarginDialog(uiState.search, actions)

    SettingsDialog.MaxTransfers -> MaxTransfersDialog(uiState.search, actions)

    SettingsDialog.RentalFormFactors -> RentalFormFactorsDialog(
      allowed = uiState.search.allowedRentalFormFactors,
      onChanged = { actions.onSearchChanged(uiState.search.copy(allowedRentalFormFactors = it)) },
      onDismiss = actions.onDismissDialog,
    )

    SettingsDialog.Theme -> ThemeDialog(uiState.display, actions)

    SettingsDialog.ClockFormat -> ClockFormatDialog(uiState.display, actions)

    is SettingsDialog.ConfirmClear -> SettingsConfirmDialog(
      title = stringResource(dialog.target.titleRes()),
      message = stringResource(dialog.target.confirmationRes()),
      confirmLabel = stringResource(R.string.settings_action_delete),
      onConfirm = { actions.onConfirmClear(dialog.target) },
      onDismiss = actions.onDismissDialog,
    )

    SettingsDialog.ConfirmReset -> SettingsConfirmDialog(
      title = stringResource(R.string.settings_reset_title),
      message = stringResource(R.string.settings_reset_confirmation),
      confirmLabel = stringResource(R.string.settings_action_reset),
      onConfirm = actions.onReset,
      onDismiss = actions.onDismissDialog,
    )
  }
}

@Composable
private fun PedestrianSpeedDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_walking_speed_title),
    description = stringResource(R.string.settings_walking_speed_description),
    options = PedestrianSpeedOption.entries,
    selected = PedestrianSpeedOption.nearest(search.pedestrianSpeedMetersPerSecond),
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onSearchChanged(search.copy(pedestrianSpeedMetersPerSecond = it.metersPerSecond)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun PedestrianProfileDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_pedestrian_profile_title),
    description = stringResource(R.string.settings_pedestrian_profile_description),
    options = PedestrianProfile.entries,
    selected = search.pedestrianProfile,
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onSearchChanged(search.copy(pedestrianProfile = it)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun CyclingSpeedDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_cycling_speed_title),
    description = stringResource(R.string.settings_cycling_speed_description),
    options = CyclingSpeedOption.entries,
    selected = CyclingSpeedOption.nearest(search.cyclingSpeedMetersPerSecond),
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onSearchChanged(search.copy(cyclingSpeedMetersPerSecond = it.metersPerSecond)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun ElevationCostsDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_elevation_costs_title),
    description = stringResource(R.string.settings_elevation_costs_description),
    options = ElevationCosts.entries,
    selected = search.elevationCosts,
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onSearchChanged(search.copy(elevationCosts = it)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun TransferMarginDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_transfer_margin_title),
    description = stringResource(R.string.settings_transfer_margin_description),
    options = AdditionalTransferTimeOptions.VALUES,
    selected = AdditionalTransferTimeOptions.nearest(search.additionalTransferTime),
    label = { transferMarginLabel(it) },
    onChoose = { actions.onSearchChanged(search.copy(additionalTransferTime = it)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun MaxTransfersDialog(search: SearchPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_max_transfers_title),
    description = stringResource(R.string.settings_max_transfers_description),
    options = MaxTransfersOptions.VALUES,
    selected = MaxTransfersOptions.nearest(search.maxTransfers),
    label = { maxTransfersLabel(it) },
    onChoose = { actions.onSearchChanged(search.copy(maxTransfers = it)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun ThemeDialog(display: DisplayPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_theme_title),
    description = stringResource(R.string.settings_theme_description),
    options = ThemeChoice.entries,
    selected = display.theme,
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onDisplayChanged(display.copy(theme = it)) },
    onDismiss = actions.onDismissDialog,
  )
}

@Composable
private fun ClockFormatDialog(display: DisplayPreferences, actions: SettingsActions) {
  SettingsChoiceDialog(
    title = stringResource(R.string.settings_clock_format_title),
    description = stringResource(R.string.settings_clock_format_description),
    options = ClockFormat.entries,
    selected = display.clockFormat,
    label = { stringResource(it.labelRes()) },
    onChoose = { actions.onDisplayChanged(display.copy(clockFormat = it)) },
    onDismiss = actions.onDismissDialog,
  )
}
