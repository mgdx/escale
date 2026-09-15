package io.github.mgdx.escale.follow

import android.content.Context
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.follow.FollowAlert
import io.github.mgdx.escale.core.follow.FollowLeg
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.core.follow.StreetKind
import io.github.mgdx.escale.core.format.FormattedDuration
import io.github.mgdx.escale.ui.results.labelRes
import java.time.Instant

/** Ce qu'une notification ou le bandeau de l'écran de détail affiche : un titre, et une ligne en dessous. */
data class FollowText(val title: String, val text: String?)

/**
 * Met en mots un état ou une alerte du suivi (SPEC.md § 5.3.1).
 *
 * C'est la seule traduction du domaine en chaînes de ce paquet : la notification permanente, les
 * alertes et le bandeau de l'écran de détail disent tous la même chose parce qu'ils passent tous
 * par ici. Rien n'y est décidé, `FollowTimeline` a déjà tout tranché dans `:core`.
 *
 * @param formatTime l'heure au format choisi par l'usager (SPEC.md § 5.6), fourni par l'appelant :
 *   le service la lit dans les préférences, l'écran dans `rememberTimeFormatter`.
 */
class FollowWording(private val context: Context, private val formatTime: (Instant) -> String) {

  fun of(state: FollowState): FollowText = when (state) {
    is FollowState.Waiting -> waiting(state)
    is FollowState.OnBoard -> onBoard(state)
    is FollowState.Connecting -> connecting(state)
    FollowState.Arrived -> FollowText(title = context.getString(R.string.follow_arrived_title), text = null)
  }

  fun of(alert: FollowAlert): FollowText = when (alert) {
    is FollowAlert.StopsBefore -> FollowText(
      title = context.resources.getQuantityString(R.plurals.follow_alert_stops_before, alert.stops, alert.stops),
      text = name(alert.stopName),
    )

    is FollowAlert.NextStop -> FollowText(
      title = context.getString(R.string.follow_alert_next_stop),
      text = name(alert.stopName),
    )

    is FollowAlert.Alight -> FollowText(
      title = context.getString(R.string.follow_alert_alight, name(alert.stopName)),
      text = connecting(alert.then).joined(),
    )

    is FollowAlert.Arrived -> FollowText(
      title = context.getString(R.string.follow_arrived_title),
      text = name(alert.destinationName),
    )

    FollowAlert.LegCancelled -> FollowText(
      title = context.getString(R.string.follow_alert_cancelled_title),
      text = context.getString(R.string.follow_alert_cancelled_text),
    )
  }

  private fun waiting(state: FollowState.Waiting): FollowText {
    val first = state.first
    return when (first) {
      is FollowLeg.Transit -> FollowText(
        title = context.getString(R.string.follow_waiting_title, formatTime(first.start), name(first.fromName)),
        text = joinWithPlatform(line(first), first.track),
      )

      is FollowLeg.Street -> FollowText(
        title = street(first),
        text = state.firstTransit?.let(::lineAt),
      )
    }
  }

  private fun onBoard(state: FollowState.OnBoard): FollowText {
    val alight = name(state.leg.toName)
    val time = formatTime(state.leg.end)
    return FollowText(
      // À bord, la direction ne sert plus : le titre reste court pour tenir sur une ligne de
      // notification, là où le prochain arrêt est ce qu'il faut lire d'un coup d'œil.
      title = context.getString(R.string.follow_onboard_title, lineName(state.leg), name(state.nextStopName)),
      text = if (state.stopsRemaining <= 1) {
        context.getString(R.string.follow_onboard_next, alight, time)
      } else {
        context.resources.getQuantityString(
          R.plurals.follow_onboard_stops,
          state.stopsRemaining,
          alight,
          state.stopsRemaining,
          time,
        )
      },
    )
  }

  private fun connecting(state: FollowState.Connecting): FollowText {
    val street = state.street
    val next = state.nextTransit
    return when {
      street != null -> FollowText(title = street(street), text = next?.let(::lineAt))

      next != null -> FollowText(
        title = context.getString(R.string.follow_transfer_title, line(next)),
        text = joinWithPlatform(context.getString(R.string.follow_transfer_text, formatTime(next.start)), next.track),
      )

      else -> FollowText(title = context.getString(R.string.follow_arrived_title), text = null)
    }
  }

  /** « Métro 4 direction Porte d'Orléans » : le mode, la ligne si elle est nommée, la girouette si elle l'est. */
  private fun line(leg: FollowLeg.Transit): String {
    val named = lineName(leg)
    val headsign = leg.headsign ?: return named
    return context.getString(R.string.follow_line_towards, named, headsign)
  }

  /** « Métro 4 », ou le seul mode quand le serveur ne nomme pas la ligne. */
  private fun lineName(leg: FollowLeg.Transit): String {
    val mode = context.getString(leg.mode.labelRes())
    return leg.lineLabel?.let { context.getString(R.string.results_leg_detail, mode, it) } ?: mode
  }

  /** « Métro 6 direction Nation à 12:41 · Quai 2 » */
  private fun lineAt(leg: FollowLeg.Transit): String =
    joinWithPlatform(context.getString(R.string.follow_line_at, line(leg), formatTime(leg.start)), leg.track)

  /** « Marcher 4 min jusqu'à Montparnasse » */
  private fun street(leg: FollowLeg.Street): String {
    val duration = duration(FormattedDuration.of(leg.duration))
    val destination = name(leg.toName)
    val res = when (leg.kind) {
      StreetKind.WALK -> R.string.follow_street_walk
      StreetKind.BIKE -> R.string.follow_street_bike
      StreetKind.CAR -> R.string.follow_street_car
      StreetKind.RENTAL -> R.string.follow_street_rental
    }
    return context.getString(res, duration, destination)
  }

  private fun joinWithPlatform(text: String, track: String?): String {
    val platform = track?.let { context.getString(R.string.detail_track, it) } ?: return text
    return context.getString(R.string.results_summary, text, platform)
  }

  private fun FollowText.joined(): String =
    if (text == null) title else context.getString(R.string.results_summary, title, text)

  /** Le même découpage que `durationText`, hors composition : la langue vient des mêmes chaînes. */
  private fun duration(value: FormattedDuration): String = when {
    value.isZero -> context.getString(R.string.results_duration_under_minute)
    value.hours == 0L -> context.getString(R.string.results_duration_minutes, value.minutes)
    value.minutes == 0L -> context.getString(R.string.results_duration_hours, value.hours)
    else -> context.getString(R.string.results_duration_hours_minutes, value.hours, value.minutes)
  }

  /** Un lieu sans nom se dit, plutôt que de laisser un blanc au milieu d'une phrase. */
  private fun name(raw: String): String = raw.ifBlank { context.getString(R.string.detail_place_unnamed) }
}
