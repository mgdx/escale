package io.github.mgdx.escale.ui.server

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Écran « Serveur MOTIS », réduit au strict nécessaire pour le jalon 1 : il affiche le serveur en
 * service et sert de point d'accroche.
 *
 * Le comportement complet décrit par SPEC.md § 5.6.1 — saisie et normalisation de l'URL, test de
 * connexion en trois étapes, mémorisation des serveurs déjà utilisés, retour au serveur par défaut,
 * avertissement sur le trafic en clair — est le travail du lot suivant.
 */
@Composable
fun ServerSettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ServerSettingsViewModel =
    viewModel(factory = ServerSettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ServerSettingsContent(uiState = uiState, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSettingsContent(uiState: ServerSettingsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.server_settings_title)) },
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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      Text(
        text = stringResource(R.string.server_settings_current_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = uiState.serverUrl,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 4.dp),
      )
      Text(
        text = stringResource(R.string.server_settings_public_instance_notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp),
      )
    }
  }
}

@Preview(showBackground = true, name = "Serveur MOTIS, thème clair")
@Preview(
  showBackground = true,
  name = "Serveur MOTIS, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServerSettingsScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    ServerSettingsContent(
      uiState = ServerSettingsUiState(serverUrl = "https://api.transitous.org"),
      onBack = {},
    )
  }
}
