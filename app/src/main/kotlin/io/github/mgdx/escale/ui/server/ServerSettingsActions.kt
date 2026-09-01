package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.model.ServerConfig

/**
 * Les actions de l'écran, groupées : une signature de composable à quinze paramètres ne se relit
 * pas, et le contrat de l'écran tient ainsi en un seul type.
 */
data class ServerSettingsActions(
  val onBack: () -> Unit = {},
  val onInputChange: (String) -> Unit = {},
  val onTestConnection: () -> Unit = {},
  val onSave: () -> Unit = {},
  val onUseAnyway: () -> Unit = {},
  val onSelectKnownServer: (ServerConfig) -> Unit = {},
  val onForgetServer: (String) -> Unit = {},
  val onResetToDefault: () -> Unit = {},
  val onAcceptCleartext: () -> Unit = {},
  val onConfirmSwitch: () -> Unit = {},
  val onDismissDialog: () -> Unit = {},
)
