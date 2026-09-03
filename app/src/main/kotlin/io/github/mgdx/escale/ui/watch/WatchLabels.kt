package io.github.mgdx.escale.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.ClockTime
import io.github.mgdx.escale.core.model.WatchIssue
import io.github.mgdx.escale.ui.results.rememberTimeFormatter
import io.github.mgdx.escale.ui.settings.uses24HourClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * La mise en mots de l'état d'une surveillance (SPEC.md § 5.5.1).
 *
 * Les noms de jours et les dates ne sont pas des chaînes du projet : `java.time` les rend déjà
 * dans la langue de l'appareil, et les recopier dans `strings_watch.xml` reviendrait à entretenir
 * une traduction que la plateforme fournit — et à se tromper sur le premier jour de la semaine,
 * qui n'est pas le même partout.
 */

/** L'ordre d'affichage des jours, à partir du premier jour de la semaine de la langue courante. */
@Composable
internal fun weekDays(): List<DayOfWeek> {
  val first = WeekFields.of(currentLocale()).firstDayOfWeek
  return (0 until DayOfWeek.entries.size).map { first.plus(it.toLong()) }
}

/** Le nom court d'un jour, pour une puce ; le nom complet sert de description vocale. */
@Composable
internal fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, currentLocale())

@Composable
internal fun dayDescription(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, currentLocale())

/** L'heure de départ habituelle, au format 12 h ou 24 h choisi par l'usager (SPEC.md § 5.6). */
@Composable
internal fun timeLabel(time: LocalTime): String =
  DateTimeFormatter.ofPattern(ClockTime.pattern(uses24HourClock()), currentLocale()).format(time)

/** « Une seule fois, le lundi 7 septembre 2026 » : la date d'une surveillance sans récurrence. */
@Composable
internal fun singleDateLabel(date: LocalDate): String = stringResource(
  R.string.watch_once_on,
  DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale()).format(date),
)

/** Les jours surveillés, ou la date unique, suivis de l'heure. */
@Composable
internal fun scheduleLabel(state: WatchUiState): String {
  val separator = stringResource(R.string.watch_value_separator)
  val days = if (state.days.isEmpty()) {
    singleDateLabel(state.singleDate)
  } else {
    weekDays().filter { it in state.days }.map { dayLabel(it) }.joinToString(separator)
  }
  return stringResource(R.string.watch_summary_format, days, timeLabel(state.time))
}

/** L'état complet montré sur la ligne de l'écran de détail : la programmation, puis le résultat. */
@Composable
internal fun watchedSummary(state: WatchUiState): String {
  val schedule = scheduleLabel(state)
  val last = lastCheckLabel(state.lastCheck)
  return if (last == null) schedule else schedule + LINE_BREAK + last
}

/**
 * Le résultat de la dernière vérification, ou `null` s'il n'y en a pas eu.
 *
 * C'est ce qui rend la fonction utilisable **sans notification**, quand `POST_NOTIFICATIONS` a été
 * refusée : SPEC.md § 5.5.1 veut alors que l'état soit visible à l'ouverture de l'application.
 */
@Composable
internal fun lastCheckLabel(record: WatchCheckRecord?): String? {
  if (record == null) return null
  val formatTime = rememberTimeFormatter()
  return stringResource(R.string.watch_last_check, formatTime(record.checkedAt), issueLabel(record.issue))
}

@Composable
internal fun issueLabel(issue: WatchIssue?): String = when (issue) {
  null, WatchIssue.NOTHING -> stringResource(R.string.watch_status_nothing)
  WatchIssue.DELAYED -> stringResource(R.string.watch_status_delayed)
  WatchIssue.CANCELLED -> stringResource(R.string.watch_status_cancelled)
  WatchIssue.DISRUPTED -> stringResource(R.string.watch_status_disrupted)
  WatchIssue.IMPOSSIBLE -> stringResource(R.string.watch_status_impossible)
}

/** « Vous surveillez déjà cinq trajets » : la limite se dit avant d'être heurtée. */
@Composable
internal fun limitMessage(count: Int): String = pluralStringResource(R.plurals.watch_limit_reached, count, count)

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private const val LINE_BREAK = "\n"
