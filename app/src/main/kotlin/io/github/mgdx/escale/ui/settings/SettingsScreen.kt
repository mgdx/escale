package io.github.mgdx.escale.ui.settings

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.AdditionalTransferTimeOptions
import io.github.mgdx.escale.core.model.CyclingSpeedOption
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.MaxTransfersOptions
import io.github.mgdx.escale.core.model.PedestrianSpeedOption
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Écran de réglages (SPEC.md § 5.6).
 *
 * La première entrée, avant toutes les autres, est « Serveur MOTIS », avec l'URL en cours en
 * sous-titre. Viennent ensuite les préférences de recherche, l'affichage, les données, et « À
 * propos » ferme la liste.
 *
 * Chaque réglage porte **une phrase qui dit ce qu'il fait** : « coût du dénivelé » ou « profil
 * piéton » ne veulent rien dire pour qui ne connaît pas l'API MOTIS, et l'écran de réglages est
 * précisément l'endroit où l'on ne peut pas supposer que l'usager la connaît.
 *
 * Un choix est enregistré au moment où il est fait : ni bouton « Appliquer », ni état à restituer
 * après une rotation.
 */
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onOpenServerSettings: () -> Unit,
  onOpenAbout: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  SettingsContent(
    uiState = uiState,
    actions = SettingsActions(
      onBack = onBack,
      onOpenServerSettings = onOpenServerSettings,
      onOpenAbout = onOpenAbout,
      onOpenDialog = viewModel::openDialog,
      onDismissDialog = viewModel::dismissDialog,
      onSearchChanged = viewModel::updateSearchPreferences,
      onDisplayChanged = viewModel::updateDisplayPreferences,
      onConfirmClear = viewModel::confirmClear,
      onReset = viewModel::resetToDefaults,
      onMessageShown = viewModel::messageShown,
    ),
    modifier = modifier,
  )
}

/**
 * Les actions de l'écran, réunies pour qu'aucun composable n'ait dix paramètres.
 *
 * `onSearchChanged` et `onDisplayChanged` reçoivent l'objet complet : l'écran calcule la copie
 * modifiée, le `ViewModel` l'enregistre. Aucune règle ne vit ici, seulement des `copy`.
 */
internal data class SettingsActions(
  val onBack: () -> Unit,
  val onOpenServerSettings: () -> Unit,
  val onOpenAbout: () -> Unit,
  val onOpenDialog: (SettingsDialog) -> Unit,
  val onDismissDialog: () -> Unit,
  val onSearchChanged: (SearchPreferences) -> Unit,
  val onDisplayChanged: (DisplayPreferences) -> Unit,
  val onConfirmClear: (ClearTarget) -> Unit,
  val onReset: () -> Unit,
  val onMessageShown: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(uiState: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
  val snackbarHostState = remember { SnackbarHostState() }
  val message = uiState.message
  val messageText = message?.let { stringResource(it.textRes()) }
  LaunchedEffect(message) {
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
        title = { Text(text = stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = actions.onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState()),
    ) {
      ListItem(
        headlineContent = { Text(text = stringResource(R.string.settings_server_title)) },
        supportingContent = {
          // Le sous-titre reste absent tant que le dépôt n'a rien émis, plutôt qu'affiché vide.
          if (uiState.serverUrl.isNotEmpty()) {
            Text(text = uiState.serverUrl)
          }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = actions.onOpenServerSettings),
      )
      HorizontalDivider()
      SearchSection(uiState.search, actions)
      HorizontalDivider()
      DisplaySection(uiState.display, actions)
      HorizontalDivider()
      DataSection(uiState, actions)
      HorizontalDivider()
      ListItem(
        headlineContent = { Text(text = stringResource(R.string.about_title)) },
        supportingContent = { Text(text = stringResource(R.string.about_attributions_title)) },
        modifier = Modifier.clickable(role = Role.Button, onClick = actions.onOpenAbout),
      )
      SettingsBottomSpacer()
    }
  }
  SettingsDialogHost(uiState, actions)
}

/** Préférences de recherche : tout ce qui part avec la requête `plan` (SPEC.md § 5.6). */
@Composable
private fun SearchSection(search: SearchPreferences, actions: SettingsActions) {
  SettingsSectionHeader(title = stringResource(R.string.settings_section_search))
  SettingsItem(
    title = stringResource(R.string.settings_walking_speed_title),
    value = stringResource(PedestrianSpeedOption.nearest(search.pedestrianSpeedMetersPerSecond).labelRes()),
    description = stringResource(R.string.settings_walking_speed_description),
    onClick = { actions.onOpenDialog(SettingsDialog.PedestrianSpeed) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_pedestrian_profile_title),
    value = stringResource(search.pedestrianProfile.labelRes()),
    description = stringResource(R.string.settings_pedestrian_profile_description),
    onClick = { actions.onOpenDialog(SettingsDialog.PedestrianProfile) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_cycling_speed_title),
    value = stringResource(CyclingSpeedOption.nearest(search.cyclingSpeedMetersPerSecond).labelRes()),
    description = stringResource(R.string.settings_cycling_speed_description),
    onClick = { actions.onOpenDialog(SettingsDialog.CyclingSpeed) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_elevation_costs_title),
    value = stringResource(search.elevationCosts.labelRes()),
    description = stringResource(R.string.settings_elevation_costs_description),
    onClick = { actions.onOpenDialog(SettingsDialog.ElevationCosts) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_transfer_margin_title),
    value = transferMarginLabel(AdditionalTransferTimeOptions.nearest(search.additionalTransferTime)),
    description = stringResource(R.string.settings_transfer_margin_description),
    onClick = { actions.onOpenDialog(SettingsDialog.TransferMargin) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_max_transfers_title),
    value = maxTransfersLabel(MaxTransfersOptions.nearest(search.maxTransfers)),
    description = stringResource(R.string.settings_max_transfers_description),
    onClick = { actions.onOpenDialog(SettingsDialog.MaxTransfers) },
  )
  SettingsSwitchItem(
    title = stringResource(R.string.settings_require_bike_transport_title),
    description = stringResource(R.string.settings_require_bike_transport_description),
    checked = search.requireBikeTransport,
    onCheckedChange = { actions.onSearchChanged(search.copy(requireBikeTransport = it)) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_rental_form_factors_title),
    value = rentalFormFactorsLabel(search.allowedRentalFormFactors),
    description = stringResource(R.string.settings_rental_form_factors_description),
    onClick = { actions.onOpenDialog(SettingsDialog.RentalFormFactors) },
  )
}

/**
 * Affichage : thème, format d'heure, couches de la carte (SPEC.md § 5.6 et § 5.7).
 *
 * **Point d'accroche du jalon 6, les arrêts sur la carte** : les trois bascules de couches sont
 * persistées dès maintenant et lisibles par `PreferencesRepository.displayPreferences`, champs
 * `showStops`, `showRentals` et `showPointsOfInterest`. Le lot qui affichera les arrêts n'a rien à
 * ajouter ici : il observe ce flux et masque la couche correspondante, indépendamment du zoom.
 *
 * Le format d'heure (`clockFormat`) attend de même son lecteur : le formatage des heures vit dans
 * `:core.format`, qui ne le consulte pas encore.
 */
@Composable
private fun DisplaySection(display: DisplayPreferences, actions: SettingsActions) {
  SettingsSectionHeader(title = stringResource(R.string.settings_section_display))
  SettingsItem(
    title = stringResource(R.string.settings_theme_title),
    value = stringResource(display.theme.labelRes()),
    description = stringResource(R.string.settings_theme_description),
    onClick = { actions.onOpenDialog(SettingsDialog.Theme) },
  )
  SettingsItem(
    title = stringResource(R.string.settings_clock_format_title),
    value = stringResource(display.clockFormat.labelRes()),
    description = stringResource(R.string.settings_clock_format_description),
    onClick = { actions.onOpenDialog(SettingsDialog.ClockFormat) },
  )
  SettingsSwitchItem(
    title = stringResource(R.string.settings_show_stops_title),
    description = stringResource(R.string.settings_show_stops_description),
    checked = display.showStops,
    onCheckedChange = { actions.onDisplayChanged(display.copy(showStops = it)) },
  )
  SettingsSwitchItem(
    title = stringResource(R.string.settings_show_rentals_title),
    description = stringResource(R.string.settings_show_rentals_description),
    checked = display.showRentals,
    onCheckedChange = { actions.onDisplayChanged(display.copy(showRentals = it)) },
  )
  SettingsSwitchItem(
    title = stringResource(R.string.settings_show_points_of_interest_title),
    description = stringResource(R.string.settings_show_points_of_interest_description),
    checked = display.showPointsOfInterest,
    onCheckedChange = { actions.onDisplayChanged(display.copy(showPointsOfInterest = it)) },
  )
}

/** Données : ce qui reste sur l'appareil, et comment s'en débarrasser (SPEC.md § 5.6). */
@Composable
private fun DataSection(uiState: SettingsUiState, actions: SettingsActions) {
  SettingsSectionHeader(title = stringResource(R.string.settings_section_data))
  SettingsSwitchItem(
    title = stringResource(R.string.settings_history_enabled_title),
    description = stringResource(R.string.settings_history_enabled_description),
    checked = uiState.display.historyEnabled,
    onCheckedChange = { actions.onDisplayChanged(uiState.display.copy(historyEnabled = it)) },
  )
  // L'entrée n'apparaît que lorsque le jalon 10 aura branché un HistoryCleaner.
  if (uiState.canClearHistory) {
    ClearItem(ClearTarget.HISTORY, R.string.settings_clear_history_description, actions)
  }
  ClearItem(ClearTarget.TILE_CACHE, R.string.settings_clear_tile_cache_description, actions)
  ClearItem(ClearTarget.RESULTS_CACHE, R.string.settings_clear_results_cache_description, actions)
  ClearItem(ClearTarget.GEOCODE_CACHE, R.string.settings_clear_geocode_cache_description, actions)
  SettingsItem(
    title = stringResource(R.string.settings_reset_title),
    description = stringResource(R.string.settings_reset_description),
    onClick = { actions.onOpenDialog(SettingsDialog.ConfirmReset) },
  )
}

@Composable
private fun ClearItem(target: ClearTarget, @StringRes descriptionRes: Int, actions: SettingsActions) {
  SettingsItem(
    title = stringResource(target.titleRes()),
    description = stringResource(descriptionRes),
    onClick = { actions.onOpenDialog(SettingsDialog.ConfirmClear(target)) },
  )
}

private val previewActions = SettingsActions(
  onBack = {},
  onOpenServerSettings = {},
  onOpenAbout = {},
  onOpenDialog = {},
  onDismissDialog = {},
  onSearchChanged = {},
  onDisplayChanged = {},
  onConfirmClear = {},
  onReset = {},
  onMessageShown = {},
)

@Preview(showBackground = true, name = "Réglages, thème clair")
@Preview(
  showBackground = true,
  name = "Réglages, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(showBackground = true, name = "Réglages, texte à 200 %", fontScale = 2f, heightDp = 1400)
@Composable
private fun SettingsScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    SettingsContent(
      uiState = SettingsUiState(serverUrl = "https://api.transitous.org"),
      actions = previewActions,
    )
  }
}

@Preview(showBackground = true, name = "Choix du profil piéton")
@Preview(showBackground = true, name = "Choix du profil piéton à 200 %", fontScale = 2f, heightDp = 900)
@Composable
private fun SettingsProfileDialogPreview() {
  EscaleTheme(dynamicColor = false) {
    SettingsContent(
      uiState = SettingsUiState(
        serverUrl = "https://api.transitous.org",
        dialog = SettingsDialog.PedestrianProfile,
      ),
      actions = previewActions,
    )
  }
}
