package io.github.mgdx.escale.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * **Pourquoi un dialogue plein écran plutôt qu'une destination de navigation ?** L'écran « À
 * propos » est le seul point d'entrée de ce texte, et il n'a pas d'état à conserver : lui ajouter
 * une route ferait porter au graphe de navigation une destination sans vie propre.
 *
 * Le fichier fait plusieurs dizaines de milliers de caractères : il est lu **hors du fil
 * principal**, et affiché par paragraphes dans une liste paresseuse, jamais dans un unique `Text`.
 */
@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    // Un texte de licence n'a rien à faire dans la largeur réduite d'une boîte de dialogue.
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    LicenseContent(onClose = onDismiss)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicenseContent(onClose: () -> Unit, modifier: Modifier = Modifier, paragraphs: List<String>? = null) {
  val loaded = paragraphs ?: rememberLicenseParagraphs()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.about_license_full_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_close),
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
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
      onClose = {},
      paragraphs = listOf(
        "                    GNU GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007",
        " Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>",
      ),
    )
  }
}
