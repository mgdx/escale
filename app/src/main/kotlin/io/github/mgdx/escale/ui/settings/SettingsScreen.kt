package io.github.mgdx.escale.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Écran de réglages (SPEC.md § 5.6).
 *
 * La première entrée, avant toutes les autres, est « Serveur MOTIS », avec l'URL en cours en
 * sous-titre. Les autres rubriques — préférences de recherche, affichage, données, à propos —
 * viendront à leurs jalons respectifs.
 */
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onOpenServerSettings: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  SettingsContent(
    uiState = uiState,
    onBack = onBack,
    onOpenServerSettings = onOpenServerSettings,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
  uiState: SettingsUiState,
  onBack: () -> Unit,
  onOpenServerSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.settings_title)) },
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
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenServerSettings),
      )
    }
  }
}

@Preview(showBackground = true, name = "Réglages, thème clair")
@Preview(
  showBackground = true,
  name = "Réglages, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    SettingsContent(
      uiState = SettingsUiState(serverUrl = "https://api.transitous.org"),
      onBack = {},
      onOpenServerSettings = {},
    )
  }
}
