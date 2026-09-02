package io.github.mgdx.escale.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.settings.uses24HourClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Le sélecteur d'heure de la carte de recherche (SPEC.md § 5.1).
 *
 * Trois choix — « Partir maintenant », « Partir à… », « Arriver avant… », ce dernier étant
 * l'`arriveBy` de l'API — puis, pour les deux derniers, la date et l'heure. L'étape courante vit
 * dans le `ViewModel` : une rotation au milieu du choix ne renvoie pas l'usager à la case départ.
 */
@Composable
fun TimeChoiceDialogs(picker: TimePickerUi, actions: SearchActions) {
  when (picker.step) {
    TimePickerStep.CHOICE -> TimeModeDialog(actions)
    TimePickerStep.DATE -> TimeDateDialog(actions)
    TimePickerStep.TIME -> TimeOfDayDialog(actions)
  }
}

@Composable
private fun TimeModeDialog(actions: SearchActions) {
  AlertDialog(
    onDismissRequest = actions.onDismissTimePicker,
    title = { Text(text = stringResource(R.string.search_time_title)) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TimeOption(R.string.search_time_option_now, actions.onTimeNowSelected)
        TimeOption(R.string.search_time_option_depart) { actions.onTimeModeSelected(TimeMode.DEPART_AT) }
        TimeOption(R.string.search_time_option_arrive) { actions.onTimeModeSelected(TimeMode.ARRIVE_BY) }
      }
    },
    confirmButton = {
      TextButton(onClick = actions.onDismissTimePicker) {
        Text(text = stringResource(R.string.search_action_cancel))
      }
    },
    icon = {
      Icon(
        painter = painterResource(R.drawable.ic_schedule),
        contentDescription = null,
      )
    },
  )
}

@Composable
private fun TimeOption(labelRes: Int, onClick: () -> Unit) {
  val label = stringResource(labelRes)
  Text(
    text = label,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchTarget)
      .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
      .padding(vertical = OptionPadding),
    style = MaterialTheme.typography.bodyLarge,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDateDialog(actions: SearchActions) {
  // Le `DatePicker` raisonne en millisecondes UTC : la date d'aujourd'hui se calcule donc dans le
  // fuseau de l'appareil, puis se convertit à minuit UTC pour être comprise du sélecteur.
  val today = LocalDate.now(ZoneId.systemDefault())
  val state = rememberDatePickerState(
    initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
  )
  DatePickerDialog(
    onDismissRequest = actions.onDismissTimePicker,
    confirmButton = {
      TextButton(
        onClick = { state.selectedDateMillis?.let(actions.onDateSelected) },
        enabled = state.selectedDateMillis != null,
      ) {
        Text(text = stringResource(R.string.search_action_next))
      }
    },
    dismissButton = {
      TextButton(onClick = actions.onDismissTimePicker) {
        Text(text = stringResource(R.string.search_action_cancel))
      }
    },
  ) {
    DatePicker(state = state, title = { DialogTitle(R.string.search_time_pick_date) })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayDialog(actions: SearchActions) {
  val now = LocalTime.now(ZoneId.systemDefault())
  val state = rememberTimePickerState(
    initialHour = now.hour,
    initialMinute = now.minute,
    // Le réglage 12 h / 24 h **de l'application** (SPEC.md § 5.6), celui-là même qui écrit les
    // heures des résultats et le libellé de la ligne d'heure. Ce que l'application affiche et ce
    // qu'elle fait saisir doivent être cohérents : quelqu'un qui lit « 2:30 pm » partout ne doit
    // pas se retrouver devant un cadran sur 24 heures. À défaut de choix explicite, la fonction
    // rend celui du système.
    is24Hour = uses24HourClock(),
  )
  AlertDialog(
    onDismissRequest = actions.onDismissTimePicker,
    title = { DialogTitle(R.string.search_time_pick_time) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TimePicker(state = state)
      }
    },
    confirmButton = {
      TextButton(onClick = { actions.onTimeSelected(state.hour, state.minute) }) {
        Text(text = stringResource(R.string.search_action_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = actions.onDismissTimePicker) {
        Text(text = stringResource(R.string.search_action_cancel))
      }
    },
  )
}

@Composable
private fun DialogTitle(labelRes: Int) {
  Text(
    text = stringResource(labelRes),
    modifier = Modifier.padding(all = OptionPadding),
    style = MaterialTheme.typography.titleMedium,
  )
}

private val MinTouchTarget: Dp = 48.dp
private val OptionPadding: Dp = 12.dp
