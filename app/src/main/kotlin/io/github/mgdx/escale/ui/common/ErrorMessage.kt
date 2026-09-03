package io.github.mgdx.escale.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * La ressource de libellé d'un échec (SPEC.md § 8).
 *
 * Chaque cas que l'usager doit pouvoir distinguer a **sa propre** chaîne : « le serveur ne répond
 * pas » ne se confond ni avec « aucun trajet trouvé », ni avec une panne de réseau, ni avec un
 * serveur trop ancien.
 *
 * C'est une fonction ordinaire, hors composition : la correspondance entre cas d'erreur et libellé
 * est ainsi vérifiable par un test JVM, sans passer par le rendu.
 */
internal fun EscaleError.messageRes(): Int = when (this) {
  EscaleError.NoNetwork -> R.string.error_no_network

  // Le nom d'hôte ne se résout pas. La cause est aussi bien une adresse fautive qu'un appareil
  // hors ligne : le libellé nomme les deux hypothèses plutôt que d'en choisir une fausse.
  EscaleError.HostNotFound -> R.string.error_host_not_found

  EscaleError.Timeout -> R.string.error_timeout

  is EscaleError.ServerUnreachable -> R.string.error_server_unreachable

  is EscaleError.ApiVersionTooOld -> R.string.error_api_version_too_old

  // Repli quand le serveur n'a joint aucun message exploitable à son refus.
  is EscaleError.BadRequest -> R.string.error_unknown

  // Une requête supplantée n'est jamais montrée : l'appelant l'ignore et attend le résultat plus
  // récent. Cette branche n'est là que pour garder le `when` exhaustif.
  EscaleError.Superseded -> R.string.error_unknown

  is EscaleError.Unknown -> R.string.error_unknown
}

/** Le message à montrer pour un échec (SPEC.md § 8). */
@Composable
fun EscaleError.asMessage(): String {
  // Le serveur donne parfois un message exploitable ; sinon on reste sur le libellé générique.
  val serverMessage = (this as? EscaleError.BadRequest)?.serverMessage
  return serverMessage ?: stringResource(messageRes())
}

/**
 * Bandeau d'erreur avec son action de reprise (SPEC.md § 8 : jamais un écran vide muet).
 *
 * [onRetry] nul retire le bouton, pour les échecs qu'une nouvelle tentative ne corrigera pas.
 */
@Composable
fun ErrorMessage(error: EscaleError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = error.asMessage(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
      if (onRetry != null) {
        TextButton(onClick = onRetry) {
          Text(text = stringResource(R.string.action_retry))
        }
      }
    }
  }
}

@Preview(showBackground = true, name = "Erreur, texte à 200 %", fontScale = 2f)
@Preview(showBackground = true, name = "Erreur, thème clair")
@Preview(
  showBackground = true,
  name = "Erreur, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ErrorMessagePreview() {
  EscaleTheme(dynamicColor = false) {
    ErrorMessage(error = EscaleError.NoNetwork, onRetry = {})
  }
}
