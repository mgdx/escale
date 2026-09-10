package io.github.mgdx.escale.ui.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.mgdx.escale.ui.map.LocationPermissionDialog
import io.github.mgdx.escale.ui.map.PermissionRequest
import io.github.mgdx.escale.ui.map.manifestPermission

/**
 * La permission de localisation demandée par l'entrée « Ma position » (SPEC.md § 5.1).
 *
 * L'entrée est en tête de liste en permanence : c'est **l'appui** qui demande la permission, jamais
 * l'ouverture de l'écran ni le démarrage (SPEC.md § 11). Le `ViewModel` décide *quand* ; ce
 * composable ne fait que passer la demande au système, parce que seul un composable peut le faire.
 *
 * Le refus réutilise l'explication du bouton de position de la carte — même titre, même phrase,
 * même chemin vers les réglages système — plutôt que d'en écrire une seconde. Sa visibilité ne
 * regarde que cet écran, et non le `ViewModel` : c'est une fenêtre ouverte, pas une donnée de
 * recherche. Elle survit à la rotation par `rememberSaveable`.
 *
 * Une permission accordée ne fait rien de plus que **rejouer l'appui** sur l'entrée : le chemin
 * vers la position reste unique, et il n'y a rien à tenir en double dans le `ViewModel`.
 */
@Composable
internal fun SearchLocationPermission(request: PermissionRequest?, onGranted: () -> Unit) {
  val context = LocalContext.current
  var explanationVisible by rememberSaveable { mutableStateOf(false) }

  // Le dernier jeton déjà passé au système. Sans lui, une recréation d'activité pendant que la
  // fenêtre de permission est ouverte relancerait la même demande par-dessus elle.
  var launchedToken by rememberSaveable { mutableLongStateOf(0L) }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted ->
      // Un refus définitif se reconnaît à cela : le système ne montre plus rien et répond non.
      explanationVisible = !granted
      if (granted) onGranted()
    },
  )
  LaunchedEffect(request?.token) {
    val pending = request ?: return@LaunchedEffect
    if (pending.token == launchedToken) return@LaunchedEffect
    launchedToken = pending.token
    launcher.launch(pending.permission.manifestPermission())
  }

  if (explanationVisible) {
    LocationPermissionDialog(
      onOpenSettings = {
        explanationVisible = false
        context.openApplicationSettings()
      },
      onDismiss = { explanationVisible = false },
    )
  }
}

/** La fiche de l'application dans les réglages système, d'où la permission peut être rendue. */
private fun Context.openApplicationSettings() {
  val target = Uri.fromParts("package", packageName, null)
  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, target)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // Un appareil sans écran de réglages n'a pas à faire planter la recherche.
  }
}
