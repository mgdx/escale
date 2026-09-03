package io.github.mgdx.escale.ui.watch

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.DayOfWeek

/**
 * La bascule « Me prévenir avant le départ » de l'écran de détail (SPEC.md § 5.5.1).
 *
 * **Elle ne s'affiche que si le trajet est en favori**, la spec ne proposant la surveillance que de
 * là. Sinon, ce composable ne dessine rien : un interrupteur qui refuse de s'allumer serait pire
 * que pas d'interrupteur du tout.
 *
 * L'activation passe toujours par l'écran de configuration, jamais par l'interrupteur seul : c'est
 * cet écran qui porte l'avertissement en une phrase que la spec exige avant toute surveillance.
 * L'arrêt, lui, est immédiat.
 */
@Composable
fun WatchSlot(modifier: Modifier = Modifier) {
  val viewModel: WatchViewModel = viewModel(factory = WatchViewModel.factory(appContainer()))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Consulter un trajet le rend frais pour trente minutes : la vérification de fond n'aura pas lieu
  // si elle tombe dans cet intervalle (SPEC.md § 5.5.1).
  LaunchedEffect(state.favoriteId) { viewModel.onJourneyViewed() }
  if (state.favoriteId == null) return
  var configuring by rememberSaveable { mutableStateOf(false) }
  val actions = remember(viewModel) { WatchActions.of(viewModel) }
  WatchRow(
    state = state,
    onConfigure = { configuring = true },
    onDisable = { actions.onWatchedChanged(false) },
    modifier = modifier,
  )
  if (configuring) {
    WatchSheet(state = state, actions = actions, onDismiss = { configuring = false })
  }
}

/** La ligne de l'écran de détail : le libellé, l'état en clair, et l'interrupteur. */
@Composable
private fun WatchRow(
  state: WatchUiState,
  onConfigure: () -> Unit,
  onDisable: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val summary = watchSummary(state)
  ElevatedCard(onClick = onConfigure, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TextSpacing)) {
        Text(text = stringResource(R.string.watch_title), style = MaterialTheme.typography.titleMedium)
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val description = stringResource(R.string.watch_title)
      Switch(
        checked = state.watched,
        // Allumer ouvre l'écran de configuration : l'avertissement de SPEC.md § 5.5.1 doit être lu
        // avant que la moindre requête ne soit programmée.
        onCheckedChange = { enabled -> if (enabled) onConfigure() else onDisable() },
        enabled = state.watched || !state.limitBlocking,
        modifier = Modifier
          .sizeIn(minWidth = TouchTarget, minHeight = TouchTarget)
          .semantics { contentDescription = description },
      )
    }
  }
}

/** Cinq trajets sont déjà surveillés : le dire avant de laisser essayer (`JourneyWatchLimit`). */
@Composable
private fun watchSummary(state: WatchUiState): String = when {
  state.watched -> watchedSummary(state)
  state.limitBlocking -> limitMessage(state.watchedCount)
  else -> stringResource(R.string.watch_summary_off)
}

@Preview(showBackground = true, name = "Surveillance éteinte, thème clair")
@Preview(
  showBackground = true,
  name = "Surveillance éteinte, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(showBackground = true, name = "Surveillance éteinte, texte à 200 %", fontScale = 2f)
@Composable
private fun WatchRowOffPreview() = PreviewRow(WatchUiState(favoriteId = 1))

@Preview(showBackground = true, name = "Surveillance active, thème clair")
@Preview(showBackground = true, name = "Surveillance active, texte à 200 %", fontScale = 2f)
@Composable
private fun WatchRowOnPreview() = PreviewRow(
  WatchUiState(
    favoriteId = 1,
    watched = true,
    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
  ),
)

/** Cinq trajets déjà surveillés : le résumé devient une phrase longue, et c'est le pire cas. */
@Preview(showBackground = true, name = "Limite atteinte, texte à 200 %", fontScale = 2f)
@Composable
private fun WatchRowLimitPreview() = PreviewRow(WatchUiState(favoriteId = 1, watchedCount = 5))

@Composable
private fun PreviewRow(state: WatchUiState) {
  EscaleTheme(dynamicColor = false) {
    Surface {
      WatchRow(state = state, onConfigure = {}, onDisable = {}, modifier = Modifier.padding(12.dp))
    }
  }
}

/** Cible tactile minimale de SPEC.md § 9. */
private val TouchTarget = 48.dp
private val CardPadding = 16.dp
private val RowSpacing = 12.dp
private val TextSpacing = 4.dp
