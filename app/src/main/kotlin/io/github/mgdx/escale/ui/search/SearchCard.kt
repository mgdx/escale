package io.github.mgdx.escale.ui.search

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Instant

/**
 * La carte de recherche flottante, au-dessus de la carte (SPEC.md § 5.1).
 *
 * Trois lignes : départ, arrivée, heure — et le bouton d'inversion à droite des deux premières.
 * **Aucun bouton « Rechercher »** : la recherche part d'elle-même dès que les deux points sont
 * connus, ce dont s'occupe la feuille de résultats.
 *
 * @param padding les encarts système transmis par `HomeScreen`. Ils portent déjà la barre d'état :
 *   la carte n'a plus qu'à s'en écarter de 12 dp.
 */
@Composable
fun SearchCard(state: SearchUiState, actions: SearchActions, padding: PaddingValues, modifier: Modifier = Modifier) {
  val direction = LocalLayoutDirection.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(direction),
      )
      .padding(all = ScreenMargin),
    verticalArrangement = Arrangement.spacedBy(ScreenMargin),
  ) {
    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = CardTonalElevation,
      shadowElevation = CardShadowElevation,
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            SearchFieldRow(SearchField.FROM, state.from, actions)
            HorizontalDivider(modifier = Modifier.padding(start = DividerInset))
            SearchFieldRow(SearchField.TO, state.to, actions)
          }
          IconButton(onClick = actions.onSwap, modifier = Modifier.size(MinTouchTarget)) {
            Icon(
              painter = painterResource(R.drawable.ic_swap_vert),
              contentDescription = stringResource(R.string.search_swap),
            )
          }
          Spacer(modifier = Modifier.width(RowSpacing))
        }
        HorizontalDivider()
        TimeRow(state = state, onClick = actions.onOpenTimePicker)
      }
    }

    // Les puces ne s'affichent que quand les deux champs sont vides, et une puce absente n'est pas
    // affichée du tout (SPEC.md § 5.1) : `quickChips` a déjà tranché, il n'y a rien à décider ici.
    if (state.chips.isNotEmpty()) {
      QuickChipRow(chips = state.chips, onChipSelected = actions.onChipSelected)
    }

    if (state.awaitingMapPick) {
      MapPickHint(onCancel = actions.onMapPickCancelled)
    }
  }
}

/**
 * Un des deux champs de la carte.
 *
 * Il n'est pas éditable sur place : un appui ouvre le plein écran, parce que SPEC.md § 5.1 refuse
 * de superposer un clavier et une liste au-dessus de la carte.
 */
@Composable
private fun SearchFieldRow(field: SearchField, value: Location?, actions: SearchActions) {
  val hint = stringResource(field.hintRes())
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchTarget)
      .clickable(onClickLabel = hint, role = Role.Button) { actions.onOpenField(field) }
      .padding(horizontal = RowPadding, vertical = RowSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(field.iconRes()),
      contentDescription = stringResource(field.labelRes()),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(RowSpacing))
    Text(
      // Le nom du lieu, tel que l'autocomplétion l'a rendu ; à défaut, ce que le champ attend.
      text = value?.name ?: hint,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
      color = if (value == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.onSurface
      },
    )
    if (value != null) {
      IconButton(onClick = { actions.onClearField(field) }, modifier = Modifier.size(MinTouchTarget)) {
        Icon(
          painter = painterResource(R.drawable.ic_cancel),
          contentDescription = stringResource(R.string.search_clear),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** La troisième ligne : le choix d'heure, « Maintenant » par défaut (SPEC.md § 5.1). */
@Composable
private fun TimeRow(state: SearchUiState, onClick: () -> Unit) {
  val label = stringResource(R.string.search_time_change)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchTarget)
      .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
      .padding(horizontal = RowPadding, vertical = RowSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_schedule),
      contentDescription = label,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(RowSpacing))
    Text(text = timeChoiceLabel(state.time), style = MaterialTheme.typography.bodyLarge)
  }
}

/**
 * Les puces d'accès rapide (SPEC.md § 5.1).
 *
 * Une rangée qui défile plutôt qu'une rangée qui se coupe : à 200 % d'agrandissement, une puce
 * « Domicile » reste entièrement lisible, quitte à ce que la suivante attende un glissement.
 */
@Composable
private fun QuickChipRow(chips: List<QuickChip>, onChipSelected: (QuickChip) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(RowSpacing)) {
    items(items = chips, key = ::chipKey) { chip ->
      AssistChip(
        onClick = { onChipSelected(chip) },
        label = { Text(text = chipLabel(chip)) },
        leadingIcon = {
          (chip as? QuickChip.Saved)?.let { saved ->
            Icon(
              painter = painterResource(saved.kind.iconRes()),
              contentDescription = null,
              modifier = Modifier.size(ChipIconSize),
            )
          }
        },
      )
    }
  }
}

/** Le message qui accompagne « Choisir sur la carte » (SPEC.md § 5.1). */
@Composable
private fun MapPickHint(onCancel: () -> Unit) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    tonalElevation = CardTonalElevation,
  ) {
    Row(
      modifier = Modifier.padding(start = RowPadding, top = RowSpacing, bottom = RowSpacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.search_pick_on_map_hint),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(onClick = onCancel) {
        Text(text = stringResource(R.string.search_action_cancel))
      }
    }
  }
}

@Composable
private fun chipLabel(chip: QuickChip): String = when (chip) {
  is QuickChip.Saved -> stringResource(chip.kind.labelRes())
  is QuickChip.Recent -> stringResource(R.string.search_chip_recent, chip.search.from.name, chip.search.to.name)
}

/** Une clé stable par puce, pour que le défilement ne recompose pas tout à chaque frappe. */
private fun chipKey(chip: QuickChip): String = when (chip) {
  is QuickChip.Saved -> chip.kind.name
  is QuickChip.Recent -> chip.search.from.name + " " + chip.search.to.name
}

/** Marges de SPEC.md § 5.1 : 12 dp entre la carte de recherche et le bord de l'écran. */
private val ScreenMargin: Dp = 12.dp

/** Cible tactile minimale de SPEC.md § 9. */
private val MinTouchTarget: Dp = 48.dp

private val RowPadding: Dp = 12.dp
private val RowSpacing: Dp = 8.dp
private val DividerInset: Dp = 48.dp
private val ChipIconSize: Dp = 18.dp
private val CardTonalElevation: Dp = 3.dp
private val CardShadowElevation: Dp = 6.dp

// --- Aperçus -----------------------------------------------------------------------------------
//
// Les libellés ci-dessous sont des **données d'exemple**, au même titre qu'une URL de serveur dans
// l'aperçu des réglages : ce sont des noms de lieux que le serveur renverrait, pas des chaînes
// d'interface. Toutes les chaînes affichées par la carte, elles, viennent de `strings_search.xml`.
//
// L'aperçu à 200 % existe parce que c'est là que cet écran casse en premier : la carte est dense,
// et SPEC.md § 9 impose qu'elle reste lisible sans troncature.

private val previewFrom = Location(
  id = null,
  name = "Place de la Bastille",
  description = "Paris",
  coordinates = LatLon(lat = 48.8532, lon = 2.3692),
  kind = PlaceKind.ADDRESS,
)

private val previewTo = Location(
  id = "de:0800:1234",
  name = "Gare de Lyon",
  description = "Paris 12e",
  coordinates = LatLon(lat = 48.8443, lon = 2.3737),
  kind = PlaceKind.STOP,
  servedModes = listOf(TransitMode.RAIL, TransitMode.SUBWAY),
)

@Preview(showBackground = true, name = "Recherche vide, thème clair")
@Preview(showBackground = true, name = "Recherche vide, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Recherche vide, texte à 200 %", fontScale = 2f)
@Composable
private fun SearchCardEmptyPreview() {
  EscaleTheme(dynamicColor = false) {
    SearchCard(
      state = SearchUiState(
        chips = listOf(
          QuickChip.Saved(SavedPlaceKind.HOME, previewFrom),
          QuickChip.Saved(SavedPlaceKind.WORK, previewTo),
        ),
      ),
      actions = SearchActions(),
      padding = PaddingValues(),
    )
  }
}

@Preview(showBackground = true, name = "Recherche remplie, thème clair")
@Preview(showBackground = true, name = "Recherche remplie, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Recherche remplie, texte à 200 %", fontScale = 2f)
@Composable
private fun SearchCardFilledPreview() {
  EscaleTheme(dynamicColor = false) {
    SearchCard(
      state = SearchUiState(
        from = previewFrom,
        to = previewTo,
        time = TimeChoice.ArriveBy(Instant.parse("2025-09-05T07:00:00Z")),
      ),
      actions = SearchActions(),
      padding = PaddingValues(),
    )
  }
}
