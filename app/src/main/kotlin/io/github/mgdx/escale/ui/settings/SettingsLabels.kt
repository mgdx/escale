package io.github.mgdx.escale.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.ClockFormat
import io.github.mgdx.escale.core.model.CyclingSpeedOption
import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.PedestrianSpeedOption
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalFormFactorSelection
import io.github.mgdx.escale.core.model.ThemeChoice
import java.time.Duration

/**
 * Les libellés des valeurs de réglage, en un seul endroit.
 *
 * Chaque `when` est exhaustif sur son énumération : ajouter une valeur au domaine casse la
 * compilation ici, au lieu de produire une ligne vide à l'écran.
 */

@StringRes
internal fun PedestrianSpeedOption.labelRes(): Int = when (this) {
  PedestrianSpeedOption.VERY_SLOW -> R.string.settings_walking_speed_very_slow
  PedestrianSpeedOption.SLOW -> R.string.settings_walking_speed_slow
  PedestrianSpeedOption.NORMAL -> R.string.settings_walking_speed_normal
  PedestrianSpeedOption.FAST -> R.string.settings_walking_speed_fast
  PedestrianSpeedOption.VERY_FAST -> R.string.settings_walking_speed_very_fast
}

@StringRes
internal fun CyclingSpeedOption.labelRes(): Int = when (this) {
  CyclingSpeedOption.VERY_SLOW -> R.string.settings_cycling_speed_very_slow
  CyclingSpeedOption.SLOW -> R.string.settings_cycling_speed_slow
  CyclingSpeedOption.NORMAL -> R.string.settings_cycling_speed_normal
  CyclingSpeedOption.FAST -> R.string.settings_cycling_speed_fast
  CyclingSpeedOption.VERY_FAST -> R.string.settings_cycling_speed_very_fast
}

@StringRes
internal fun PedestrianProfile.labelRes(): Int = when (this) {
  PedestrianProfile.FOOT -> R.string.settings_pedestrian_profile_foot
  PedestrianProfile.WHEELCHAIR -> R.string.settings_pedestrian_profile_wheelchair
}

@StringRes
internal fun ElevationCosts.labelRes(): Int = when (this) {
  ElevationCosts.NONE -> R.string.settings_elevation_costs_none
  ElevationCosts.LOW -> R.string.settings_elevation_costs_low
  ElevationCosts.HIGH -> R.string.settings_elevation_costs_high
}

@StringRes
internal fun ThemeChoice.labelRes(): Int = when (this) {
  ThemeChoice.SYSTEM -> R.string.settings_theme_system
  ThemeChoice.LIGHT -> R.string.settings_theme_light
  ThemeChoice.DARK -> R.string.settings_theme_dark
}

@StringRes
internal fun ClockFormat.labelRes(): Int = when (this) {
  ClockFormat.SYSTEM -> R.string.settings_clock_format_system
  ClockFormat.HOURS_12 -> R.string.settings_clock_format_12
  ClockFormat.HOURS_24 -> R.string.settings_clock_format_24
}

@StringRes
internal fun RentalFormFactor.labelRes(): Int = when (this) {
  RentalFormFactor.BICYCLE -> R.string.settings_rental_bicycle
  RentalFormFactor.CARGO_BICYCLE -> R.string.settings_rental_cargo_bicycle
  RentalFormFactor.CAR -> R.string.settings_rental_car
  RentalFormFactor.MOPED -> R.string.settings_rental_moped
  RentalFormFactor.SCOOTER_STANDING -> R.string.settings_rental_scooter_standing
  RentalFormFactor.SCOOTER_SEATED -> R.string.settings_rental_scooter_seated
  RentalFormFactor.OTHER -> R.string.settings_rental_other
}

/** « Aucune marge » plutôt que « 0 minute » : une durée nulle se dit, elle ne se compte pas. */
@Composable
internal fun transferMarginLabel(margin: Duration): String = if (margin.isZero) {
  stringResource(R.string.settings_transfer_margin_none)
} else {
  val minutes = margin.toMinutes().toInt()
  pluralStringResource(R.plurals.settings_transfer_margin_minutes, minutes, minutes)
}

/** `null` est le choix « sans limite », et zéro celui des trajets sans correspondance. */
@Composable
internal fun maxTransfersLabel(maxTransfers: Int?): String = when (maxTransfers) {
  null -> stringResource(R.string.settings_max_transfers_unlimited)
  0 -> stringResource(R.string.settings_max_transfers_none)
  else -> pluralStringResource(R.plurals.settings_max_transfers_count, maxTransfers, maxTransfers)
}

/**
 * Ce que le réglage des types de véhicules affiche en valeur : « tous acceptés » quand rien n'est
 * filtré — car un ensemble vide signifie « aucun filtre » et non « aucun véhicule » (SPEC.md § 5.2).
 */
@Composable
internal fun rentalFormFactorsLabel(allowed: Set<RentalFormFactor>): String {
  val selected = RentalFormFactorSelection.selected(allowed)
  return if (selected.size == RentalFormFactorSelection.OFFERED.size) {
    stringResource(R.string.settings_rental_form_factors_all)
  } else {
    val names = selected
      .sortedBy { RentalFormFactorSelection.OFFERED.indexOf(it) }
      .map { stringResource(it.labelRes()) }
    names.joinToString(separator = stringResource(R.string.settings_value_separator))
  }
}

@StringRes
internal fun SettingsMessage.textRes(): Int = when (this) {
  SettingsMessage.HISTORY_CLEARED -> R.string.settings_cleared_history
  SettingsMessage.TILE_CACHE_CLEARED -> R.string.settings_cleared_tile_cache
  SettingsMessage.RESULTS_CACHE_CLEARED -> R.string.settings_cleared_results_cache
  SettingsMessage.GEOCODE_CACHE_CLEARED -> R.string.settings_cleared_geocode_cache
  SettingsMessage.CLEAR_FAILED -> R.string.settings_clear_failed
  SettingsMessage.SETTINGS_RESET -> R.string.settings_reset_done
  SettingsMessage.SAVE_FAILED -> R.string.settings_save_failed
}

@StringRes
internal fun ClearTarget.titleRes(): Int = when (this) {
  ClearTarget.HISTORY -> R.string.settings_clear_history_title
  ClearTarget.TILE_CACHE -> R.string.settings_clear_tile_cache_title
  ClearTarget.RESULTS_CACHE -> R.string.settings_clear_results_cache_title
  ClearTarget.GEOCODE_CACHE -> R.string.settings_clear_geocode_cache_title
}

/** Ce que la confirmation annonce : chaque effacement dit précisément ce qu'il supprime. */
@StringRes
internal fun ClearTarget.confirmationRes(): Int = when (this) {
  ClearTarget.HISTORY -> R.string.settings_clear_history_confirmation
  ClearTarget.TILE_CACHE -> R.string.settings_clear_tile_cache_confirmation
  ClearTarget.RESULTS_CACHE -> R.string.settings_clear_results_cache_confirmation
  ClearTarget.GEOCODE_CACHE -> R.string.settings_clear_geocode_cache_confirmation
}
