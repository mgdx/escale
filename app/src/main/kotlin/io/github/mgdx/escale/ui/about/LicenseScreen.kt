package io.github.mgdx.escale.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.ui.theme.EscaleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le texte intégral de la GPLv3, lu depuis `res/raw/gpl_3_0.txt`.
 *
 * **Pourquoi embarquer le texte ?** Le lien vers `gnu.org/licenses/gpl-3.0.html` rend 403 : une
 * application GPLv3 dont le lien vers sa propre licence ne fonctionne pas est un très mauvais
 * signal pour un relecteur F-Droid. Le texte embarqué ne dépend d'aucun tiers et marche hors ligne.
 * Le lien vers gnu.org reste proposé, en complément.
 *
 * **Pourquoi une destination de navigation et non un dialogue ?** Voir [LicenseRoute] : un `Dialog`
 * plein écran ne consomme pas les encarts système et ne se comporte pas comme un écran vis-à-vis
 * du retour arrière matériel.
 *
 * Le fichier fait plusieurs dizaines de milliers de caractères : il est lu **hors du fil
 * principal**, et affiché par paragraphes dans une liste paresseuse, jamais dans un unique `Text`.
 */
@Composable
fun LicenseScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  LicenseContent(onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicenseContent(onBack: () -> Unit, modifier: Modifier = Modifier, paragraphs: List<String>? = null) {
  val loaded = paragraphs ?: rememberLicenseParagraphs()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.about_license_full_title)) },
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
    if (loaded == null) {
      LoadingIndicator(modifier = Modifier.padding(innerPadding))
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Les encarts système entrent dans le `contentPadding` et non dans un `padding` de la
        // liste : le texte défile alors *sous* la barre de navigation, comme partout ailleurs dans
        // l'application, sans que le dernier paragraphe s'y trouve caché.
        contentPadding = innerPadding.plus(horizontal = TextMargin, vertical = TextGap),
        verticalArrangement = Arrangement.spacedBy(TextGap),
      ) {
        items(count = loaded.size) { index ->
          Text(
            text = loaded[index],
            // Le texte est mis en forme à la colonne : une police à chasse fixe le respecte, et le
            // retour à la ligne automatique évite toute troncature à 200 % d'agrandissement.
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

/** Ajoute une marge de texte aux encarts déjà calculés par le `Scaffold`. */
@Composable
private fun PaddingValues.plus(horizontal: Dp, vertical: Dp): PaddingValues {
  val direction = LocalLayoutDirection.current
  return PaddingValues(
    start = calculateStartPadding(direction) + horizontal,
    top = calculateTopPadding() + vertical,
    end = calculateEndPadding(direction) + horizontal,
    bottom = calculateBottomPadding() + vertical,
  )
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

/**
 * Lit la licence hors du fil principal et la découpe en paragraphes.
 *
 * `null` tant que la lecture n'est pas finie : l'écran montre alors son indicateur d'attente plutôt
 * que de bloquer la composition sur une entrée-sortie.
 */
@Composable
private fun rememberLicenseParagraphs(): List<String>? {
  val resources = LocalResources.current
  val paragraphs by produceState<List<String>?>(initialValue = null, resources) {
    value = withContext(Dispatchers.IO) {
      resources.openRawResource(R.raw.gpl_3_0)
        .bufferedReader()
        .use { it.readText() }
        .split(PARAGRAPH_SEPARATOR)
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
    }
  }
  return paragraphs
}

/**
 * Une ligne vide sépare deux paragraphes. Les découper évite un unique `Text` de trente-cinq mille
 * caractères, et donne aux lecteurs d'écran une unité de lecture qui a un sens.
 */
private val PARAGRAPH_SEPARATOR = Regex("\\n[ \\t]*\\n")

/** Marge latérale du texte, celle des autres écrans de réglages. */
private val TextMargin: Dp = 24.dp

/** Écart entre deux paragraphes, repris en marge haute et basse de la liste. */
private val TextGap: Dp = 12.dp

@Preview(showBackground = true, name = "Licence, thème clair")
@Preview(
  showBackground = true,
  name = "Licence, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LicenseContentPreview() {
  EscaleTheme(dynamicColor = false) {
    LicenseContent(
      onBack = {},
      paragraphs = listOf(
        "                    GNU GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007",
        " Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>",
      ),
    )
  }
}
