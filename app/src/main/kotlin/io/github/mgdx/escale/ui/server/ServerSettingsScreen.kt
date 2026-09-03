package io.github.mgdx.escale.ui.server

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.theme.EscaleTheme
import kotlinx.coroutines.launch

/** L'écran « Serveur MOTIS » (SPEC.md § 5.6.1). */
@Composable
fun ServerSettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ServerSettingsViewModel =
    viewModel(factory = ServerSettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ServerSettingsContent(
    uiState = uiState,
    actions = ServerSettingsActions(
      onBack = onBack,
      onInputChange = viewModel::onInputChange,
      onTestConnection = viewModel::onTestConnection,
      onSave = viewModel::onSave,
      onUseAnyway = viewModel::onUseAnyway,
      onSelectKnownServer = viewModel::onSelectKnownServer,
      onForgetServer = viewModel::onForgetServer,
      onResetToDefault = viewModel::onResetToDefault,
      onAcceptCleartext = viewModel::onAcceptCleartext,
      onConfirmSwitch = viewModel::onConfirmSwitch,
      onDismissDialog = viewModel::onDismissDialog,
    ),
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSettingsContent(
  uiState: ServerSettingsUiState,
  actions: ServerSettingsActions,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.server_settings_title)) },
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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      CurrentServer(uiState.currentServerUrl)
      ServerUrlField(uiState = uiState, onInputChange = actions.onInputChange)
      ServerActions(uiState = uiState, actions = actions)
      ConnectionTestResults(test = uiState.connectionTest, onRetry = actions.onTestConnection)
      HorizontalDivider()
      KnownServers(uiState = uiState, actions = actions)
      HorizontalDivider()
      PublicInstanceFooter()
    }
  }
  ServerDialogs(dialog = uiState.dialog, actions = actions)
}

@Composable
private fun CurrentServer(currentServerUrl: String) {
  Column {
    Text(
      text = stringResource(R.string.server_settings_current_label),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = currentServerUrl,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

/**
 * Le champ d'URL : clavier de type URI, ni correction automatique ni majuscule initiale, et un
 * bouton de collage pour éviter la saisie au clavier d'une adresse longue (SPEC.md § 5.6.1).
 */
@Composable
private fun ServerUrlField(uiState: ServerSettingsUiState, onInputChange: (String) -> Unit) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  OutlinedTextField(
    value = uiState.input,
    onValueChange = onInputChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(text = stringResource(R.string.server_settings_url_label)) },
    singleLine = true,
    isError = uiState.inputInvalid,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.None,
      autoCorrectEnabled = false,
      keyboardType = KeyboardType.Uri,
      imeAction = ImeAction.Done,
    ),
    trailingIcon = {
      IconButton(
        onClick = {
          scope.launch {
            pastedText(clipboard)?.let(onInputChange)
          }
        },
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_content_paste),
          contentDescription = stringResource(R.string.server_settings_paste),
        )
      }
    },
    supportingText = { UrlSupportingText(uiState) },
  )
}

@Composable
private fun UrlSupportingText(uiState: ServerSettingsUiState) {
  when {
    uiState.inputInvalid -> Text(text = stringResource(R.string.server_settings_url_invalid))

    // La normalisation est montrée dès qu'elle change quelque chose : l'usager voit ce qui sera
    // réellement enregistré, à savoir la racine du serveur (SPEC.md § 5.6.1).
    uiState.normalizedInput != null && uiState.normalizedInput != uiState.input ->
      Text(text = stringResource(R.string.server_settings_url_normalized, uiState.normalizedInput))

    else -> Text(text = stringResource(R.string.server_settings_url_hint))
  }
}

@Composable
private fun ServerActions(uiState: ServerSettingsUiState, actions: ServerSettingsActions) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = actions.onTestConnection,
      modifier = Modifier.fillMaxWidth(),
      enabled = uiState.normalizedInput != null && !uiState.connectionTest.running,
    ) {
      Text(text = stringResource(R.string.server_settings_test))
    }
    Button(
      onClick = actions.onSave,
      modifier = Modifier.fillMaxWidth(),
      enabled = uiState.canSave,
    ) {
      Text(text = stringResource(R.string.server_settings_save))
    }
    if (uiState.canForce) {
      // « Utiliser quand même » : la seule porte de sortie quand le test n'a pas réussi
      // (SPEC.md § 5.6.1).
      OutlinedButton(onClick = actions.onUseAnyway, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.server_settings_use_anyway))
      }
    }
    TextButton(onClick = actions.onResetToDefault, modifier = Modifier.fillMaxWidth()) {
      Icon(
        painter = painterResource(R.drawable.ic_settings_backup_restore),
        // Le libellé du bouton dit déjà l'action : redire l'icône ferait doublon au lecteur d'écran.
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(R.string.server_settings_reset_default),
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

/** Les trois résultats du test, affichés séparément et chacun doublé d'un texte (SPEC.md § 9). */
@Composable
private fun ConnectionTestResults(test: ConnectionTest, onRetry: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.server_settings_test_results),
      style = MaterialTheme.typography.titleSmall,
    )
    CheckStepRow(R.string.server_settings_check_reachable, test.reachable)
    CheckStepRow(R.string.server_settings_check_api, test.apiVersion)
    CheckStepRow(R.string.server_settings_check_tiles, test.tiles)
    if (test.tiles == CheckStepState.ABSENT) {
      Text(
        text = stringResource(R.string.server_settings_tiles_absent_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    test.error?.let { error -> ErrorMessage(error = error, onRetry = onRetry) }
  }
}

@Composable
private fun CheckStepRow(labelRes: Int, state: CheckStepState) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      if (state == CheckStepState.RUNNING) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Icon(
          painter = painterResource(state.iconRes()),
          // L'état est déjà écrit en toutes lettres sous le libellé : l'icône ne le redit pas au
          // lecteur d'écran, elle le double pour l'œil (SPEC.md § 9).
          contentDescription = null,
          tint = state.tint(),
        )
      }
    }
    Column(modifier = Modifier.padding(start = 12.dp)) {
      Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
      Text(
        text = stringResource(state.labelRes()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun KnownServers(uiState: ServerSettingsUiState, actions: ServerSettingsActions) {
  Column {
    Text(
      text = stringResource(R.string.server_settings_known_title),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    if (uiState.knownServers.isEmpty()) {
      Text(
        text = stringResource(R.string.server_settings_known_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    uiState.knownServers.forEach { server ->
      KnownServerRow(
        server = server,
        inUse = server.baseUrl == uiState.currentServerUrl,
        onSelect = { actions.onSelectKnownServer(server) },
        onForget = { actions.onForgetServer(server.baseUrl) },
      )
    }
    Text(
      text = stringResource(R.string.server_settings_known_swipe_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

/**
 * Suppression par balayage, telle que demandée par SPEC.md § 5.6.1.
 *
 * Le balayage est un geste, et un geste n'est atteignable ni au lecteur d'écran, ni à la commande
 * par contacteur : la même suppression est donc exposée comme **action d'accessibilité** sur la
 * ligne. Sans elle, retirer un serveur serait tout simplement impossible à qui n'a pas l'usage de
 * l'écran tactile (SPEC.md § 9).
 */
@Composable
private fun KnownServerRow(server: ServerConfig, inUse: Boolean, onSelect: () -> Unit, onForget: () -> Unit) {
  val dismissState = rememberSwipeToDismissBoxState()
  val forgetLabel = stringResource(R.string.server_settings_forget)
  val forgetActions = remember(inUse, forgetLabel, onForget) {
    // Le serveur en service ne se supprime pas, pas plus par l'action que par le balayage.
    if (inUse) emptyList() else listOf(CustomAccessibilityAction(forgetLabel) { onForget(); true })
  }
  SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { ForgetBackground() },
    // Le serveur en service ne se supprime pas d'un balayage : il n'y aurait plus rien à utiliser.
    enableDismissFromStartToEnd = !inUse,
    enableDismissFromEndToStart = !inUse,
    onDismiss = { onForget() },
  ) {
    ListItem(
      headlineContent = { Text(text = server.label) },
      supportingContent = { Text(text = server.baseUrl) },
      trailingContent = {
        if (inUse) {
          Text(
            text = stringResource(R.string.server_settings_known_in_use),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      },
      leadingContent = {
        Icon(
          painter = painterResource(R.drawable.ic_dns),
          contentDescription = null,
        )
      },
      modifier = Modifier
        .semantics { customActions = forgetActions }
        .clickable(
          enabled = !inUse,
          role = Role.Button,
          onClick = onSelect,
        ),
    )
  }
}

@Composable
private fun ForgetBackground() {
  ListItem(
    headlineContent = {
      Text(
        text = stringResource(R.string.server_settings_forget),
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    },
    leadingContent = {
      Icon(
        painter = painterResource(R.drawable.ic_delete),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
  )
}

/** Rappel de la politique d'usage de l'instance publique (SPEC.md § 4.2 et § 5.6.1). */
@Composable
private fun PublicInstanceFooter() {
  val uriHandler = LocalUriHandler.current
  val policyUrl = stringResource(R.string.server_settings_policy_url)
  Column {
    Text(
      text = stringResource(R.string.server_settings_public_instance_notice),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { runCatching { uriHandler.openUri(policyUrl) } }) {
      Text(text = stringResource(R.string.server_settings_policy_link))
      Icon(
        painter = painterResource(R.drawable.ic_open_in_new),
        contentDescription = stringResource(R.string.settings_action_opens_externally),
        modifier = Modifier
          .padding(start = 8.dp)
          .size(18.dp),
      )
    }
  }
}

@Composable
private fun ServerDialogs(dialog: ServerDialog?, actions: ServerSettingsActions) {
  when (dialog) {
    null -> Unit

    is ServerDialog.CleartextWarning -> AlertDialog(
      onDismissRequest = actions.onDismissDialog,
      icon = {
        Icon(
          painter = painterResource(R.drawable.ic_warning),
          contentDescription = null,
        )
      },
      title = { Text(text = stringResource(R.string.server_settings_cleartext_title)) },
      text = { Text(text = stringResource(R.string.server_settings_cleartext_message, dialog.host)) },
      confirmButton = {
        TextButton(onClick = actions.onAcceptCleartext) {
          Text(text = stringResource(R.string.server_settings_cleartext_accept))
        }
      },
      dismissButton = {
        TextButton(onClick = actions.onDismissDialog) {
          Text(text = stringResource(R.string.settings_action_cancel))
        }
      },
    )

    is ServerDialog.SwitchEffects -> AlertDialog(
      onDismissRequest = actions.onDismissDialog,
      title = { Text(text = stringResource(R.string.server_settings_switch_title)) },
      text = { SwitchEffectsText(dialog) },
      confirmButton = {
        TextButton(onClick = actions.onConfirmSwitch) {
          Text(text = stringResource(R.string.server_settings_switch_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = actions.onDismissDialog) {
          Text(text = stringResource(R.string.settings_action_cancel))
        }
      },
    )
  }
}

@Composable
private fun SwitchEffectsText(dialog: ServerDialog.SwitchEffects) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = stringResource(R.string.server_settings_switch_message, dialog.baseUrl))
    Text(text = stringResource(R.string.server_settings_switch_favorites))
    if (dialog.untested) {
      // Un avertissement se distingue d'une explication par son pictogramme, jamais par sa seule
      // couleur (SPEC.md § 9). L'icône est muette, le texte à côté dit tout.
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_warning),
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.error,
        )
        Text(
          text = stringResource(R.string.server_settings_switch_untested),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

/**
 * Le texte présent dans le presse-papiers, ou `null` s'il n'y en a pas d'exploitable.
 *
 * Le collage est demandé explicitement par l'usager, et rien n'est journalisé : le presse-papiers
 * peut contenir n'importe quelle donnée personnelle (SPEC.md § 11).
 */
private suspend fun pastedText(clipboard: Clipboard): String? {
  val clipData = clipboard.getClipEntry()?.clipData ?: return null
  if (clipData.itemCount == 0) return null
  return clipData.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
}

/** Le pictogramme qui double l'état d'une étape, pour que la couleur ne soit jamais seule. */
private fun CheckStepState.iconRes(): Int = when (this) {
  CheckStepState.IDLE -> R.drawable.ic_info
  CheckStepState.RUNNING -> R.drawable.ic_info
  CheckStepState.PASSED -> R.drawable.ic_check_circle
  CheckStepState.FAILED -> R.drawable.ic_error
  CheckStepState.ABSENT -> R.drawable.ic_info
}

private fun CheckStepState.labelRes(): Int = when (this) {
  CheckStepState.IDLE -> R.string.server_settings_step_idle
  CheckStepState.RUNNING -> R.string.server_settings_step_running
  CheckStepState.PASSED -> R.string.server_settings_step_passed
  CheckStepState.FAILED -> R.string.server_settings_step_failed
  CheckStepState.ABSENT -> R.string.server_settings_step_absent
}

@Composable
private fun CheckStepState.tint(): Color = when (this) {
  CheckStepState.FAILED -> MaterialTheme.colorScheme.error
  CheckStepState.PASSED -> MaterialTheme.colorScheme.primary
  else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true, name = "Serveur MOTIS, texte à 200 %", fontScale = 2f, heightDp = 1400)
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
      uiState = ServerSettingsUiState(
        currentServerUrl = "https://api.transitous.org",
        input = "https://api.transitous.org",
        normalizedInput = "https://api.transitous.org",
        connectionTest = ConnectionTest(
          reachable = CheckStepState.PASSED,
          apiVersion = CheckStepState.PASSED,
          tiles = CheckStepState.ABSENT,
        ),
        knownServers = listOf(
          ServerConfig(baseUrl = "https://api.transitous.org", label = "api.transitous.org"),
          ServerConfig(baseUrl = "http://192.168.1.10:8080", label = "192.168.1.10"),
        ),
      ),
      actions = ServerSettingsActions(),
    )
  }
}
