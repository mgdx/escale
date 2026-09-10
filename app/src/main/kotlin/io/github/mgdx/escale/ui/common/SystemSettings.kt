package io.github.mgdx.escale.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * La fiche de l'application dans les réglages système, d'où la permission peut être rendue.
 *
 * La carte comme la recherche y envoient l'utilisateur après un refus définitif : le chemin est le
 * même, il n'a donc pas à être écrit deux fois. Un appareil dépourvu d'écran de réglages ne fait
 * planter ni l'un ni l'autre — l'appui y reste alors sans effet.
 */
internal fun Context.openApplicationSettings() {
  val target = Uri.fromParts("package", packageName, null)
  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, target)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // Un appareil sans écran de réglages n'a pas à faire planter l'application.
  }
}
