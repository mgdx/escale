package io.github.mgdx.escale.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiKind
import io.github.mgdx.escale.ui.map.poiCategoryLabel
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'écran « Couches de la carte » (SPEC.md § 5.6), qui décide de ce que la carte montre.
 *
 * « Un réglage permet de masquer complètement les arrêts, les stations en libre-service ou les
 * points d'intérêt, **indépendamment du zoom** » (SPEC.md § 5.7). Quatorze bascules le font, rangées
 * en trois sections : le transport, les repères, puis les commerces et services.
 *
 * **Rien n'y déclenche de requête.** Les arrêts et le libre-service coupent un flux déjà branché ;
 * les douze catégories de points d'intérêt allument ou éteignent une couche que la feuille de
 * style porte déjà, sans jamais l'ajouter ni la retirer (SPEC.md § 5.7, règle 8).
 *
 * L'écran est dédié, et non une poignée de lignes de plus dans Réglages → Affichage : quatorze
 * bascules noyées entre le thème et le format de l'heure auraient rendu les deux illisibles.
 */
@Composable
fun MapLayersScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  MapLayersContent(
    display = uiState.display,
    // Chaque bascule est enregistrée au lâcher, comme tout réglage de cet écran : ni bouton
    // « Appliquer », ni état à restituer après une rotation.
    onDisplayChanged = viewModel::updateDisplayPreferences,
    onBack = onBack,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLayersContent(
  display: DisplayPreferences,
  onDisplayChanged: (DisplayPreferences) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.settings_map_layers_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        // À 200 % d'agrandissement, quatorze bascules ne tiennent dans aucune hauteur d'écran : sans
        // défilement, les dernières seraient inatteignables (SPEC.md § 9).
        .verticalScroll(rememberScrollState()),
    ) {
      Text(
        text = stringResource(R.string.settings_map_layers_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = HintHorizontalPadding, vertical = HintVerticalPadding),
      )

      SettingsSectionHeader(title = stringResource(R.string.settings_map_layers_section_transport))
      SettingsSwitchItem(
        title = stringResource(R.string.settings_show_stops_title),
        description = stringResource(R.string.settings_show_stops_description),
        checked = display.showStops,
        onCheckedChange = { onDisplayChanged(display.copy(showStops = it)) },
      )
      SettingsSwitchItem(
        title = stringResource(R.string.settings_show_rentals_title),
        description = stringResource(R.string.settings_show_rentals_description),
        checked = display.showRentals,
        onCheckedChange = { onDisplayChanged(display.copy(showRentals = it)) },
      )

      SettingsSectionHeader(title = stringResource(R.string.settings_map_layers_section_landmarks))
      PoiSection(kind = PoiKind.LANDMARK, display = display, onDisplayChanged = onDisplayChanged)

      SettingsSectionHeader(title = stringResource(R.string.settings_map_layers_section_shops))
      PoiSection(kind = PoiKind.SHOP, display = display, onDisplayChanged = onDisplayChanged)

      SettingsBottomSpacer()
    }
  }
}

/**
 * Les catégories d'une section, dans l'ordre de l'énumération.
 *
 * L'ordre des bascules est celui de `PoiCategory`, et c'est délibéré : la liste affichée et la liste
 * de `:core` ne peuvent pas diverger, et une catégorie ajoutée demain apparaît d'elle-même à sa
 * place, avec son libellé et sa description.
 */
@Composable
private fun PoiSection(kind: PoiKind, display: DisplayPreferences, onDisplayChanged: (DisplayPreferences) -> Unit) {
  PoiCategory.entries.filter { it.kind == kind }.forEach { category ->
    SettingsSwitchItem(
      title = stringResource(poiCategoryLabel(category)),
      description = stringResource(category.descriptionRes()),
      checked = category in display.visiblePoiCategories,
      onCheckedChange = { checked ->
        val visible = if (checked) {
          display.visiblePoiCategories + category
        } else {
          display.visiblePoiCategories - category
        }
        onDisplayChanged(display.copy(visiblePoiCategories = visible))
      },
    )
  }
}

/**
 * Ce que la bascule montre en une phrase : quelques exemples, jamais la liste OpenStreetMap.
 *
 * Le titre, lui, n'est pas ici : c'est le libellé de la catégorie, celui que la fiche affiche déjà
 * (`poiCategoryLabel`). Une catégorie porte un seul nom, à l'écran comme sur la carte.
 */
private fun PoiCategory.descriptionRes(): Int = when (this) {
  PoiCategory.PUBLIC_SERVICES -> R.string.settings_layer_public_services_description
  PoiCategory.EDUCATION -> R.string.settings_layer_education_description
  PoiCategory.CULTURE -> R.string.settings_layer_culture_description
  PoiCategory.HEALTHCARE -> R.string.settings_layer_healthcare_description
  PoiCategory.TOILETS -> R.string.settings_layer_toilets_description
  PoiCategory.FOOD -> R.string.settings_layer_food_description
  PoiCategory.DINING -> R.string.settings_layer_dining_description
  PoiCategory.HEALTH_SHOPS -> R.string.settings_layer_health_shops_description
  PoiCategory.MONEY -> R.string.settings_layer_money_description
  PoiCategory.LODGING -> R.string.settings_layer_lodging_description
  PoiCategory.EVERYDAY_SERVICES -> R.string.settings_layer_everyday_services_description
  PoiCategory.OTHER_SHOPS -> R.string.settings_layer_other_shops_description
}

private val HintHorizontalPadding = 16.dp
private val HintVerticalPadding = 12.dp

@Preview(name = "Couches de la carte, thème clair", showBackground = true)
@Preview(name = "Couches de la carte, thème sombre", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Couches de la carte, texte à 200 %", showBackground = true, fontScale = 2f)
@Composable
private fun MapLayersScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      MapLayersContent(display = DisplayPreferences(), onDisplayChanged = {}, onBack = {})
    }
  }
}
