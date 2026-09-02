package io.github.mgdx.escale.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

// Le réglage de langue de l'application (SPEC.md § 5.6).
//
// Pourquoi passer par le système plutôt que par un réglage interne ? Attribuer une langue à une
// seule application demande soit `androidx.appcompat`, écarté — on ne tire pas une bibliothèque
// entière, avec son thème et ses ressources, pour une fonction dans un projet tout Compose — soit
// `LocaleManager`, qui n'existe qu'à partir d'Android 13 alors que `minSdk` vaut 26. La voie
// retenue est celle qu'Android recommande : la langue par application, tenue par le système,
// alimentée par le `localeConfig` du manifeste.
//
// Conséquence assumée : sous Android 13, l'entrée n'est pas affichée et Escale suit la langue de
// l'appareil. Une commande sans effet vaut moins que pas de commande du tout (SPEC.md § 9).

/**
 * Vrai quand la plateforme sait porter une langue par application, donc à partir d'Android 13.
 *
 * C'est la condition d'affichage de l'entrée « Langue », et non la garantie que l'écran s'ouvrira :
 * celle-là ne se connaît qu'en essayant, voir [openAppLanguageSettings].
 */
internal fun appLanguageSettingsExist(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Ouvre l'écran système « Langue de l'application » pour Escale.
 *
 * Rend `false` si rien n'a pu être ouvert, à charge pour l'appelant de le dire à l'usager : un
 * appui sans réponse ni explication est le pire des deux résultats.
 */
internal fun Context.openAppLanguageSettings(): Boolean {
  // Le test de version est ici, et pas seulement au point d'affichage : c'est lui qui autorise la
  // lecture d'une constante introduite en API 33.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
  val application = Uri.fromParts("package", packageName, null)
  // Certains constructeurs retirent l'écran dédié de leur surcouche. La fiche de l'application
  // porte le même réglage depuis Android 13 : elle sert de second essai avant de renoncer.
  return startFirstAvailable(
    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, application),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, application),
  )
}

/**
 * Lance la première intention qui trouve preneur. `FLAG_ACTIVITY_NEW_TASK` parce que le contexte
 * d'un composable n'est pas garanti être une activité, et qu'un écran de réglages système a sa
 * place dans sa propre tâche.
 */
private fun Context.startFirstAvailable(vararg intents: Intent): Boolean = intents.any { intent ->
  try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
  } catch (_: ActivityNotFoundException) {
    // Un appareil sans cet écran n'a pas à faire tomber les réglages : on essaie le suivant.
    false
  }
}
