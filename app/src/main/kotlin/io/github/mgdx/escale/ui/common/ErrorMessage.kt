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
 * Le message à montrer pour un échec (SPEC.md § 8).
 *
 * Chaque cas a son propre libellé : l'usager doit pouvoir distinguer « le serveur ne répond pas »
 * d'« aucun trajet trouvé », et un serveur trop ancien d'une panne de réseau.
 */
@Composable
fun EscaleError.asMessage(): String = when (this) {
  EscaleError.NoNetwork -> stringResource(R.string.error_no_network)

  EscaleError.Timeout -> stringResource(R.string.error_timeout)

  is EscaleError.ServerUnreachable -> stringResource(R.string.error_server_unreachable)

  is EscaleError.ApiVersionTooOld -> stringResource(R.string.error_api_version_too_old)

  // Le serveur donne parfois un message exploitable ; sinon on reste générique.
  is EscaleError.BadRequest -> serverMessage ?: stringResource(R.string.error_unknown)

  is EscaleError.Unknown -> stringResource(R.string.error_unknown)
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
