package io.github.mgdx.escale.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Écran d'accueil provisoire du jalon 1.
 *
 * Il deviendra la carte plein écran de SPEC.md § 5.1 au jalon 2 ; en attendant, il porte le nom de
 * l'application, son accroche, et le chemin vers les réglages du serveur.
 */
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
  Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(innerPadding)
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.app_tagline),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
      )
      Button(
        onClick = onOpenSettings,
        modifier = Modifier.padding(top = 32.dp),
      ) {
        Text(text = stringResource(R.string.action_settings))
      }
    }
  }
}

@Preview(showBackground = true, name = "Accueil, thème clair")
@Preview(
  showBackground = true,
  name = "Accueil, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    HomeScreen(onOpenSettings = {})
  }
}
