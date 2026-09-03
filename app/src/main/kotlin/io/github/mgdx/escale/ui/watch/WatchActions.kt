package io.github.mgdx.escale.ui.watch

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * Les gestes de l'écran de configuration, regroupés pour que les composables ne connaissent pas le
 * `ViewModel` (docs/architecture.md § 8).
 */
internal data class WatchActions(
  val onWatchedChanged: (Boolean) -> Unit,
  val onTimeChanged: (LocalTime) -> Unit,
  val onDayToggled: (DayOfWeek) -> Unit,
  val onThresholdChanged: (Duration) -> Unit,
  val onNotifyAlwaysChanged: (Boolean) -> Unit,
  val onNotificationPermissionResult: () -> Unit,
) {
  companion object {
    fun of(viewModel: WatchViewModel) = WatchActions(
      onWatchedChanged = viewModel::onWatchedChanged,
      onTimeChanged = viewModel::onTimeChanged,
      onDayToggled = viewModel::onDayToggled,
      onThresholdChanged = viewModel::onThresholdChanged,
      onNotifyAlwaysChanged = viewModel::onNotifyAlwaysChanged,
      onNotificationPermissionResult = viewModel::onNotificationPermissionResult,
    )
  }
}
