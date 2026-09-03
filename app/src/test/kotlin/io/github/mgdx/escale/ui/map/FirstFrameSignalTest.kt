package io.github.mgdx.escale.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La bascule de la mesure de démarrage (SPEC.md § 5.7).
 *
 * MapLibre n'est pas nécessaire pour la vérifier : tout ce qui compte est du Kotlin pur, et c'est
 * précisément pourquoi la bascule est une classe à part plutôt que trois lignes noyées dans
 * `MapInstance`, qui, lui, ne s'instancie pas hors d'un appareil.
 */
class FirstFrameSignalTest {

  @Test
  fun `avant la premiere image le temoin est faux`() {
    assertFalse(FirstFrameSignal().rendered.value)
  }

  @Test
  fun `la premiere image bascule le temoin et demande le retrait de l ecouteur`() {
    val signal = FirstFrameSignal()

    assertTrue(signal.markRendered())
    assertTrue(signal.rendered.value)
  }

  @Test
  fun `les images suivantes ne redemandent rien`() {
    // Le cas qui coûte : l'écouteur MapLibre se déclenche à chaque image rendue. Seul le premier
    // appel doit demander son retrait ; les suivants — s'il en arrive un avant que le retrait
    // n'ait pris effet — ne doivent rien déclencher du tout (règle 6 du § 5.7).
    val signal = FirstFrameSignal()
    signal.markRendered()

    assertFalse(signal.markRendered())
    assertFalse(signal.markRendered())
    assertTrue(signal.rendered.value)
  }

  @Test
  fun `le temoin est un etat durable, pas un evenement`() {
    // La carte survit à l'activité (règle 8 du § 5.7) : au second lancement, l'image est déjà
    // rendue et l'écouteur ne se redéclenchera jamais. Qui collecte le flux après coup doit
    // malgré tout obtenir la valeur tout de suite, sans quoi `reportFullyDrawn()` ne partirait
    // qu'au premier démarrage à froid du processus.
    val signal = FirstFrameSignal()
    signal.markRendered()

    assertTrue(signal.rendered.value)
  }
}
