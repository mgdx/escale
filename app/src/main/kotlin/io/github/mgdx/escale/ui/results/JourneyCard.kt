package io.github.mgdx.escale.ui.results

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.format.JourneyTimeline
import io.github.mgdx.escale.core.format.TimeStatus
import io.github.mgdx.escale.core.format.TimelineSegment
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg

/**
 * Une carte de résultat : un trajet proposé (SPEC.md § 5.2).
 *
 * Tout ce qui est porté par une couleur est aussi porté par un texte et une icône — retard,
 * suppression, perturbation (SPEC.md § 9). Rien n'est tronqué à 200 % d'agrandissement : la frise
 * est un graphique de hauteur fixe, et l'information qu'elle porte est reprise juste en dessous,
 * en toutes lettres, sur autant de lignes qu'il faut.
 */
@Composable
internal fun JourneyCard(journey: Journey, isSelected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
  val status = TimeStatus.of(journey)
  val legend = legendOf(journey)
  val legendDescription = stringResource(R.string.results_timeline_description, legend)
  val selectLabel = stringResource(R.string.results_select_journey)
  ElevatedCard(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = selectLabel, onClick = onSelect)
      // Le trajet mis en évidence se distingue par un liseré **et** par la mention en toutes
      // lettres qui ferme la carte : jamais par la seule couleur (SPEC.md § 9).
      .then(
        if (isSelected) {
          Modifier.border(SelectedBorder, MaterialTheme.colorScheme.primary, CardDefaults.elevatedShape)
        } else {
          Modifier
        },
      )
      .semantics { selected = isSelected },
    colors = if (isSelected) {
      CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
      CardDefaults.elevatedCardColors()
    },
  ) {
    Column(
      modifier = Modifier.padding(CardPadding),
      verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
      JourneyHeader(journey)
      JourneyTimelineRow(JourneyTimeline.of(journey))
      Text(
        text = legend,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Le lecteur d'écran annonce « Portions : … » : la frise juste au-dessus, elle, est muette,
        // pour ne pas dire deux fois la même chose.
        modifier = Modifier.semantics { contentDescription = legendDescription },
      )
      JourneyStatusLines(journey, status)
      if (isSelected) {
        StatusLine(
          icon = R.drawable.ic_map,
          text = stringResource(R.string.results_journey_selected),
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

/** Heures de départ et d'arrivée, durée totale, nombre de correspondances (SPEC.md § 5.2). */
@Composable
private fun JourneyHeader(journey: Journey) {
  val formatTime = rememberTimeFormatter()
  val departure = formatTime(journey.startTime)
  val arrival = formatTime(journey.endTime)
  val transfers = if (journey.transfers <= 0) {
    stringResource(R.string.results_no_transfer)
  } else {
    pluralStringResource(R.plurals.results_transfers, journey.transfers, journey.transfers)
  }
  val timesDescription = stringResource(R.string.results_time_range_description, departure, arrival)
  Column(verticalArrangement = Arrangement.spacedBy(HeaderSpacing)) {
    Text(
      text = stringResource(R.string.results_time_range, departure, arrival),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { contentDescription = timesDescription },
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(StatusSpacing),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_transfer_within_a_station),
        contentDescription = null,
        modifier = Modifier.size(StatusIconSize),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(R.string.results_summary, durationText(journey.duration), transfers),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * La frise horizontale des portions, **à l'échelle de leur durée** (SPEC.md § 5.2).
 *
 * Elle est décorative au sens des lecteurs d'écran : la même information est juste en dessous, en
 * toutes lettres. La dire deux fois ferait perdre du temps à qui écoute.
 */
@Composable
private fun JourneyTimelineRow(segments: List<TimelineSegment>) {
  // La frise est un graphique, mais elle porte du texte : sa hauteur suit donc l'agrandissement des
  // polices, plafonné à 200 %, sans quoi le nom de ligne y serait à l'étroit (SPEC.md § 9).
  val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TimelineHeight * scale)
      .clearAndSetSemantics { },
    horizontalArrangement = Arrangement.spacedBy(TimelineGap),
  ) {
    segments.forEach { segment -> TimelineSegmentBox(segment) }
  }
}

@Composable
private fun RowScope.TimelineSegmentBox(segment: TimelineSegment) {
  val leg = segment.leg
  val background = segmentColor(leg)
  val content = segmentContentColor(leg)
  Box(
    modifier = Modifier
      .weight(segment.weight)
      .fillMaxHeight()
      .clip(RoundedCornerShape(SegmentCorner))
      .background(background)
      .padding(horizontal = SegmentPadding),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
      // Sous une certaine largeur, un pictogramme n'est plus qu'une tache : le segment reste alors
      // une simple barre de couleur, et le libellé sous la frise dit ce qu'elle est.
      if (segment.weight >= MIN_WEIGHT_FOR_ICON) {
        Icon(
          painter = painterResource(if (leg.cancelled) R.drawable.ic_cancel else leg.modeIcon()),
          contentDescription = null,
          modifier = Modifier.size(SegmentIconSize),
          tint = content,
        )
      }
      // Le nom de ligne dans la frise, quand le segment est assez large pour l'accueillir. Il est
      // coupé plutôt que renvoyé à la ligne : ce n'est qu'un rappel visuel, la même information est
      // écrite en entier juste en dessous, et c'est elle que lisent les lecteurs d'écran.
      val line = (leg as? JourneyLeg.Transit)?.lineLabel()
      if (line != null && segment.weight >= MIN_WEIGHT_FOR_LINE) {
        Text(
          text = line,
          style = MaterialTheme.typography.labelMedium,
          color = content,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Clip,
        )
      }
    }
  }
}

/** Le libellé de ligne arbitré par le serveur : le numéro court s'il existe, sinon `displayName`. */
private fun JourneyLeg.Transit.lineLabel(): String? =
  (routeShortName?.takeIf(String::isNotBlank) ?: lineName).takeIf(String::isNotBlank)

/** La couleur d'une portion : celle de la ligne quand le serveur la publie, sinon celle du thème. */
@Composable
private fun segmentColor(leg: JourneyLeg): Color = when {
  leg.cancelled -> MaterialTheme.colorScheme.errorContainer
  leg is JourneyLeg.Transit -> routeColor(leg.routeColor) ?: MaterialTheme.colorScheme.primaryContainer
  leg is JourneyLeg.Rental -> routeColor(leg.rental?.color) ?: MaterialTheme.colorScheme.tertiaryContainer
  else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun segmentContentColor(leg: JourneyLeg): Color = when {
  leg.cancelled -> MaterialTheme.colorScheme.onErrorContainer

  leg is JourneyLeg.Transit ->
    onRouteColor(leg.routeTextColor, leg.routeColor, MaterialTheme.colorScheme.onPrimaryContainer)

  leg is JourneyLeg.Rental ->
    onRouteColor(
      textColor = null,
      background = leg.rental?.color,
      fallback = MaterialTheme.colorScheme.onTertiaryContainer,
    )

  else -> MaterialTheme.colorScheme.onSecondaryContainer
}

/**
 * Le résumé textuel de la frise : « Marche 6 min › Bus 21 › Métro 4 ».
 *
 * C'est lui qui double les pictogrammes de mode, comme l'exige SPEC.md § 9.
 */
@Composable
private fun legendOf(journey: Journey): String {
  val separator = stringResource(R.string.results_leg_separator)
  // `map` est une fonction inline : l'appel composable y est légal, ce qu'un `joinToString` avec
  // lambda ne permet pas.
  return journey.legs.map { legLabel(it) }.joinToString(separator)
}

@Composable
private fun legLabel(leg: JourneyLeg): String {
  val mode = stringResource(leg.modeLabel())
  val detail = (leg as? JourneyLeg.Transit)?.lineLabel() ?: durationText(leg.duration)
  return stringResource(R.string.results_leg_detail, mode, detail)
}

/**
 * Les mentions de temps réel, de suppression et de perturbation (SPEC.md § 5.2).
 *
 * **Une portion sans donnée temps réel n'en produit aucune** : ni couleur, ni « à l'heure ». C'est
 * `TimeStatus`, dans `:core`, qui tranche, et il rend `null` plutôt qu'un écart nul.
 */
@Composable
private fun JourneyStatusLines(journey: Journey, status: TimeStatus) {
  if (status.cancelled) {
    StatusLine(
      icon = R.drawable.ic_cancel,
      text = stringResource(R.string.results_cancelled),
      color = MaterialTheme.colorScheme.error,
    )
  } else {
    val delay = status.departure ?: status.arrival
    if (delay != null) {
      val amount = durationText(delay.formatted)
      // Le libellé suit la **qualification** de l'écart, pas sa valeur arrondie : à quarante-cinq
      // secondes de retard, `:core` dit « à l'heure », et l'affichage doit dire la même chose.
      val text = when (delay.quality) {
        DelayQuality.ON_TIME -> stringResource(R.string.results_delay_on_time)
        DelayQuality.EARLY -> stringResource(R.string.results_delay_early, amount)
        DelayQuality.SLIGHT, DelayQuality.SEVERE -> stringResource(R.string.results_delay_late, amount)
      }
      StatusLine(
        icon = if (delay.quality == DelayQuality.ON_TIME) R.drawable.ic_check_circle else R.drawable.ic_schedule,
        text = text,
        color = delayColor(delay.quality),
      )
    }
  }
  val alerts = journey.alerts
  if (alerts.isNotEmpty()) {
    StatusLine(
      icon = R.drawable.ic_warning,
      text = pluralStringResource(R.plurals.results_disruptions, alerts.size, alerts.size),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  RentalLine(journey)
}

/** L'exploitant du véhicule partagé, nommé en clair (SPEC.md § 5.2). */
@Composable
private fun RentalLine(journey: Journey) {
  val leg = journey.legs.filterIsInstance<JourneyLeg.Rental>().firstOrNull { it.rental?.systemName != null }
  val name = leg?.rental?.systemName?.takeIf(String::isNotBlank) ?: return
  StatusLine(
    icon = leg.modeIcon(),
    text = stringResource(R.string.results_rental_operator, name),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * Une ligne d'état : une icône, un texte, une couleur — dans cet ordre d'importance.
 *
 * L'icône est décorative parce que le texte à côté dit exactement la même chose ; c'est justement
 * ce qui satisfait SPEC.md § 9.
 */
@Composable
private fun StatusLine(@DrawableRes icon: Int, text: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(StatusSpacing),
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      modifier = Modifier.size(StatusIconSize),
      tint = color,
    )
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}

/** En deçà, le pictogramme d'un segment de frise n'est plus qu'une tache. */
private const val MIN_WEIGHT_FOR_ICON = 0.12f

/** En deçà, le nom de ligne n'a pas la place de tenir dans le segment. */
private const val MIN_WEIGHT_FOR_LINE = 0.22f

/** SPEC.md § 9 demande la lisibilité jusqu'à 200 % : au-delà, la frise cesse de grandir. */
private const val MAX_FONT_SCALE = 2f

private val CardPadding: Dp = 16.dp
private val SelectedBorder: Dp = 2.dp
private val CardSpacing: Dp = 8.dp
private val HeaderSpacing: Dp = 4.dp
private val StatusSpacing: Dp = 8.dp
private val StatusIconSize: Dp = 18.dp
private val TimelineHeight: Dp = 24.dp
private val TimelineGap: Dp = 2.dp
private val SegmentCorner: Dp = 4.dp
private val SegmentIconSize: Dp = 16.dp
private val SegmentPadding: Dp = 4.dp
private val SegmentGap: Dp = 2.dp
