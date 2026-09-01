package io.github.mgdx.escale.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.BuildConfig
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * Écran « À propos » (SPEC.md § 5.6 et § 4.2).
 *
 * Il porte les attributions que la politique d'usage de Transitous rend obligatoires, la licence de
 * l'application et le lien vers son code source. Tous les liens sont confiés au navigateur du
 * système par une intention externe : SPEC.md § 2 exclut toute WebView.
 *
 * Le texte de la licence, lui, est **embarqué** (`res/raw/gpl_3_0.txt`) : le lien vers
 * `gnu.org/licenses/gpl-3.0.html` rend 403, et une application GPLv3 dont le lien vers sa propre
 * licence ne fonctionne pas est un très mauvais signal pour un relecteur F-Droid. Le lien vers
 * gnu.org reste proposé en complément, jamais en remplacement.
 *
 * Le texte de la licence est un écran à part entière ([LicenseRoute]), pas un dialogue : cet écran
 * n'a donc aucun état à retenir d'une rotation à l'autre, et pas davantage de `ViewModel` — la
 * version vient de `BuildConfig`.
 */
@Composable
fun AboutScreen(
  onBack: () -> Unit,
  onOpenLicense: () -> Unit,
  modifier: Modifier = Modifier,
  versionName: String = BuildConfig.VERSION_NAME,
) {
  AboutContent(onBack = onBack, onOpenLicense = onOpenLicense, versionName = versionName, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutContent(
  onBack: () -> Unit,
  onOpenLicense: () -> Unit,
  versionName: String,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.about_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState()),
    ) {
      Identity(versionName = versionName)
      HorizontalDivider()
      // Le texte intégral est dans l'application : il s'affiche hors ligne, sans dépendre d'un tiers.
      ListItem(
        headlineContent = { Text(text = stringResource(R.string.about_license_link)) },
        supportingContent = { Text(text = stringResource(R.string.about_license_link_subtitle)) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLicense),
      )
      ExternalLink(
        titleRes = R.string.about_license_online,
        subtitleRes = R.string.about_license_online_subtitle,
        urlRes = R.string.about_license_url,
      )
      ExternalLink(
        titleRes = R.string.about_repository,
        subtitleRes = R.string.about_repository_subtitle,
        urlRes = R.string.about_repository_url,
      )
      HorizontalDivider()
      Attributions()
    }
  }
}

/** Nom, version et licence de l'application (SPEC.md § 5.6). */
@Composable
private fun Identity(versionName: String) {
  Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
    Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
    Text(
      text = stringResource(R.string.about_version, versionName),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp),
    )
    Text(
      text = stringResource(R.string.about_license_summary),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 12.dp),
    )
  }
}

/** Les attributions rendues obligatoires par la politique d'usage de Transitous (SPEC.md § 4.2). */
@Composable
private fun Attributions() {
  Text(
    text = stringResource(R.string.about_attributions_title),
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
  )
  Text(
    text = stringResource(R.string.about_attributions_notice),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 24.dp),
  )
  ExternalLink(
    titleRes = R.string.about_transitous,
    subtitleRes = R.string.about_transitous_subtitle,
    urlRes = R.string.about_transitous_url,
  )
  ExternalLink(
    titleRes = R.string.about_openstreetmap,
    subtitleRes = R.string.about_openstreetmap_subtitle,
    urlRes = R.string.about_openstreetmap_url,
  )
}

/** Une entrée qui ouvre un lien dans une application externe, jamais dans une WebView. */
@Composable
private fun ExternalLink(titleRes: Int, subtitleRes: Int, urlRes: Int) {
  val uriHandler = LocalUriHandler.current
  val url = stringResource(urlRes)
  ListItem(
    headlineContent = { Text(text = stringResource(titleRes)) },
    supportingContent = { Text(text = stringResource(subtitleRes)) },
    trailingContent = {
      Icon(
        painter = painterResource(R.drawable.ic_open_in_new),
        contentDescription = stringResource(R.string.settings_action_opens_externally),
      )
    },
    // `runCatching` : un appareil sans navigateur ne doit pas faire tomber l'écran.
    modifier = Modifier.clickable(role = Role.Button) { runCatching { uriHandler.openUri(url) } },
  )
}

@Preview(showBackground = true, name = "À propos, thème clair")
@Preview(
  showBackground = true,
  name = "À propos, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AboutScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    AboutContent(onBack = {}, onOpenLicense = {}, versionName = "1.0.0")
  }
}
