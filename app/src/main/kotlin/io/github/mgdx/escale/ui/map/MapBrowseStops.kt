package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.theme.EscaleTheme

/*
 * Le parcours des arrêts affichés (SPEC.md § 9).
 *
 * Le § 9 promet que « tous les éléments interactifs ont un contentDescription ». Les marqueurs de
 * la carte y échappaient, et pas par négligence : MapLibre rend la carte comme **une seule vue**,
 * et la règle 6 du § 5.7 interdit d'en faire des vues Android superposées, qui écrouleraient la
 * fluidité dès la centaine de points. Aucun `contentDescription` ne peut donc atteindre un marqueur.
 *
 * D'où cette porte d'à côté : un bouton, une liste ordinaire, et chaque arrêt menant à la **même**
 * infobulle qu'un appui sur la carte. Elle ne coûte rien à la fluidité — aucune requête, aucun
 * calcul pendant le mouvement, aucune vue posée sur la carte — et elle donne un accès réel, y
 * compris à la commande par contacteur et au clavier.
 *
 * L'autre piste envisagée, une surface transparente exposant un nœud sémantique par arrêt visible,
 * positionnée par la projection, aurait rendu la carte navigable au doigt exploratoire. Elle
 * demande en revanche de reprojeter chaque point à chaque `onCameraIdle` et de tenir des nœuds
 * superposés à la vue de MapLibre : c'est le genre de dispositif qui se paye en images perdues, et
 * la règle 6 existe précisément pour l'éviter.
 */

/**
 * Le bouton qui ouvre la liste, sous les commandes de la carte.
 *
 * Il ne s'affiche que lorsqu'il y a quelque chose à parcourir : un bouton qui ouvrirait une liste
 * vide serait un piège. Son libellé annonce le compte, ce qui est aussi la seule façon, sans voir
 * la carte, de savoir qu'on vient d'arriver quelque part de dense.
 */
@Composable
fun BrowseStopsButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  if (count <= 0) return
  val label = stringResource(R.string.map_browse_stops)
  val summary = pluralStringResource(R.plurals.map_browse_stops_count, count, count)
  FloatingActionButton(
    onClick = onClick,
    modifier = modifier.size(MIN_TOUCH_TARGET),
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_format_list_bulleted),
      // L'action et son compte en une annonce : « Parcourir les arrêts affichés, 12 arrêts ».
      contentDescription = stringResource(R.string.results_summary, label, summary),
    )
  }
}

/**
 * La liste elle-même : une feuille modale, des lignes ordinaires, rien d'autre.
 *
 * Chaque ligne porte le pictogramme de son mode **et** le nom du mode en toutes lettres : SPEC.md
 * § 9 exige que les pictogrammes de mode soient doublés d'un libellé textuel, et cette liste est
 * justement lue par ceux qui ne voient pas le pictogramme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseStopsSheet(
  stops: List<SelectedStop>,
  truncated: Boolean,
  onStopSelected: (SelectedStop) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    BrowseStopsList(stops = stops, truncated = truncated, onStopSelected = onStopSelected)
  }
}

/** Le contenu de la feuille, séparé pour être rendu par un aperçu — modale comprise, à 200 %. */
@Composable
internal fun BrowseStopsList(
  stops: List<SelectedStop>,
  truncated: Boolean,
  onStopSelected: (SelectedStop) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SheetSpacing),
  ) {
    Text(
      text = stringResource(R.string.map_browse_stops_title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = SheetPadding),
    )
    if (stops.isEmpty()) {
      // Une liste vide dit pourquoi elle l'est et ce qu'il faut faire : SPEC.md § 8 interdit un
      // écran muet, et ici le muet serait doublement injuste — rien à voir, rien à entendre.
      Text(
        text = stringResource(R.string.map_browse_stops_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SheetPadding, vertical = SheetPadding),
      )
      return@Column
    }
    if (truncated) {
      Text(
        text = pluralStringResource(R.plurals.map_browse_stops_truncated, stops.size, stops.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SheetPadding),
      )
    }
    LazyColumn {
      items(items = stops, key = { it.id }) { stop -> BrowseStopRow(stop = stop, onClick = onStopSelected) }
    }
  }
}

@Composable
private fun BrowseStopRow(stop: SelectedStop, onClick: (SelectedStop) -> Unit) {
  val icon = StopIcon.of(stop.mode)
  val mode = stringResource(icon.label)
  val open = stringResource(R.string.map_browse_stops_open)
  ListItem(
    headlineContent = { Text(text = stop.name) },
    // Le mode écrit sous le nom : le pictogramme à gauche ne dit rien à qui écoute (SPEC.md § 9).
    supportingContent = { Text(text = mode) },
    leadingContent = {
      Icon(
        painter = painterResource(icon.drawable),
        contentDescription = null,
        modifier = Modifier.size(RowIconSize),
      )
    },
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MIN_TOUCH_TARGET)
      .clickable(onClickLabel = open, role = Role.Button) { onClick(stop) },
  )
}

private val SheetPadding = 16.dp
private val SheetSpacing = 8.dp
private val RowIconSize = 24.dp

private val PREVIEW_STOPS = listOf(
  SelectedStop(id = "a", name = "Châtelet — Les Halles", mode = TransitMode.SUBURBAN),
  SelectedStop(id = "b", name = "Châtelet", mode = TransitMode.SUBWAY),
  SelectedStop(id = "c", name = "Pont Neuf", mode = TransitMode.BUS),
  SelectedStop(id = "d", name = "Gare de Lyon", mode = TransitMode.RAIL),
)

@Preview(name = "Arrêts affichés, thème clair", showBackground = true)
@Preview(
  name = "Arrêts affichés, thème sombre",
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Arrêts affichés, texte à 200 %", showBackground = true, fontScale = 2f, heightDp = 700)
@Composable
private fun BrowseStopsPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      BrowseStopsList(
        stops = PREVIEW_STOPS,
        truncated = true,
        onStopSelected = {},
        modifier = Modifier.padding(vertical = SheetPadding),
      )
    }
  }
}

@Preview(name = "Aucun arrêt affiché", showBackground = true)
@Preview(name = "Aucun arrêt affiché, texte à 200 %", showBackground = true, fontScale = 2f)
@Composable
private fun BrowseStopsEmptyPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      BrowseStopsList(
        stops = emptyList(),
        truncated = false,
        onStopSelected = {},
        modifier = Modifier.padding(vertical = SheetPadding),
      )
    }
  }
}
