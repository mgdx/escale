package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'infobulle d'une station ou d'un véhicule en libre-service (SPEC.md § 5.7) : « nom, véhicules
 * disponibles, places libres, lien vers l'exploitant ».
 *
 * Jumelle de [MapStopCard], et posée au même endroit, pour les mêmes raisons : au bas de la carte
 * plutôt qu'accrochée au marqueur, afin que rien ne sorte de l'écran à 200 % d'agrandissement
 * (SPEC.md § 9). Les deux ne s'affichent jamais ensemble, le `ViewModel` ferme l'une en ouvrant
 * l'autre.
 *
 * **Rien n'est porté par la couleur** : la nature du point est écrite (« Station » / « Véhicule en
 * libre accès »), le type de véhicule est nommé dans la description du pictogramme, et les comptes
 * sont des chiffres accompagnés de leur unité au bon pluriel.
 */
@Composable
fun MapRentalCard(rental: SelectedRental, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
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
      RentalHeader(rental = rental, onDismiss = onDismiss)
      RentalAvailabilityLines(rental = rental)
      OperatorLink(uri = rental.rentalUriAndroid)
    }
  }
}

/** Le pictogramme du véhicule, le nom du point, sa nature, et la croix de fermeture, à 48 dp. */
@Composable
private fun RentalHeader(rental: SelectedRental, onDismiss: () -> Unit) {
  val vehicleType = stringResource(rental.icon.label)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(rental.icon.drawable),
      // Le type de véhicule n'est écrit nulle part ailleurs dans la fiche : le pictogramme le porte,
      // et sa description le dit à voix haute (SPEC.md § 9).
      contentDescription = vehicleType,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(HeaderIconSize),
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = CardSpacing),
      verticalArrangement = Arrangement.spacedBy(TitleSpacing),
    ) {
      // Un véhicule isolé n'a pas de nom : son type en tient lieu, ce qui reste vrai et lisible.
      Text(text = rental.name.ifBlank { vehicleType }, style = MaterialTheme.typography.titleMedium)
      Text(
        text = stringResource(rental.kind.labelRes()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onDismiss, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
      Icon(
        painter = painterResource(R.drawable.ic_cancel),
        contentDescription = stringResource(R.string.map_rental_close),
      )
    }
  }
}

/**
 * Les disponibilités : « 7 vélos disponibles · 4 places libres » (SPEC.md § 5.7).
 *
 * Les places libres ne s'affichent que là où elles ont un sens : un véhicule isolé ne se rend pas
 * dans une borne. Une station hors service le dit en toutes lettres plutôt que par une teinte.
 */
@Composable
private fun RentalAvailabilityLines(rental: SelectedRental) {
  RentalLine(
    icon = R.drawable.ic_bike_scooter,
    text = pluralStringResource(
      R.plurals.map_rental_vehicles_available,
      rental.vehiclesAvailable,
      rental.vehiclesAvailable,
    ),
  )
  if (rental.kind == RentalMarkerKind.STATION) {
    RentalLine(
      icon = R.drawable.ic_place,
      text = pluralStringResource(R.plurals.map_rental_docks_available, rental.docksAvailable, rental.docksAvailable),
    )
  }
  if (!rental.isRenting) {
    RentalLine(
      icon = R.drawable.ic_warning,
      text = stringResource(R.string.map_rental_not_renting),
      color = MaterialTheme.colorScheme.error,
    )
  }
  if (rental.kind == RentalMarkerKind.STATION && !rental.isReturning) {
    RentalLine(
      icon = R.drawable.ic_warning,
      text = stringResource(R.string.map_rental_not_returning),
      color = MaterialTheme.colorScheme.error,
    )
  }
}

/** Une ligne d'information : un pictogramme et un texte, jamais un texte seul ni un pictogramme seul. */
@Composable
private fun RentalLine(icon: Int, text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(icon),
      // Le texte de la ligne dit déjà tout : décrire le pictogramme le répéterait à l'écoute.
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(LineIconSize),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = color,
      modifier = Modifier.padding(start = CardSpacing),
    )
  }
}

/**
 * Le lien vers l'exploitant (SPEC.md § 5.7) : **intent externe, jamais de WebView** (SPEC.md § 2).
 *
 * Absent du flux, il n'y a pas de bouton : mieux vaut pas de bouton qu'un bouton mort. Présent mais
 * non résolu — aucune application installée ne sait ouvrir ce schéma —, `runCatching` évite la
 * `ActivityNotFoundException` qui ferait tomber l'application.
 */
@Composable
private fun OperatorLink(uri: String?) {
  if (uri.isNullOrBlank()) return
  val uriHandler = LocalUriHandler.current
  TextButton(
    onClick = { runCatching { uriHandler.openUri(uri) } },
    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_open_in_new),
      contentDescription = null,
      modifier = Modifier.size(ButtonIconSize),
    )
    Text(text = stringResource(R.string.map_rental_open_app), modifier = Modifier.padding(start = CardSpacing))
  }
}

/** Le libellé de la famille, écrit en toutes lettres : la forme du marqueur ne suffit pas à l'oral. */
private fun RentalMarkerKind.labelRes(): Int = when (this) {
  RentalMarkerKind.STATION -> R.string.map_rental_kind_station
  RentalMarkerKind.VEHICLE -> R.string.map_rental_kind_vehicle
}

private val CardPadding = 16.dp
private val CardSpacing = 8.dp
private val CardElevation = 3.dp
private val TitleSpacing = 2.dp
private val HeaderIconSize = 28.dp
private val LineIconSize = 18.dp
private val ButtonIconSize = 18.dp

@Preview(name = "Libre-service, thème clair", showBackground = true)
@Preview(name = "Libre-service, thème sombre", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Libre-service, texte à 200 %", showBackground = true, fontScale = 2f)
@Composable
private fun MapRentalCardPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapRentalCard(rental = PREVIEW_STATION, onDismiss = {})
        MapRentalCard(rental = PREVIEW_VEHICLE, onDismiss = {})
      }
    }
  }
}

/** Une station d'exemple pour l'aperçu : des données publiques, aucune position d'usager. */
private val PREVIEW_STATION = SelectedRental(
  id = "station",
  name = "Hôtel de Ville",
  kind = RentalMarkerKind.STATION,
  icon = RentalIcon.BICYCLE,
  vehiclesAvailable = 7,
  docksAvailable = 4,
  isRenting = true,
  isReturning = false,
  rentalUriAndroid = "https://example.org/station",
)

private val PREVIEW_VEHICLE = SelectedRental(
  id = "vehicule",
  name = "",
  kind = RentalMarkerKind.VEHICLE,
  icon = RentalIcon.SCOOTER,
  vehiclesAvailable = 1,
  docksAvailable = 0,
  isRenting = true,
  isReturning = true,
)
