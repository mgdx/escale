package io.github.mgdx.escale.ui.server

/**
 * État de l'écran « Serveur MOTIS ».
 *
 * Volontairement réduit à l'URL en cours pour le jalon 1 : la saisie, la normalisation, le test en
 * trois étapes, la liste des serveurs déjà utilisés et l'avertissement sur le trafic en clair
 * (SPEC.md § 5.6.1) viennent enrichir cet état au lot suivant.
 */
data class ServerSettingsUiState(val serverUrl: String = "")
