package io.github.mgdx.escale.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.format.TimeStatus
import io.github.mgdx.escale.core.format.elevationOf
import io.github.mgdx.escale.core.format.transitLineLabel
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TimeWindow
import io.github.mgdx.escale.core.model.TravelStep
import io.github.mgdx.escale.core.model.travelSteps
import io.github.mgdx.escale.ui.results.delayColor
import io.github.mgdx.escale.ui.results.durationText
import io.github.mgdx.escale.ui.results.modeIcon
import io.github.mgdx.escale.ui.results.modeLabel
import io.github.mgdx.escale.ui.results.rememberTimeFormatter
import java.time.Instant

/**
 * Une portion de trajet, dépliable d'un appui (SPEC.md § 5.3).
 *
 * Repliée, elle dit l'essentiel : le mode, la ligne, la direction, les heures. Dépliée, elle dit
 * tout ce que le serveur a envoyé — arrêts desservis, quai, accessibilité, perturbations, ou
 * instructions pas-à-pas pour un cheminement.
 */
@Composable
internal fun LegCard(index: Int, leg: JourneyLeg, state: DetailUiState, actions: DetailActions) {
  val expanded = index in state.expandedLegs
  val label = stringResource(if (expanded) R.string.detail_leg_collapse else R.string.detail_leg_expand)
  ElevatedCard(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClickLabel = label, role = Role.Button) { actions.onLegToggled(index) },
  ) {
    Column(
      modifier = Modifier.padding(CardPadding),
      verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
      LegHeader(leg = leg, expanded = expanded)
      StatusLines(TimeStatus.of(leg))
      if (expanded) {
        LegBody(index = index, leg = leg, state = state, actions = actions)
        leg.alerts.forEach { AlertBlock(it) }
      } else if (leg.alerts.isNotEmpty()) {
        DetailRow(
          icon = R.drawable.ic_warning,
          text = pluralStringResource(R.plurals.results_disruptions, leg.alerts.size, leg.alerts.size),
        )
      }
    }
  }
}

/** Le mode, la ligne, la direction et les heures : ce qui se lit sans rien déplier. */
@Composable
private fun LegHeader(leg: JourneyLeg, expanded: Boolean) {
  val formatTime = rememberTimeFormatter()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = TouchTarget),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Icon(
      painter = painterResource(if (leg.cancelled) R.drawable.ic_cancel else leg.modeIcon()),
      contentDescription = null,
      modifier = Modifier.size(HeaderIconSize),
      tint = MaterialTheme.colorScheme.primary,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = legTitle(leg), style = MaterialTheme.typography.titleMedium)
      val subtitle = legSubtitle(leg)
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(text = formatTime(leg.startTime), style = MaterialTheme.typography.bodyMedium)
      Text(text = formatTime(leg.endTime), style = MaterialTheme.typography.bodyMedium)
    }
    Icon(
      painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
      contentDescription = null,
      modifier = Modifier.size(RowIconSize),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** « Bus E5 », ou le seul libellé de mode quand le serveur ne nomme pas la ligne (SPEC.md § 9). */
@Composable
private fun legTitle(leg: JourneyLeg): String {
  val mode = stringResource(leg.modeLabel())
  val line = (leg as? JourneyLeg.Transit)?.let(::transitLineLabel) ?: return mode
  return stringResource(R.string.results_leg_detail, mode, line)
}

@Composable
private fun legSubtitle(leg: JourneyLeg): String? {
  if (leg is JourneyLeg.Transit) {
    val headsign = leg.headsign?.takeIf(String::isNotBlank)
    return headsign?.let { stringResource(R.string.detail_direction, it) }
  }
  val duration = durationText(leg.duration)
  val meters = leg.distanceMeters
  return if (meters == null) duration else stringResource(R.string.results_summary, distanceText(meters), duration)
}

@Composable
private fun LegBody(index: Int, leg: JourneyLeg, state: DetailUiState, actions: DetailActions) {
  when (leg) {
    is JourneyLeg.Transit -> TransitBody(index = index, leg = leg, state = state, actions = actions)

    is JourneyLeg.Rental -> {
      RentalBody(leg.rental)
      StepsSection(index = index, steps = leg.travelSteps, state = state, actions = actions)
    }

    else -> StepsSection(index = index, steps = leg.travelSteps, state = state, actions = actions)
  }
}

// --- Transport en commun -----------------------------------------------------------------------

@Composable
private fun TransitBody(index: Int, leg: JourneyLeg.Transit, state: DetailUiState, actions: DetailActions) {
  leg.agencyName?.takeIf(String::isNotBlank)?.let {
    DetailRow(icon = R.drawable.ic_business, text = stringResource(R.string.detail_agency, it))
  }
  PlaceBlock(icon = R.drawable.ic_trip_origin, labelRes = R.string.detail_board, place = leg.from)
  IntermediateStops(index = index, leg = leg, state = state, actions = actions)
  PlaceBlock(icon = R.drawable.ic_place, labelRes = R.string.detail_alight, place = leg.to)
  leg.wheelchairAccessible?.let { DetailRow(icon = it.iconRes(), text = stringResource(it.labelRes())) }
  leg.bikesAllowed?.let {
    DetailRow(
      icon = R.drawable.ic_pedal_bike,
      text = stringResource(if (it) R.string.detail_bikes_allowed else R.string.detail_bikes_not_allowed),
    )
  }
  TripHook(leg = leg, actions = actions)
}

/**
 * **Point d'accroche du jalon 9** : SPEC.md § 5.3 veut qu'un appui sur la ligne ouvre l'écran
 * « détail de la course » (`/api/v6/trip`), avec la desserte complète du véhicule.
 *
 * Cet écran n'existe pas encore, et `TripRepository` n'a pas d'implémentation dans `:data`. La
 * commande n'apparaît donc pas : elle apparaîtra d'elle-même le jour où le lot du jalon 9
 * renseignera `DetailActions.onTripSelected`. Un bouton qui ne mène nulle part serait pire.
 */
@Composable
private fun TripHook(leg: JourneyLeg.Transit, actions: DetailActions) {
  val open = actions.onTripSelected ?: return
  val tripId = leg.tripId?.takeIf(String::isNotBlank) ?: return
  val label = stringResource(R.string.detail_trip_action)
  DetailRow(
    icon = R.drawable.ic_directions_transit,
    text = label,
    modifier = Modifier.clickable(onClickLabel = label, role = Role.Button) { open(tripId) },
  )
}

/** Arrêt de montée ou de descente : nom, heure, horaire théorique s'il diffère, quai (§ 5.3). */
@Composable
private fun PlaceBlock(@DrawableRes icon: Int, @StringRes labelRes: Int, place: Place) {
  val formatTime = rememberTimeFormatter()
  DetailRow(icon = icon, text = stringResource(labelRes, place.name), trailing = formatTime(place.time))
  // L'horaire théorique n'est montré que s'il diffère de l'heure effective : sans temps réel les
  // deux sont égales par construction, et l'afficher ferait croire à une information qu'on n'a pas.
  if (place.scheduledTime != place.time) {
    DetailRow(icon = null, text = stringResource(R.string.detail_scheduled_time, formatTime(place.scheduledTime)))
  }
  place.track?.takeIf(String::isNotBlank)?.let {
    DetailRow(icon = R.drawable.ic_signpost, text = stringResource(R.string.detail_track, it))
  }
}

/**
 * Le nombre d'arrêts intermédiaires, **avec le pluriel de la langue**, et leur liste dépliable
 * (SPEC.md § 5.3).
 *
 * Tant que le détail n'est pas arrivé, une liste vide ne veut pas dire « sans arrêt » mais
 * « on ne sait pas encore » : rien ne s'affiche alors, plutôt qu'une affirmation fausse.
 */
@Composable
private fun IntermediateStops(index: Int, leg: JourneyLeg.Transit, state: DetailUiState, actions: DetailActions) {
  val stops = leg.intermediateStops
  if (stops.isEmpty()) {
    if (state.detailed) DetailRow(icon = null, text = stringResource(R.string.detail_no_intermediate_stop))
    return
  }
  val expanded = index in state.expandedStops
  ToggleRow(
    expanded = expanded,
    text = pluralStringResource(R.plurals.detail_intermediate_stops, stops.size, stops.size),
    labelRes = if (expanded) R.string.detail_hide_stops else R.string.detail_show_stops,
    onClick = { actions.onStopsToggled(index) },
  )
  if (expanded) stops.forEach { StopRow(it) }
}

@Composable
private fun StopRow(visit: StopVisit) {
  val formatTime = rememberTimeFormatter()
  val time = visit.arrival ?: visit.departure
  val name = visit.place.name
  val text = if (visit.cancelled) stringResource(R.string.detail_stop_cancelled) else name
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = NestedIndent),
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Text(
      text = if (visit.cancelled) stringResource(R.string.results_summary, name, text) else name,
      style = MaterialTheme.typography.bodyMedium,
      color = if (visit.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    if (time != null) {
      Text(
        text = formatTime(time),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// --- Libre-service -----------------------------------------------------------------------------

/**
 * Une portion en véhicule partagé (SPEC.md § 5.3).
 *
 * **Point d'accroche du jalon 8** : la disponibilité de la station — « 7 vélos disponibles ·
 * 4 places libres à l'arrivée », par `GET /api/v1/rentals` — s'insère juste après la station de
 * prise et la station de retour. Elle n'est pas demandée ici : `RentalsRepository` est déclaré
 * dans `:core` et n'a pas encore d'implémentation, et SPEC.md § 7 interdit d'émettre une requête
 * dont on ne sait pas encore afficher la réponse ni la fraîcheur.
 */
@Composable
private fun RentalBody(rental: RentalInfo?) {
  if (rental == null) return
  rental.systemName?.takeIf(String::isNotBlank)?.let {
    DetailRow(icon = R.drawable.ic_business, text = stringResource(R.string.results_rental_operator, it))
  }
  rental.propulsionType?.let { DetailRow(icon = null, text = stringResource(it.labelRes())) }
  val pickup = rental.fromStationName
  val dropoff = rental.toStationName
  if (pickup == null && dropoff == null) {
    DetailRow(icon = R.drawable.ic_near_me, text = stringResource(R.string.detail_rental_free_floating))
  }
  pickup?.let { DetailRow(icon = R.drawable.ic_trip_origin, text = stringResource(R.string.detail_rental_pickup, it)) }
  dropoff?.let { DetailRow(icon = R.drawable.ic_place, text = stringResource(R.string.detail_rental_dropoff, it)) }
  DetailRow(icon = R.drawable.ic_info, text = stringResource(rental.returnConstraint.labelRes()))
  OperatorLink(rental)
}

/** Le lien profond vers l'application de l'exploitant : intention externe, jamais de WebView (§ 2). */
@Composable
private fun OperatorLink(rental: RentalInfo) {
  val uri = rental.rentalUriAndroid ?: return
  val uriHandler = LocalUriHandler.current
  val name = rental.systemName?.takeIf(String::isNotBlank)
  val label = if (name == null) {
    stringResource(R.string.detail_rental_open_app_generic)
  } else {
    stringResource(R.string.detail_rental_open_app, name)
  }
  // `runCatching` : un appareil sans application capable d'ouvrir ce lien ne doit pas tomber.
  TextButton(onClick = { runCatching { uriHandler.openUri(uri) } }) {
    Icon(
      painter = painterResource(R.drawable.ic_open_in_new),
      contentDescription = null,
      modifier = Modifier.size(RowIconSize),
    )
    Text(text = label, modifier = Modifier.padding(start = RowSpacing))
  }
}

// --- Cheminements ------------------------------------------------------------------------------

/**
 * Le dénivelé et les instructions pas-à-pas d'un cheminement (SPEC.md § 5.3).
 *
 * Les manœuvres ne sont **jamais dépliées par défaut** : quarante lignes d'instructions au milieu
 * d'un trajet en noieraient la lecture.
 */
@Composable
private fun StepsSection(index: Int, steps: List<TravelStep>, state: DetailUiState, actions: DetailActions) {
  elevationOf(steps)?.takeIf { it.hasRelief }?.let {
    DetailRow(
      icon = R.drawable.ic_terrain,
      text = stringResource(R.string.detail_elevation, it.upMeters, it.downMeters),
    )
  }
  if (steps.isEmpty()) {
    if (state.detailed) DetailRow(icon = null, text = stringResource(R.string.detail_no_steps))
    return
  }
  val expanded = index in state.expandedSteps
  ToggleRow(
    expanded = expanded,
    text = stringResource(if (expanded) R.string.detail_hide_steps else R.string.detail_show_steps),
    labelRes = if (expanded) R.string.detail_hide_steps else R.string.detail_show_steps,
    onClick = { actions.onStepsToggled(index) },
  )
  if (expanded) steps.forEach { StepRow(it) }
}

@Composable
private fun StepRow(step: TravelStep) {
  val maneuver = stringResource(step.direction.labelRes())
  val street = step.streetName.takeIf(String::isNotBlank)
  val text = if (street == null) maneuver else stringResource(R.string.detail_step_named, maneuver, street)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = NestedIndent),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Icon(
      painter = painterResource(step.direction.iconRes()),
      contentDescription = null,
      modifier = Modifier
        .size(RowIconSize)
        // Un « à droite » est le tracé « à gauche » vu dans un miroir : un seul fichier suffit.
        .scale(scaleX = if (step.direction.isMirrored()) -1f else 1f, scaleY = 1f),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = distanceText(step.distanceMeters),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// --- Perturbations -----------------------------------------------------------------------------

/**
 * Une perturbation : titre, description, gravité, période, lien (SPEC.md § 5.3).
 *
 * La gravité est **écrite**, jamais seulement colorée : SPEC.md § 9 interdit qu'une information ne
 * tienne qu'à la couleur.
 */
@Composable
private fun AlertBlock(alert: Disruption) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shape = RoundedCornerShape(AlertCorner),
  ) {
    Column(
      modifier = Modifier.padding(CardPadding),
      verticalArrangement = Arrangement.spacedBy(AlertSpacing),
    ) {
      DetailRow(icon = R.drawable.ic_warning, text = stringResource(alert.severity.labelRes()))
      if (alert.headerText.isNotBlank()) {
        Text(text = alert.headerText, style = MaterialTheme.typography.titleSmall)
      }
      if (alert.descriptionText.isNotBlank()) {
        Text(text = alert.descriptionText, style = MaterialTheme.typography.bodyMedium)
      }
      alert.periods.forEach { period -> PeriodText(period) }
      AlertLink(alert.url)
    }
  }
}

@Composable
private fun PeriodText(period: TimeWindow) {
  val start = period.start
  val end = period.end
  val text = when {
    start != null && end != null ->
      stringResource(R.string.detail_alert_period, dateTimeText(start), dateTimeText(end))

    start != null -> stringResource(R.string.detail_alert_period_from, dateTimeText(start))

    end != null -> stringResource(R.string.detail_alert_period_until, dateTimeText(end))

    else -> return
  }
  Text(text = text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AlertLink(url: String?) {
  if (url.isNullOrBlank()) return
  val uriHandler = LocalUriHandler.current
  TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
    Icon(
      painter = painterResource(R.drawable.ic_open_in_new),
      contentDescription = null,
      modifier = Modifier.size(RowIconSize),
    )
    Text(text = stringResource(R.string.detail_alert_link), modifier = Modifier.padding(start = RowSpacing))
  }
}

// --- Briques communes --------------------------------------------------------------------------

/**
 * Les mentions de temps réel et de suppression d'une portion (SPEC.md § 5.2).
 *
 * **Une portion sans donnée temps réel n'en produit aucune** : ni couleur, ni « à l'heure ». C'est
 * `TimeStatus`, dans `:core`, qui tranche.
 */
@Composable
internal fun StatusLines(status: TimeStatus) {
  if (status.cancelled) {
    DetailRow(
      icon = R.drawable.ic_cancel,
      text = stringResource(R.string.results_cancelled),
      color = MaterialTheme.colorScheme.error,
    )
    return
  }
  val delay = status.departure ?: status.arrival ?: return
  val amount = durationText(delay.formatted)
  val text = when (delay.quality) {
    DelayQuality.ON_TIME -> stringResource(R.string.results_delay_on_time)
    DelayQuality.EARLY -> stringResource(R.string.results_delay_early, amount)
    DelayQuality.SLIGHT, DelayQuality.SEVERE -> stringResource(R.string.results_delay_late, amount)
  }
  DetailRow(
    icon = if (delay.quality == DelayQuality.ON_TIME) R.drawable.ic_check_circle else R.drawable.ic_schedule,
    text = text,
    color = delayColor(delay.quality),
  )
}

/**
 * Une ligne d'information : un pictogramme facultatif, un texte, une valeur alignée à droite.
 *
 * Le pictogramme est décoratif parce que le texte dit exactement la même chose : c'est ce que
 * demande SPEC.md § 9.
 */
@Composable
internal fun DetailRow(
  @DrawableRes icon: Int?,
  text: String,
  modifier: Modifier = Modifier,
  trailing: String? = null,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = RowHeight),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (icon != null) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(RowIconSize),
        tint = color,
      )
    } else {
      // Une ligne sans pictogramme reste alignée avec celles qui en ont un.
      Spacer(modifier = Modifier.size(RowIconSize))
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    if (trailing != null) {
      Text(text = trailing, style = MaterialTheme.typography.bodyMedium, color = color)
    }
  }
}

/** Une bascule de dépliage : toute la ligne fait 48 dp, la cible tactile de SPEC.md § 9. */
@Composable
private fun ToggleRow(expanded: Boolean, text: String, @StringRes labelRes: Int, onClick: () -> Unit) {
  val label = stringResource(labelRes)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = TouchTarget)
      .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Icon(
      painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
      contentDescription = null,
      modifier = Modifier.size(RowIconSize),
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
  }
}

private val HeaderIconSize: Dp = 24.dp
private val RowHeight: Dp = 24.dp
private val TouchTarget: Dp = 48.dp
private val NestedIndent: Dp = 28.dp
private val AlertCorner: Dp = 12.dp
private val AlertSpacing: Dp = 4.dp
