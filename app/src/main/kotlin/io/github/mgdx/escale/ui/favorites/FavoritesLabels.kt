package io.github.mgdx.escale.ui.favorites

import androidx.annotation.StringRes
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.search.SavedPlaceKind

/**
 * Les libellés de l'écran des favoris, en un seul endroit.
 *
 * Chaque `when` est exhaustif : ajouter une cible de choix ou un message casse la compilation ici,
 * au lieu de produire une ligne vide à l'écran.
 */

@StringRes
internal fun FavoritesMessage.textRes(): Int = when (this) {
  FavoritesMessage.SAVED -> R.string.favorites_saved
  FavoritesMessage.DELETED -> R.string.favorites_deleted
  FavoritesMessage.HISTORY_CLEARED -> R.string.favorites_history_cleared
  FavoritesMessage.FAILED -> R.string.favorites_failed
}

/** Le titre du choix de lieu : il dit ce qu'on est en train de renseigner. */
@StringRes
internal fun PickerTarget.titleRes(): Int = when (this) {
  is PickerTarget.Named -> when (kind) {
    SavedPlaceKind.HOME -> R.string.favorites_picker_home
    SavedPlaceKind.WORK -> R.string.favorites_picker_work
  }

  PickerTarget.Place -> R.string.favorites_picker_place

  PickerTarget.Stop -> R.string.favorites_picker_stop
}

/** Ce que le champ de recherche attend : un arrêt, ou n'importe quel lieu. */
@StringRes
internal fun PickerTarget.hintRes(): Int = when (this) {
  PickerTarget.Stop -> R.string.favorites_picker_stop_hint
  else -> R.string.favorites_picker_hint
}
