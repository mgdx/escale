package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.theme.EscaleTheme

/** Le bouton de position, 56 dp, bas à droite, trois états (SPEC.md § 5.1). */
@Composable
fun LocateButton(state: LocateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val icon = when (state) {
    LocateState.UNKNOWN -> R.drawable.ic_location_searching
    LocateState.CENTERED -> R.drawable.ic_my_location
    LocateState.FOLLOWING -> R.drawable.ic_near_me
  }
  // L'état n'est jamais porté par la seule couleur (SPEC.md § 9) : l'icône change de dessin, et
  // le libellé lu par les lecteurs d'écran dit l'état en toutes lettres.
  val label = when (state) {
    LocateState.UNKNOWN -> R.string.map_locate_unknown
    LocateState.CENTERED -> R.string.map_locate_centered
    LocateState.FOLLOWING -> R.string.map_locate_following
  }
  val container = when (state) {
    LocateState.FOLLOWING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.surface
  }
  val content = when (state) {
    LocateState.FOLLOWING -> MaterialTheme.colorScheme.onPrimary
    else -> MaterialTheme.colorScheme.onSurface
  }

  FloatingActionButton(
    onClick = onClick,
    modifier = modifier.size(LOCATE_BUTTON_SIZE),
    containerColor = container,
    contentColor = content,
  ) {
    Icon(painter = painterResource(icon), contentDescription = stringResource(label))
  }
}

/**
 * L'attribution OpenStreetMap, visible en permanence (SPEC.md § 4.2 et § 5.7).
 *
 * Ce n'est pas une option : c'est une obligation de la politique d'usage de Transitous. Le bandeau
 * est aussi le bouton qui mène aux sources.
 */
@Composable
fun AttributionBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    modifier = modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
    color = MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA),
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shape = MaterialTheme.shapes.small,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_info),
        contentDescription = stringResource(R.string.map_attribution_button),
        modifier = Modifier.size(ATTRIBUTION_ICON_SIZE),
      )
      Text(
        text = stringResource(R.string.map_attribution_short),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 4.dp),
      )
    }
  }
}

/** Le message discret d'un serveur sans fond de carte (SPEC.md § 5.7). */
@Composable
fun TileWarning(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ATTRIBUTION_ALPHA),
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shape = MaterialTheme.shapes.small,
  ) {
    Text(
      text = stringResource(R.string.map_tiles_unavailable),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

/** « Partir d'ici » / « Aller ici », le menu d'appui long de SPEC.md § 5.1. */
@Composable
fun MapPickDropdown(expanded: Boolean, onPick: (MapPickPurpose) -> Unit, onDismiss: () -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuItem(
      text = { Text(text = stringResource(R.string.map_pick_departure)) },
      onClick = { onPick(MapPickPurpose.DEPARTURE) },
      leadingIcon = {
        Icon(painter = painterResource(R.drawable.ic_trip_origin), contentDescription = null)
      },
    )
    DropdownMenuItem(
      text = { Text(text = stringResource(R.string.map_pick_destination)) },
      onClick = { onPick(MapPickPurpose.DESTINATION) },
      leadingIcon = {
        Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
      },
    )
  }
}

/**
 * Permission de localisation refusée (SPEC.md § 5.1).
 *
 * Une phrase, et le chemin vers les réglages système. Le bouton de position, lui, reste : refuser
 * la localisation ne retire aucune fonction à l'application.
 */
@Composable
fun LocationPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.map_location_denied_title)) },
    text = { Text(text = stringResource(R.string.map_location_denied_message)) },
    confirmButton = {
      TextButton(onClick = onOpenSettings) {
        Text(text = stringResource(R.string.map_location_denied_action))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.map_dismiss))
      }
    },
  )
}

/** Les sources des données de la carte (SPEC.md § 4.2). */
@Composable
fun AttributionDialog(onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.map_attribution_title)) },
    text = {
      Column {
        AttributionLink(
          label = R.string.map_attribution_openstreetmap,
          link = R.string.map_attribution_openstreetmap_link,
          url = OPENSTREETMAP_URL,
          onOpenLink = onOpenLink,
        )
        AttributionLink(
          label = R.string.map_attribution_transitous,
          link = R.string.map_attribution_transitous_link,
          url = TRANSITOUS_URL,
          onOpenLink = onOpenLink,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.map_dismiss))
      }
    },
  )
}

@Composable
private fun AttributionLink(label: Int, link: Int, url: String, onOpenLink: (String) -> Unit) {
  Text(text = stringResource(label), style = MaterialTheme.typography.bodyMedium)
  TextButton(
    onClick = { onOpenLink(url) },
    modifier = Modifier.padding(bottom = 8.dp),
  ) {
    Text(
      text = stringResource(link),
      textDecoration = TextDecoration.Underline,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

/** SPEC.md § 5.1 : le bouton de position fait 56 dp. */
val LOCATE_BUTTON_SIZE = 56.dp

/** SPEC.md § 9 : cible tactile minimale de 48 dp. */
val MIN_TOUCH_TARGET = 48.dp

private const val ATTRIBUTION_ALPHA = 0.85f
private val ATTRIBUTION_ICON_SIZE = 16.dp

/** Obligations d'attribution de SPEC.md § 4.2. */
private const val OPENSTREETMAP_URL = "https://www.openstreetmap.org/copyright"
private const val TRANSITOUS_URL = "https://transitous.org/sources/"

@Preview(name = "Commandes de carte, thème clair", showBackground = true)
@Preview(
  name = "Commandes de carte, thème sombre",
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MapOverlaysPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Les trois états côte à côte : c'est ainsi qu'on vérifie qu'ils se distinguent autrement
        // que par la couleur (SPEC.md § 9).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          LocateState.entries.forEach { entry -> LocateButton(state = entry, onClick = {}) }
        }
        AttributionBar(onClick = {})
        TileWarning()
      }
    }
  }
}
