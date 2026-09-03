package io.github.mgdx.escale.ui.watch

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.ui.settings.uses24HourClock
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * L'écran d'activation d'une surveillance (SPEC.md § 5.5.1).
 *
 * Il porte **l'avertissement que la spec impose** : « L'écran d'activation explique en une phrase
 * que l'application enverra une requête au serveur configuré une heure avant chaque trajet
 * surveillé, et que ces requêtes, à heure fixe, révèlent des habitudes de déplacement. » Cette
 * phrase est affichée avant l'activation, et le reste après : ce n'est pas un avertissement qu'on
 * fait disparaître une fois accepté.
 *
 * C'est aussi ici, et pas avant, que `POST_NOTIFICATIONS` est demandée — au moment où l'usager
 * active sa première surveillance. Son refus n'annule rien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchSheet(state: WatchUiState, actions: WatchActions, onDismiss: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SheetPadding)
        .padding(bottom = SheetPadding),
      verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
      Text(text = stringResource(R.string.watch_sheet_title), style = MaterialTheme.typography.titleLarge)
      Text(
        text = stringResource(R.string.watch_privacy_notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // La seconde chose que l'usager doit savoir avant d'activer : après un redémarrage du
      // téléphone, une occurrence peut être manquée (SPEC.md § 5.5.1 et § 11). Le taire ferait de
      // la surveillance une fonction qui échoue sans le dire.
      Text(
        text = stringResource(R.string.watch_restart_notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      WatchTimeSection(state = state, onTimeChanged = actions.onTimeChanged)
      WatchDaysSection(state = state, onDayToggled = actions.onDayToggled)
      WatchAlertSection(state = state, actions = actions)
      WatchStatusSection(state = state)
      WatchButtons(state = state, actions = actions, onDismiss = onDismiss)
    }
  }
}

/** L'heure de départ habituelle, réglée par le sélecteur d'heure de Material 3. */
@Composable
private fun WatchTimeSection(state: WatchUiState, onTimeChanged: (LocalTime) -> Unit) {
  var picking by rememberSaveable { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
    Text(text = stringResource(R.string.watch_time_label), style = MaterialTheme.typography.titleSmall)
    val description = stringResource(R.string.watch_time_action)
    OutlinedButton(
      onClick = { picking = true },
      modifier = Modifier
        .sizeIn(minHeight = TouchTarget)
        .semantics { contentDescription = description },
    ) {
      Text(text = timeLabel(state.time))
    }
  }
  if (picking) {
    WatchTimeDialog(
      initial = state.time,
      onConfirm = {
        onTimeChanged(it)
        picking = false
      },
      onDismiss = { picking = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchTimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
  val picker = rememberTimePickerState(
    initialHour = initial.hour,
    initialMinute = initial.minute,
    is24Hour = uses24HourClock(),
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) {
        Text(text = stringResource(R.string.watch_time_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.watch_close)) }
    },
    text = { TimePicker(state = picker) },
  )
}

/** Les jours concernés. Aucun jour coché : la surveillance vaut pour une date unique. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchDaysSection(state: WatchUiState, onDayToggled: (DayOfWeek) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
    Text(text = stringResource(R.string.watch_days_label), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
      weekDays().forEach { day ->
        val description = dayDescription(day)
        FilterChip(
          selected = day in state.days,
          onClick = { onDayToggled(day) },
          label = { Text(text = dayLabel(day)) },
          modifier = Modifier
            .sizeIn(minHeight = TouchTarget)
            .semantics { contentDescription = description },
        )
      }
    }
    Text(
      text = if (state.days.isEmpty()) singleDateLabel(state.singleDate) else scheduleLabel(state),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.days.isEmpty()) {
      Text(
        text = stringResource(R.string.watch_repeat_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Le seuil de retard et « me prévenir même si tout va bien » (SPEC.md § 5.5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchAlertSection(state: WatchUiState, actions: WatchActions) {
  Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
    Text(text = stringResource(R.string.watch_threshold_label), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
      WatchAlertSettings.THRESHOLD_CHOICES.forEach { choice ->
        val minutes = choice.toMinutes().toInt()
        FilterChip(
          selected = choice == state.settings.delayThreshold,
          onClick = { actions.onThresholdChanged(choice) },
          label = { Text(text = pluralStringResource(R.plurals.watch_threshold_value, minutes, minutes)) },
          modifier = Modifier.sizeIn(minHeight = TouchTarget),
        )
      }
    }
    WatchSwitchRow(
      label = stringResource(R.string.watch_notify_always),
      checked = state.settings.notifyWhenNothingChanged,
      onCheckedChange = actions.onNotifyAlwaysChanged,
    )
  }
}

/** Ce que l'application a à dire : la limite, les notifications éteintes, la dernière vérification. */
@Composable
private fun WatchStatusSection(state: WatchUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
    if (state.limitReached || state.limitBlocking) {
      Text(
        text = limitMessage(state.watchedCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
    // Refus de POST_NOTIFICATIONS, ou canal éteint depuis les réglages système : la surveillance
    // reste proposée, sans notification (SPEC.md § 5.5.1).
    if (state.watched && !state.notificationsAllowed) {
      Text(
        text = stringResource(R.string.watch_notifications_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (state.watched) {
      Text(
        text = lastCheckLabel(state.lastCheck) ?: stringResource(R.string.watch_last_check_none),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Le bouton d'activation, et la demande de permission qui l'accompagne.
 *
 * `POST_NOTIFICATIONS` n'est demandée qu'ici, à l'activation, et seulement à partir d'Android 13.
 * La surveillance est enregistrée **quelle que soit la réponse** : la spec veut qu'un refus laisse
 * la fonction utilisable, son résultat s'affichant dans l'application.
 */
@Composable
private fun WatchButtons(state: WatchUiState, actions: WatchActions, onDismiss: () -> Unit) {
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    actions.onNotificationPermissionResult()
    actions.onWatchedChanged(true)
  }
  val enable = remember(state.watched, permission) {
    {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        actions.onWatchedChanged(true)
      }
    }
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(RowSpacing, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = TouchTarget)) {
      Text(text = stringResource(R.string.watch_close))
    }
    Button(
      onClick = {
        if (state.watched) actions.onWatchedChanged(false) else enable()
      },
      enabled = state.watched || !state.limitBlocking,
      modifier = Modifier.sizeIn(minHeight = TouchTarget),
    ) {
      Text(text = stringResource(if (state.watched) R.string.watch_stop else R.string.watch_enable))
    }
  }
}

@Composable
private fun WatchSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier
        .sizeIn(minWidth = TouchTarget, minHeight = TouchTarget)
        .semantics { contentDescription = label },
    )
  }
}

/** Cible tactile minimale de SPEC.md § 9. */
private val TouchTarget = 48.dp
private val SheetPadding = 16.dp
private val SectionSpacing = 20.dp
private val RowSpacing = 12.dp
private val ChipSpacing = 8.dp
