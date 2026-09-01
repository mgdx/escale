package io.github.mgdx.escale.ui.settings

/**
 * État de l'écran de réglages.
 *
 * [serverUrl] est vide tant que le dépôt n'a pas émis : l'entrée « Serveur MOTIS » s'affiche alors
 * sans sous-titre plutôt que d'attendre.
 */
data class SettingsUiState(val serverUrl: String = "")
