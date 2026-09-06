package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiComplement
import io.github.mgdx.escale.core.model.PoiTypeKey
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * La fiche d'un point d'intérêt du fond de carte (SPEC.md § 5.7).
 *
 * « Le nom du lieu, ou le libellé de sa famille s'il n'en a pas ; son type traduit ; son adresse ;
 * enfin *Partir d'ici* et *Aller ici*. » Elle s'ouvre sur **tout** pictogramme visible, commerce
 * comme repère de la v1 : un dessin qui ne répond pas au doigt est un dessin qui ment.
 *
 * Jumelle de [MapStopCard] et de [MapRentalCard], et posée au même endroit, pour les mêmes raisons :
 * au bas de la carte plutôt qu'accrochée au marqueur, afin que rien ne sorte de l'écran à 200 %
 * d'agrandissement (SPEC.md § 9). Les trois ne s'affichent jamais ensemble.
 *
 * **Rien n'est porté par la couleur** : le type est écrit en toutes lettres, l'adresse aussi, et les
 * deux boutons disent ce qu'ils font. Ni horaires, ni site, ni téléphone : ces données ne sont ni
 * dans les tuiles ni dans l'API, et on n'affiche pas ce qu'on n'a pas.
 */
@Composable
fun MapPlaceCard(
  place: SelectedPlace,
  onPick: (MapPickPurpose) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
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
      PlaceHeader(place = place, onDismiss = onDismiss)
      PlaceAddress(place = place)
      // Deux boutons empilés plutôt que côte à côte : à 200 % d'agrandissement, « Partir d'ici » et
      // « Aller ici » ne tiennent pas sur une ligne partagée sans se tronquer (SPEC.md § 9).
      FilledTonalButton(
        onClick = { onPick(MapPickPurpose.DEPARTURE) },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MIN_TOUCH_TARGET),
      ) {
        ButtonContent(icon = R.drawable.ic_trip_origin, label = stringResource(R.string.map_pick_departure))
      }
      Button(
        onClick = { onPick(MapPickPurpose.DESTINATION) },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MIN_TOUCH_TARGET),
      ) {
        ButtonContent(icon = R.drawable.ic_place, label = stringResource(R.string.map_pick_destination))
      }
    }
  }
}

/** Le pictogramme du lieu, son nom, son type, et la croix de fermeture, à 48 dp. */
@Composable
private fun PlaceHeader(place: SelectedPlace, onDismiss: () -> Unit) {
  val type = placeTypeLabel(place)
  val title = place.name.ifBlank { type } ?: stringResource(R.string.map_place_unnamed)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(R.drawable.ic_business),
      // Le type est écrit juste à côté : décrire le pictogramme le répéterait à l'écoute.
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(HeaderIconSize),
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = CardSpacing),
      verticalArrangement = Arrangement.spacedBy(TitleSpacing),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      // Le type ne se répète pas quand il a déjà servi de nom à un lieu qui n'en porte pas.
      if (type != null && title != type) {
        Text(
          text = type,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    IconButton(onClick = onDismiss, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
      Icon(
        painter = painterResource(R.drawable.ic_cancel),
        contentDescription = stringResource(R.string.map_place_close),
      )
    }
  }
}

/**
 * L'adresse, et rien quand il n'y en a pas.
 *
 * La tuile ne porte que le numéro de voie : il s'affiche en attendant que le géocodage inverse rende
 * la rue. Ni l'un ni l'autre, et la ligne disparaît — un « Adresse inconnue » n'apprendrait rien.
 */
@Composable
private fun PlaceAddress(place: SelectedPlace) {
  val address = place.address?.name?.takeIf { it.isNotBlank() } ?: place.houseNumber ?: return
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(R.drawable.ic_signpost),
      // Le texte de la ligne est l'adresse elle-même : le pictogramme n'a rien à ajouter.
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(LineIconSize),
    )
    Text(
      text = address,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = CardSpacing),
    )
  }
}

/** Le contenu d'un bouton : un pictogramme muet et le libellé qui dit ce qui va se passer. */
@Composable
private fun ButtonContent(icon: Int, label: String) {
  Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonIconSize))
  Text(text = label, modifier = Modifier.padding(start = CardSpacing))
}

/**
 * Le type traduit, complément compris : « Boulangerie », « Restaurant · italian », « Banque ·
 * distributeur », « Lieu de culte · catholic ».
 *
 * À défaut de type précis, le libellé de la catégorie fait l'affaire — « Autres commerces » reste
 * vrai. Et à défaut de catégorie, il n'y a rien à écrire.
 */
@Composable
private fun placeTypeLabel(place: SelectedPlace): String? {
  val type = place.typeKey?.let { POI_TYPE_LABELS[it] }
    ?: place.category?.let(::poiCategoryLabel)
    ?: return null
  return when (val complement = place.complement) {
    null -> stringResource(type)

    is PoiComplement.Detail -> stringResource(R.string.map_place_type_detail, stringResource(type), complement.value)

    PoiComplement.CashMachine -> stringResource(
      R.string.map_place_type_detail,
      stringResource(type),
      stringResource(R.string.map_place_detail_atm),
    )
  }
}

private val CardPadding = 16.dp
private val CardSpacing = 8.dp
private val CardElevation = 3.dp
private val TitleSpacing = 2.dp
private val HeaderIconSize = 28.dp
private val LineIconSize = 18.dp
private val ButtonIconSize = 18.dp

@Preview(name = "Fiche de lieu, thème clair", showBackground = true)
@Preview(name = "Fiche de lieu, thème sombre", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fiche de lieu, texte à 200 %", showBackground = true, fontScale = 2f)
@Composable
private fun MapPlaceCardPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapPlaceCard(place = PREVIEW_RESTAURANT, onPick = {}, onDismiss = {})
        MapPlaceCard(place = PREVIEW_UNNAMED, onPick = {}, onDismiss = {})
      }
    }
  }
}

/** Un lieu d'exemple pour l'aperçu : une donnée publique, aucune position d'usager. */
private val PREVIEW_RESTAURANT = SelectedPlace(
  point = LatLon(lat = 48.8566, lon = 2.3522),
  name = "Chez Marcel",
  category = PoiCategory.DINING,
  typeKey = PoiTypeKey.RESTAURANT,
  complement = PoiComplement.Detail("italian"),
  houseNumber = "12",
  address = Location(
    id = null,
    name = "12 Rue de Rivoli",
    description = "Paris",
    coordinates = LatLon(lat = 48.8566, lon = 2.3522),
    kind = PlaceKind.ADDRESS,
  ),
  addressLoading = false,
)

/** Un lieu sans nom et sans adresse : son type lui en tient lieu, et la fiche reste utile. */
private val PREVIEW_UNNAMED = SelectedPlace(
  point = LatLon(lat = 48.8570, lon = 2.3530),
  category = PoiCategory.TOILETS,
  typeKey = PoiTypeKey.TOILETS,
  addressLoading = false,
)
