package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'infobulle d'un arrêt (SPEC.md § 5.7) : « le nom et les lignes desservies, et un bouton menant
 * aux prochains départs ».
 *
 * Elle se pose au bas de la carte plutôt qu'accrochée au marqueur, et c'est délibéré : SPEC.md § 9
 * exige que tout reste lisible à 200 % d'agrandissement, et une bulle ancrée sur un point sortirait
 * de l'écran dès qu'on touche un arrêt d'un bord. Ici, la fiche occupe la largeur, les lignes
 * passent à la ligne, et le remplissage transmis la tient au-dessus de la feuille de résultats.
 *
 * Aucune information n'est portée par la seule couleur : chaque ligne porte son numéro écrit, son
 * pictogramme de mode, et un libellé lu à voix haute qui nomme le mode en toutes lettres.
 */
@Composable
fun MapStopCard(stop: SelectedStop, onDepartures: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.large,
    tonalElevation = CardElevation,
    shadowElevation = CardElevation,
  ) {
    Column(
      modifier = Modifier.padding(CardPadding),
      verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
      StopHeader(stop = stop, onDismiss = onDismiss)
      StopLines(stop = stop)
      Button(
        onClick = onDepartures,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_schedule),
          contentDescription = null,
          modifier = Modifier.size(ButtonIconSize),
        )
        Text(
          text = stringResource(R.string.map_stop_departures),
          modifier = Modifier.padding(start = CardSpacing),
        )
      }
    }
  }
}

/** Le pictogramme du mode, le nom de l'arrêt, et la croix de fermeture, à 48 dp. */
@Composable
private fun StopHeader(stop: SelectedStop, onDismiss: () -> Unit) {
  val icon = StopIcon.of(stop.mode)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(icon.drawable),
      // Le mode est déjà annoncé par le sous-titre des lignes ; le répéter ici alourdirait
      // l'écoute au lecteur d'écran.
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(HeaderIconSize),
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = CardSpacing),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(text = stop.name, style = MaterialTheme.typography.titleMedium)
      StopSummary(stop = stop)
    }
    IconButton(onClick = onDismiss, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
      Icon(
        painter = painterResource(R.drawable.ic_cancel),
        contentDescription = stringResource(R.string.map_stop_close),
      )
    }
  }
}

/** Ce que l'arrêt dessert, en une ligne : le compte des lignes, ou l'état du chargement. */
@Composable
private fun StopSummary(stop: SelectedStop) {
  val text = when {
    stop.linesLoading -> stringResource(R.string.map_stop_lines_loading)
    stop.linesFailed -> stringResource(R.string.map_stop_lines_unavailable)
    stop.lines.isEmpty() -> stringResource(R.string.map_stop_lines_none)
    else -> pluralStringResource(R.plurals.map_stop_lines_served, stop.lines.size, stop.lines.size)
  }
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Les lignes desservies, une puce chacune, qui passent à la ligne quand elles ne tiennent pas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopLines(stop: SelectedStop) {
  if (stop.linesLoading) {
    CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
    return
  }
  if (stop.lines.isEmpty()) return
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    verticalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    stop.lines.forEach { line -> StopLineChip(line = line) }
  }
}

/**
 * Une ligne desservie.
 *
 * La couleur est celle du réseau quand il la publie, et **elle ne porte jamais l'information à elle
 * seule** (SPEC.md § 9) : le numéro est écrit, le pictogramme dit le mode, et le libellé lu par un
 * lecteur d'écran énonce « Métro 14 ». Sans numéro ni nom, la puce affiche le mode, ce qui reste
 * vrai.
 */
@Composable
private fun StopLineChip(line: StopLine) {
  val mode = stringResource(StopIcon.of(line.mode).label)
  val label = line.label.ifBlank { mode }
  val background = HexColor.parse(line.color)?.let(::Color) ?: MaterialTheme.colorScheme.secondaryContainer
  val foreground = HexColor.parse(line.textColor)?.let(::Color) ?: MaterialTheme.colorScheme.onSecondaryContainer
  val description = stringResource(R.string.map_stop_line_description, mode, label)

  Row(
    modifier = Modifier
      .heightIn(min = ChipHeight)
      .background(background, CircleShape)
      .padding(horizontal = ChipPadding)
      // Une seule annonce par puce : le pictogramme et le numéro forment un tout.
      .clearAndSetSemantics { contentDescription = description },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      painter = painterResource(StopIcon.of(line.mode).drawable),
      contentDescription = null,
      tint = foreground,
      modifier = Modifier.size(ChipIconSize),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = foreground,
      modifier = Modifier.widthIn(max = ChipLabelMaxWidth),
    )
  }
}

private val CardPadding = 16.dp
private val CardSpacing = 8.dp
private val CardElevation = 3.dp
private val HeaderIconSize = 28.dp
private val ButtonIconSize = 18.dp
private val ProgressSize = 24.dp
private val ChipSpacing = 6.dp
private val ChipHeight = 32.dp
private val ChipPadding = 10.dp
private val ChipIconSize = 16.dp

/** Un numéro de ligne tient en quelques caractères ; un nom long ne doit pas manger la fiche. */
private val ChipLabelMaxWidth = 160.dp

@Preview(name = "Infobulle d'arrêt, thème clair", showBackground = true)
@Preview(name = "Infobulle d'arrêt, thème sombre", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Infobulle d'arrêt, texte à 200 %", showBackground = true, fontScale = 2f)
@Composable
private fun MapStopCardPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapStopCard(stop = PREVIEW_STOP, onDepartures = {}, onDismiss = {})
        MapStopCard(stop = SelectedStop(id = "b", name = "Pont Neuf", mode = TransitMode.BUS), {}, {})
      }
    }
  }
}

/** Un arrêt d'exemple pour l'aperçu : des données publiques, aucune position d'usager. */
private val PREVIEW_STOP = SelectedStop(
  id = "a",
  name = "Châtelet",
  mode = TransitMode.SUBWAY,
  linesLoading = false,
  lines = listOf(
    StopLine("1", "1", "", TransitMode.SUBWAY, "RATP", "#FFBE00", "#000000"),
    StopLine("4", "4", "", TransitMode.SUBWAY, "RATP", "#A0006E", "#FFFFFF"),
    StopLine("14", "14", "", TransitMode.SUBWAY, "RATP", "#640082", "#FFFFFF"),
    StopLine("21", "21", "", TransitMode.BUS, "RATP"),
    StopLine("N11", "N11", "", TransitMode.BUS, "Bord de Marne"),
  ),
)
