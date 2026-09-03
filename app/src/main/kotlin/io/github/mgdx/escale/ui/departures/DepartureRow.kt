package io.github.mgdx.escale.ui.departures

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.Delay
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.ui.map.StopIcon
import io.github.mgdx.escale.ui.results.delayColor
import io.github.mgdx.escale.ui.results.durationText
import io.github.mgdx.escale.ui.results.onRouteColor
import io.github.mgdx.escale.ui.results.rememberTimeFormatter
import io.github.mgdx.escale.ui.results.routeColor

/**
 * Un départ dans la liste (SPEC.md § 5.4) : « heure, retard, ligne, direction, quai, annulations
 * barrées ».
 *
 * La disposition est **verticale et sans largeur fixe**, pour tenir à 200 % d'agrandissement sans
 * troncature (SPEC.md § 9) : un tableau de départs en colonnes serré aurait été plus joli et
 * illisible pour qui grossit le texte.
 *
 * @param onOpen ouvre la desserte de la course. Nul quand le serveur n'a pas donné de `tripId` —
 *   une course ajoutée en temps réel n'en a pas — auquel cas la ligne reste affichée mais n'est
 *   pas cliquable : un appui qui ne mène nulle part serait pire que pas d'appui du tout.
 */
@Composable
internal fun DepartureRow(entry: StopTimeEntry, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
  val openLabel = stringResource(R.string.departures_open_trip)
  val clickable = if (onOpen == null) {
    Modifier
  } else {
    Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
  }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .then(clickable)
      // Cible tactile d'au moins 48 dp (SPEC.md § 9), padding compris.
      .defaultMinSize(minHeight = RowMinHeight)
      .padding(horizontal = RowPadding, vertical = RowSpacing),
    verticalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    LineAndTime(entry)
    Headsign(entry)
    entry.track?.takeIf(String::isNotBlank)?.let {
      DepartureLine(icon = R.drawable.ic_signpost, text = stringResource(R.string.departures_track, it))
    }
    StatusLine(entry)
  }
}

/** La ligne et l'heure : le pictogramme de mode, la pastille de ligne, puis l'heure à droite. */
@Composable
private fun LineAndTime(entry: StopTimeEntry) {
  val formatTime = rememberTimeFormatter()
  val icon = StopIcon.of(entry.mode)
  val modeLabel = stringResource(icon.label)
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Icon(
      painter = painterResource(icon.drawable),
      // Le pictogramme est muet : c'est la pastille voisine qui porte le libellé complet, et le
      // répéter ferait dire deux fois le mode au lecteur d'écran (SPEC.md § 9).
      contentDescription = null,
      modifier = Modifier.size(ModeIconSize),
    )
    LineBadge(entry = entry, modeLabel = modeLabel, modifier = Modifier.weight(1f, fill = false))
    Text(
      text = formatTime(entry.time),
      style = MaterialTheme.typography.titleMedium,
      // « Annulations barrées » (SPEC.md § 5.4). Le barré ne porte pas l'information à lui seul :
      // la ligne d'état en dessous l'écrit en toutes lettres, avec son pictogramme.
      textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null,
    )
  }
}

/**
 * La pastille de ligne, aux couleurs du réseau quand il en publie.
 *
 * Le mode est **toujours** énoncé au lecteur d'écran, même quand l'œil le lit dans le pictogramme
 * voisin : « Bus, ligne 18 » (SPEC.md § 9).
 */
@Composable
private fun LineBadge(entry: StopTimeEntry, modeLabel: String, modifier: Modifier = Modifier) {
  val label = entry.lineName.ifBlank { modeLabel }
  val description = stringResource(R.string.departures_line_description, modeLabel, label)
  val background = routeColor(entry.routeColor) ?: MaterialTheme.colorScheme.secondaryContainer
  val content = onRouteColor(
    textColor = entry.routeTextColor,
    background = entry.routeColor,
    fallback = MaterialTheme.colorScheme.onSecondaryContainer,
  )
  Text(
    text = label,
    style = MaterialTheme.typography.titleSmall,
    color = content,
    modifier = modifier
      .clip(RoundedCornerShape(BadgeCorner))
      .background(background)
      .padding(horizontal = BadgePadding, vertical = BadgeVerticalPadding)
      .semantics { contentDescription = description },
  )
}

/** La direction annoncée à l'usager, c'est-à-dire la girouette du véhicule. */
@Composable
private fun Headsign(entry: StopTimeEntry) {
  val headsign = entry.headsign.takeIf(String::isNotBlank) ?: return
  DepartureLine(
    icon = R.drawable.ic_arrow_forward,
    text = stringResource(R.string.departures_towards, headsign),
    style = MaterialTheme.typography.bodyLarge,
  )
}

/**
 * Ce que l'application a le droit d'annoncer sur l'horaire de ce départ.
 *
 * **Une entrée sans donnée temps réel ne produit aucune ligne** : ni couleur, ni « à l'heure ».
 * C'est `Delay.between` qui tranche, dans `:core`, et il rend `null` plutôt qu'un écart nul — sans
 * temps réel, les deux heures sont égales par construction et un écart nul ne prouve rien
 * (SPEC.md § 5.2).
 */
@Composable
private fun StatusLine(entry: StopTimeEntry) {
  if (entry.cancelled) {
    DepartureLine(
      icon = R.drawable.ic_cancel,
      text = stringResource(R.string.results_cancelled),
      color = MaterialTheme.colorScheme.error,
    )
    return
  }
  val delay = Delay.between(entry.time, entry.scheduledTime, entry.realTime) ?: return
  val amount = durationText(delay.formatted)
  // Le libellé suit la **qualification** de l'écart, pas sa valeur arrondie : à quarante-cinq
  // secondes de retard, `:core` dit « à l'heure », et l'affichage doit dire la même chose.
  val text = when (delay.quality) {
    DelayQuality.ON_TIME -> stringResource(R.string.results_delay_on_time)
    DelayQuality.EARLY -> stringResource(R.string.results_delay_early, amount)
    DelayQuality.SLIGHT, DelayQuality.SEVERE -> stringResource(R.string.results_delay_late, amount)
  }
  DepartureLine(
    icon = if (delay.quality == DelayQuality.ON_TIME) R.drawable.ic_check_circle else R.drawable.ic_schedule,
    text = text,
    color = delayColor(delay.quality),
  )
  ScheduledTime(entry)
}

/**
 * L'horaire théorique, montré **seulement** quand il diffère de l'heure effective.
 *
 * Sans temps réel les deux sont égales par construction, et l'afficher ferait croire à une
 * information qu'on n'a pas — même arbitrage que l'écran de détail.
 */
@Composable
private fun ScheduledTime(entry: StopTimeEntry) {
  if (entry.scheduledTime == entry.time) return
  val formatTime = rememberTimeFormatter()
  DepartureLine(
    icon = null,
    text = stringResource(R.string.departures_scheduled_time, formatTime(entry.scheduledTime)),
    style = MaterialTheme.typography.bodySmall,
  )
}

/** Une ligne d'information : un pictogramme, un texte, et jamais une couleur seule (SPEC.md § 9). */
@Composable
private fun DepartureLine(
  @DrawableRes icon: Int?,
  text: String,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (icon != null) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(LineIconSize),
      )
    }
    Text(text = text, style = style, color = color)
  }
}

private val RowMinHeight: Dp = 48.dp
private val RowPadding: Dp = 16.dp
private val RowSpacing: Dp = 8.dp
private val ModeIconSize: Dp = 24.dp
private val LineIconSize: Dp = 18.dp
private val BadgeCorner: Dp = 8.dp
private val BadgePadding: Dp = 10.dp
private val BadgeVerticalPadding: Dp = 4.dp
