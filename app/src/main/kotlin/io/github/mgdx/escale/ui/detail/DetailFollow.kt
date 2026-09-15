package io.github.mgdx.escale.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationManagerCompat
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.follow.FollowWording
import io.github.mgdx.escale.ui.common.openApplicationSettings
import io.github.mgdx.escale.ui.results.rememberTimeFormatter

/*
 * Le suivi de trajet vu depuis la fiche (SPEC.md § 5.3.1) : le bouton de la barre, le bandeau, la
 * confirmation de remplacement, et la demande de permission de notification.
 */

/**
 * Le bouton de la barre : « Suivre ce trajet », ou « Arrêter le suivi » quand c'est ce trajet-ci
 * qui est suivi. Deux dessins et deux libellés, jamais une nuance de couleur (SPEC.md § 9).
 *
 * Il n'est proposé que dans les conditions de `FollowLimits` : hors de la fenêtre de lancement,
 * il n'apparaît pas, comme la spec le demande. Absent aussi des aperçus, qui n'ont pas de suivi.
 */
@Composable
internal fun FollowBarAction(state: DetailUiState, actions: DetailActions) {
  val request = actions.onFollowRequested ?: return
  if (state.follow != null) {
    IconButton(onClick = actions.onFollowStop) {
      Icon(
        painter = painterResource(R.drawable.ic_notifications_off),
        contentDescription = stringResource(R.string.follow_action_stop),
      )
    }
    return
  }
  if (!state.followAvailable) return
  val start = rememberFollowStart(onGranted = request)
  IconButton(onClick = start) {
    Icon(
      painter = painterResource(R.drawable.ic_notifications),
      contentDescription = stringResource(R.string.follow_action_start),
    )
  }
}

/**
 * La permission `POST_NOTIFICATIONS`, demandée **à l'appui sur le bouton** et nulle part ailleurs
 * (SPEC.md § 5.3.1 et § 11).
 *
 * Trois cas, dans l'ordre : les notifications sont permises, le suivi commence ; Android 13 et
 * suivants sans permission, le système la demande, et un accord rejoue simplement l'appui ; un
 * refus — définitif, ou des notifications coupées dans les réglages sur une version antérieure —
 * ouvre une explication et le chemin vers les réglages. **Pas de suivi muet** : sans notification,
 * rien ne démarre.
 */
@Composable
private fun rememberFollowStart(onGranted: () -> Unit): () -> Unit {
  val context = LocalContext.current
  val granted by rememberUpdatedState(onGranted)
  var explanationVisible by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { ok -> if (ok) granted() else explanationVisible = true },
  )
  if (explanationVisible) {
    FollowPermissionDialog(
      onOpenSettings = {
        explanationVisible = false
        context.openApplicationSettings()
      },
      onDismiss = { explanationVisible = false },
    )
  }
  return remember(context, launcher) {
    {
      when {
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> granted()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else -> explanationVisible = true
      }
    }
  }
}

/**
 * Le bandeau du suivi, en tête de la fiche : la même progression que la notification, mise en mots
 * par le même `FollowWording`, et un bouton pour arrêter.
 *
 * Le titre est une région vivante : un lecteur d'écran annonce chaque échéance sans que l'usager
 * ait à relire la fiche (SPEC.md § 9).
 */
@Composable
internal fun FollowBanner(state: FollowState, onStop: () -> Unit) {
  val context = LocalContext.current
  val formatTime = rememberTimeFormatter()
  val wording = remember(context, formatTime, state) { FollowWording(context, formatTime).of(state) }
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    // Le bouton est sous le texte, et non à côté : à 200 % de taille de police (SPEC.md § 9), un
    // bouton en bout de ligne écrasait le texte sur un tiers de la largeur.
    Column(modifier = Modifier.padding(start = CardPadding, top = CardPadding, end = CardPadding)) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_notifications),
          contentDescription = null,
          modifier = Modifier.size(RowIconSize),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(
          modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
          verticalArrangement = Arrangement.spacedBy(CardSpacing / 2),
        ) {
          Text(
            text = wording.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          wording.text?.let {
            Text(
              text = it,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Text(
            text = stringResource(R.string.follow_banner_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      TextButton(onClick = onStop, modifier = Modifier.align(Alignment.End)) {
        Text(text = stringResource(R.string.follow_action_stop))
      }
    }
  }
}

/** Un seul trajet se suit à la fois : en suivre un autre remplace le précédent, après confirmation. */
@Composable
internal fun FollowReplaceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.follow_replace_title)) },
    text = { Text(text = stringResource(R.string.follow_replace_text)) },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.follow_replace_confirm)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.follow_replace_cancel)) }
    },
  )
}

/** Le refus de la permission : le suivi n'est pas lancé, et l'écran l'explique (SPEC.md § 5.3.1). */
@Composable
private fun FollowPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.follow_permission_title)) },
    text = { Text(text = stringResource(R.string.follow_permission_text)) },
    confirmButton = {
      TextButton(onClick = onOpenSettings) { Text(text = stringResource(R.string.follow_permission_action)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.follow_replace_cancel)) }
    },
  )
}

/** La portion que le suivi met en évidence : celle du véhicule à bord, ou du cheminement en cours. */
internal fun FollowState?.currentLegIndex(): Int? = when (this) {
  is FollowState.OnBoard -> legIndex
  is FollowState.Connecting -> streetIndex
  else -> null
}

/** Le prochain arrêt de la portion [legIndex], quand l'usager est à bord de celle-ci. */
internal fun FollowState?.nextStopIndexOf(legIndex: Int): Int? =
  (this as? FollowState.OnBoard)?.takeIf { it.legIndex == legIndex }?.nextStopIndex
