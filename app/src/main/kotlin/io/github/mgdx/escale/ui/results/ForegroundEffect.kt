package io.github.mgdx.escale.ui.results

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Appelle [onForeground] chaque fois que **l'application** revient au premier plan (SPEC.md § 7.4).
 *
 * C'est le cycle de vie de l'activité qui est observé, et non celui de la destination de
 * navigation : passer d'un écran à l'autre à l'intérieur d'Escale n'est pas un retour au premier
 * plan, et déclencher une requête à chaque aller-retour entre la liste et le détail serait
 * exactement le trafic que SPEC.md § 7 cherche à éviter.
 *
 * **Ce n'est pas une minuterie.** Rien n'est répété, rien n'est planifié : l'observateur ne
 * s'exécute qu'aux transitions du système, et l'appelant décide ensuite s'il y a lieu de faire
 * quelque chose — c'est `RealtimeRefreshPolicy`, dans `:core`, qui tranche.
 *
 * Un observateur ajouté à un cycle de vie déjà démarré reçoit aussitôt un `ON_START` : le premier
 * appel a donc lieu à l'affichage de l'écran. La règle des 60 secondes le rend inoffensif — il n'y
 * a alors rien de périmé à rafraîchir.
 */
@Composable
internal fun ForegroundEffect(onForeground: () -> Unit) {
  val owner = LocalActivity.current as? LifecycleOwner ?: return
  val callback by rememberUpdatedState(onForeground)
  DisposableEffect(owner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) callback()
    }
    owner.lifecycle.addObserver(observer)
    onDispose { owner.lifecycle.removeObserver(observer) }
  }
}
