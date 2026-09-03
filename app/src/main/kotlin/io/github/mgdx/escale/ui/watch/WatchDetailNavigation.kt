package io.github.mgdx.escale.ui.watch

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.ui.common.asMessage
import io.github.mgdx.escale.work.WatchNotifications

/**
 * L'appui sur la notification d'un trajet surveillé ouvre son détail rafraîchi (SPEC.md § 5.5.1).
 *
 * Branché dans `EscaleNavHost` sur le modèle de la navigation vers les prochains départs
 * (docs/architecture.md § 11.4) : l'intention de la notification devient une **demande**, la
 * navigation la consomme, et rien ne se rejoue ensuite.
 *
 * Les trois situations aboutissent au même endroit — application fermée, en arrière-plan, ou
 * ouverte sur un autre écran — parce que la notification recrée l'activité avec son intention
 * (`FLAG_ACTIVITY_CLEAR_TOP` sans `FLAG_ACTIVITY_SINGLE_TOP`, voir `WatchNotifications`).
 *
 * @param onOpenDetail la navigation vers l'écran de détail. Elle n'est appelée qu'une fois le
 *   trajet rafraîchi obtenu et publié : ouvrir l'écran avant l'aurait fait se refermer aussitôt,
 *   faute de trajet à montrer.
 */
@Composable
fun WatchDetailNavigation(onOpenDetail: () -> Unit) {
  val container = appContainer()
  val requests = container.watchOpenRequests
  val viewModel: WatchOpenViewModel = viewModel(factory = WatchOpenViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val intent = LocalActivity.current?.intent
  // `rememberSaveable` survit à la rotation : sans lui, tourner l'écran relirait la même intention
  // et rouvrirait le trajet. Une notification suivante, elle, recrée l'activité et repart de faux.
  var handled by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(intent, handled) {
    if (handled) return@LaunchedEffect
    handled = true
    val journeyId = intent?.watchedJourneyId() ?: return@LaunchedEffect
    requests.open(journeyId)
  }
  LaunchedEffect(state.ready) {
    if (state.ready == null) return@LaunchedEffect
    onOpenDetail()
    viewModel.onOpened()
  }
  if (state.loading) WatchOpeningDialog()
  val error = state.error
  if (error != null) {
    WatchOpenFailureDialog(
      message = error.asMessage(),
      onRetry = viewModel::onRetry,
      onDismiss = viewModel::onDismissError,
    )
  }
}

/**
 * L'identifiant du trajet surveillé porté par l'intention, ou `null` si elle n'en porte pas.
 *
 * `-1` est la valeur d'absence : un identifiant de favori vaut toujours au moins 1, Room
 * n'attribuant pas de zéro.
 */
private fun Intent.watchedJourneyId(): Long? =
  getLongExtra(WatchNotifications.EXTRA_JOURNEY_ID, MISSING).takeIf { it != MISSING }

/**
 * L'attente pendant que le trajet est redemandé au serveur.
 *
 * Elle est visible parce qu'un appui sur une notification doit produire quelque chose tout de
 * suite : la requête dure d'ordinaire moins d'une seconde, mais sur un réseau lent le silence
 * ferait croire que rien ne s'est passé (SPEC.md § 8).
 */
@Composable
private fun WatchOpeningDialog() {
  AlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    icon = { CircularProgressIndicator() },
    text = { Text(text = stringResource(R.string.watch_opening)) },
  )
}

/** L'échec de l'ouverture : il se dit, avec de quoi réessayer (SPEC.md § 8). */
@Composable
private fun WatchOpenFailureDialog(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.watch_open_failed)) },
    text = { Text(text = message) },
    confirmButton = {
      TextButton(onClick = onRetry) { Text(text = stringResource(R.string.action_retry)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.watch_close)) }
    },
  )
}

private const val MISSING = -1L
