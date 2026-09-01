package io.github.mgdx.escale.ui.results

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Emplacement de la **feuille de résultats** (SPEC.md § 5.2), branché dans le `NavHost` au point
 * d'appel de `HomeScreen` (docs/architecture.md § 11.4).
 *
 * Il est vide, et c'est volontaire : le lot « résultats » remplit ce corps, dans ce fichier, sans
 * jamais toucher à `HomeScreen` ni à `EscaleNavHost`. C'est précisément ce qui lui évite d'entrer
 * en conflit avec le lot « recherche », qui travaille en parallèle sur le même écran.
 *
 * La feuille lit la recherche en cours dans `SearchSession`, exposée par `AppContainer` : elle n'a
 * rien à demander au lot « recherche », et réciproquement.
 *
 * `HomeScreen` mesure la hauteur occupée par cet emplacement et la reporte sur le `padding` de la
 * caméra : c'est ce qui permettra au cadrage d'un trajet de tenir compte de la feuille ouverte
 * (SPEC.md § 5.7, règle 9). La feuille n'a donc pas à publier sa hauteur.
 *
 * @param padding les encarts système transmis par `HomeScreen`.
 */
// Les deux paramètres sont inutilisés tant que l'emplacement est vide, et c'est tout l'objet du
// contrat : ils fixent la signature contre laquelle le lot suivant code. Les retirer obligerait à
// modifier `EscaleNavHost` au moment de les remettre, c'est-à-dire exactement le conflit de fusion
// que ce fichier existe pour éviter.
@Suppress("UnusedParameter")
@Composable
fun ResultsSheetSlot(padding: PaddingValues, modifier: Modifier = Modifier) {
  // Emplacement laissé vide pour le lot « résultats ».
}
