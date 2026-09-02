package io.github.mgdx.escale.ui.settings

import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.SearchPreferences

/**
 * État de l'écran de réglages (SPEC.md § 5.6).
 *
 * [serverUrl] est vide tant que le dépôt n'a pas émis : l'entrée « Serveur MOTIS » s'affiche alors
 * sans sous-titre plutôt que d'attendre. [search] et [display] partent des valeurs par défaut du
 * domaine, si bien que la première image montre déjà l'état d'un usager qui n'a rien réglé.
 */
data class SettingsUiState(
  val serverUrl: String = "",
  val search: SearchPreferences = SearchPreferences(),
  val display: DisplayPreferences = DisplayPreferences(),
  /** Le dialogue ouvert, ou `null`. Un seul à la fois. */
  val dialog: SettingsDialog? = null,
  /** Le message à afficher une fois, puis à oublier. */
  val message: SettingsMessage? = null,
  /**
   * Faux tant que le jalon 10 n'a pas fourni de dépôt d'historique : l'entrée « Effacer
   * l'historique » n'est pas affichée plutôt que d'être affichée sans effet.
   */
  val canClearHistory: Boolean = false,
)

/** Les dialogues de l'écran. Un choix unique par entrée, sauf les types de véhicules. */
sealed interface SettingsDialog {
  data object PedestrianSpeed : SettingsDialog

  data object PedestrianProfile : SettingsDialog

  data object CyclingSpeed : SettingsDialog

  data object ElevationCosts : SettingsDialog

  data object TransferMargin : SettingsDialog

  data object MaxTransfers : SettingsDialog

  data object RentalFormFactors : SettingsDialog

  data object Theme : SettingsDialog

  data object ClockFormat : SettingsDialog

  /** Confirmation avant un effacement : SPEC.md § 5.6 veut que l'usager sache ce qu'il supprime. */
  data class ConfirmClear(val target: ClearTarget) : SettingsDialog

  data object ConfirmReset : SettingsDialog
}

/** Ce qu'une confirmation d'effacement va supprimer (SPEC.md § 5.6, rubrique « Données »). */
enum class ClearTarget {
  HISTORY,
  TILE_CACHE,
  RESULTS_CACHE,
  GEOCODE_CACHE,
}

/**
 * Retour affiché après une action, traduit en chaîne par l'écran.
 *
 * Le `ViewModel` nomme le message et n'en connaît pas le texte : c'est ce qui lui permet de ne
 * référencer aucune ressource et de rester lisible en JVM.
 */
enum class SettingsMessage {
  HISTORY_CLEARED,
  TILE_CACHE_CLEARED,
  RESULTS_CACHE_CLEARED,
  GEOCODE_CACHE_CLEARED,
  CLEAR_FAILED,
  SETTINGS_RESET,
  SAVE_FAILED,
}
