package io.github.mgdx.escale.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Emplacement de la **carte de recherche flottante** (SPEC.md § 5.1), branché dans le `NavHost` au
 * point d'appel de `HomeScreen` (docs/architecture.md § 11.4).
 *
 * Il est vide, et c'est volontaire : le lot « recherche » remplit ce corps, dans ce fichier, sans
 * jamais toucher à `HomeScreen` ni à `EscaleNavHost`. C'est précisément ce qui lui évite d'entrer
 * en conflit avec le lot « résultats », qui travaille en parallèle sur le même écran.
 *
 * Ce que la carte de recherche produit — départ, arrivée, heure — n'appartient pas à ce fichier :
 * il vit dans `SearchSession`, exposée par `AppContainer`, parce que la feuille de résultats le
 * consomme (SPEC.md § 5.1 : « dès que Départ et Arrivée sont renseignés, la recherche se lance »).
 *
 * @param padding les encarts système transmis par `HomeScreen`. La carte se place elle-même sous
 *   la barre d'état, à 12 dp des bords.
 */
// Les deux paramètres sont inutilisés tant que l'emplacement est vide, et c'est tout l'objet du
// contrat : ils fixent la signature contre laquelle le lot suivant code. Les retirer obligerait à
// modifier `EscaleNavHost` au moment de les remettre, c'est-à-dire exactement le conflit de fusion
// que ce fichier existe pour éviter.
@Suppress("UnusedParameter")
@Composable
fun SearchCardSlot(padding: PaddingValues, modifier: Modifier = Modifier) {
  // Emplacement laissé vide pour le lot « recherche ».
}
