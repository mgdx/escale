package io.github.mgdx.escale.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.query.PlanQueryBuilder

/*
 * Les états de la feuille autres que la liste : chargement, état vide illustré, pagination, filtre.
 *
 * L'état d'erreur, lui, n'est pas ici : `ui/common/ErrorMessage` traduit déjà chaque `EscaleError`
 * en message distinct (SPEC.md § 8), et le réécrire en dupliquerait la table.
 */

/** Le temps de la requête. Jamais un écran blanc : l'attente est nommée (SPEC.md § 8). */
@Composable
internal fun ResultsLoading(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(StatePadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(StateSpacing),
  ) {
    CircularProgressIndicator()
    Text(
      text = stringResource(R.string.results_loading),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * L'état vide **explicite** de SPEC.md § 5.2 : jamais une liste vide muette.
 *
 * Le message n'est pas le même selon l'onglet, et ce n'est pas un détail de rédaction : les onglets
 * Voiture, Vélo et À pied envoient un plafond de durée explicite, si bien que l'absence de résultat
 * veut d'abord dire « au-delà de la limite ». La limite affichée est lue dans `PlanQueryBuilder`,
 * qui est aussi celui qui l'envoie : les deux ne peuvent pas diverger.
 */
@Composable
internal fun ResultsEmpty(category: JourneyCategory, modifier: Modifier = Modifier) {
  val limit = PlanQueryBuilder.maxDirectTime(category)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(StatePadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(StateSpacing),
  ) {
    Icon(
      painter = painterResource(category.iconRes()),
      contentDescription = null,
      modifier = Modifier.size(EmptyIconSize),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = stringResource(R.string.results_empty_title), style = MaterialTheme.typography.titleMedium)
    Text(
      text = if (limit == null) {
        stringResource(R.string.results_empty_transit_message)
      } else {
        stringResource(R.string.results_empty_direct_message, durationText(limit))
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    emptyHints(limit == null).forEach { hint ->
      Text(
        text = stringResource(R.string.results_empty_hint_item, hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/**
 * Les suggestions imposées par SPEC.md § 8, celles qui ont un sens pour l'onglet consulté.
 *
 * La première suggestion de l'onglet Transport nomme le rabattement à pied maximal de la requête.
 * Comme le plafond de durée des onglets directs, la valeur est **lue** dans `PlanQueryBuilder` et
 * jamais recopiée dans la chaîne : celui qui l'envoie est celui qui l'annonce.
 */
@Composable
private fun emptyHints(transit: Boolean): List<String> = if (transit) {
  listOf(
    stringResource(
      R.string.results_empty_hint_walk,
      durationText(PlanQueryBuilder.maxPrePostTransitTime()),
    ),
    stringResource(R.string.results_empty_hint_window),
    stringResource(R.string.results_empty_hint_transfers),
    stringResource(R.string.results_empty_hint_time),
  )
} else {
  listOf(
    stringResource(R.string.results_empty_hint_transit),
    stringResource(R.string.results_empty_hint_time),
  )
}

/**
 * « Plus tôt » et « Plus tard », en tête et en pied de liste (SPEC.md § 5.2).
 *
 * Le bouton occupe toute la largeur et au moins 48 dp de haut : c'est la cible tactile minimale de
 * SPEC.md § 9, et elle ne dépend pas de la taille du texte.
 */
@Composable
internal fun PageButton(page: ResultsPage, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  TextButton(
    onClick = onClick,
    enabled = !loading,
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = MinTouchTarget),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
    ) {
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(InlineProgressSize), strokeWidth = InlineProgressStroke)
      } else {
        Icon(
          painter = painterResource(
            when (page) {
              ResultsPage.EARLIER -> R.drawable.ic_expand_less
              ResultsPage.LATER -> R.drawable.ic_expand_more
            },
          ),
          contentDescription = null,
        )
      }
      Text(
        text = stringResource(
          when (page) {
            ResultsPage.EARLIER -> R.string.results_earlier
            ResultsPage.LATER -> R.string.results_later
          },
        ),
      )
    }
  }
}

/**
 * Le filtre de l'onglet Vélo (SPEC.md § 5.2) : vélo personnel, vélo partagé, ou les deux.
 *
 * Il ne s'affiche que si les deux familles cohabitent vraiment dans la liste.
 */
@Composable
internal fun BikeFilterRow(selected: BikeFilter, onSelected: (BikeFilter) -> Unit, modifier: Modifier = Modifier) {
  val label = stringResource(R.string.results_filter_label)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .semantics { contentDescription = label },
    horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BikeFilter.entries.forEach { filter ->
      val isSelected = filter == selected
      FilterChip(
        selected = isSelected,
        onClick = { onSelected(filter) },
        label = { Text(stringResource(filter.labelRes())) },
        // Material ne pose pas de coche tout seul : sans elle, la puce active ne se distinguait
        // que par sa couleur de fond, ce que SPEC.md § 9 interdit. Le pictogramme est muet, l'état
        // « sélectionné » étant déjà annoncé par le rôle de la puce.
        leadingIcon = if (isSelected) {
          {
            Icon(
              painter = painterResource(R.drawable.ic_check_circle),
              contentDescription = null,
              modifier = Modifier.size(FilterIconSize),
            )
          }
        } else {
          null
        },
        modifier = Modifier.heightIn(min = MinTouchTarget),
      )
    }
  }
}

private fun BikeFilter.labelRes(): Int = when (this) {
  BikeFilter.ALL -> R.string.results_filter_all
  BikeFilter.OWN -> R.string.results_filter_own
  BikeFilter.SHARED -> R.string.results_filter_shared
}

private val FilterIconSize: Dp = 18.dp
private val StatePadding: Dp = 24.dp
private val StateSpacing: Dp = 8.dp
private val EmptyIconSize: Dp = 48.dp
private val ButtonSpacing: Dp = 8.dp
private val InlineProgressSize: Dp = 20.dp
private val InlineProgressStroke: Dp = 2.dp

/** Cible tactile minimale de SPEC.md § 9. */
private val MinTouchTarget: Dp = 48.dp
