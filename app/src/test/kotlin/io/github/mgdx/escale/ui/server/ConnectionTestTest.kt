package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.model.ServerTestProgress
import io.github.mgdx.escale.core.model.ServerTestStep
import io.github.mgdx.escale.core.result.EscaleError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC.md § 5.6.1 : la traduction d'un avancement de test en trois lignes d'écran.
 *
 * La règle est pure, donc éprouvée ici sans `ViewModel`, sans réseau et sans répartiteur.
 */
class ConnectionTestTest {

  @Test
  fun `chaque etape ne touche que sa propre ligne`() {
    val afterFirst = ConnectionTest(running = true)
      .after(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      .after(ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = true))

    assertEquals(CheckStepState.PASSED, afterFirst.reachable)
    assertEquals(CheckStepState.IDLE, afterFirst.apiVersion)
    assertEquals(CheckStepState.IDLE, afterFirst.tiles)
  }

  @Test
  fun `un test en cours n est ni concluant ni termine`() {
    val running = ConnectionTest(running = true)
      .after(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      .after(ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = true))
      .after(ServerTestProgress.Started(ServerTestStep.API_VERSION))

    assertEquals(CheckStepState.RUNNING, running.apiVersion)
    assertFalse(running.passed)
    assertFalse(running.finished)
  }

  /** SPEC.md § 5.6.1 : l'enregistrement dépend du résultat complet, pas de la première étape verte. */
  @Test
  fun `un test dont il reste une etape n est pas concluant`() {
    val midway = ConnectionTest(running = true)
      .after(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      .after(ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = true))
      .after(ServerTestProgress.Started(ServerTestStep.API_VERSION))
      .after(ServerTestProgress.Finished(ServerTestStep.API_VERSION, passed = true))
      .after(ServerTestProgress.Started(ServerTestStep.TILES))

    assertFalse(midway.passed)
    assertFalse(midway.finished)
  }

  @Test
  fun `un serveur sans tuiles est absent, pas en echec`() {
    val test = complete(tilesAvailable = false)

    assertEquals(CheckStepState.ABSENT, test.tiles)
    // Les tuiles sont facultatives : le test reste concluant (SPEC.md § 5.7).
    assertTrue(test.passed)
  }

  @Test
  fun `une version d API trop ancienne est un echec`() {
    val test = complete(apiCompatible = false)

    assertEquals(CheckStepState.FAILED, test.apiVersion)
    assertFalse(test.passed)
    assertTrue(test.finished)
  }

  @Test
  fun `un serveur injoignable laisse les deux etapes suivantes sans objet`() {
    val test = ConnectionTest(running = true)
      .after(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      .after(
        ServerTestProgress.Finished(
          step = ServerTestStep.REACHABLE,
          passed = false,
          error = EscaleError.NoNetwork,
        ),
      )
      .after(ServerTestProgress.Skipped(ServerTestStep.API_VERSION))
      .after(ServerTestProgress.Skipped(ServerTestStep.TILES))
      .copy(running = false)

    assertEquals(CheckStepState.FAILED, test.reachable)
    assertEquals(CheckStepState.SKIPPED, test.apiVersion)
    assertEquals(CheckStepState.SKIPPED, test.tiles)
    assertEquals(EscaleError.NoNetwork, test.error)
    assertFalse(test.passed)
    assertTrue(test.finished)
  }

  @Test
  fun `un test reussi ne porte aucune erreur`() {
    assertNull(complete().error)
    assertTrue(complete().passed)
    assertTrue(complete().finished)
  }

  private fun complete(apiCompatible: Boolean = true, tilesAvailable: Boolean = true): ConnectionTest =
    ConnectionTest(running = true)
      .after(ServerTestProgress.Started(ServerTestStep.REACHABLE))
      .after(ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = true))
      .after(ServerTestProgress.Started(ServerTestStep.API_VERSION))
      .after(ServerTestProgress.Finished(ServerTestStep.API_VERSION, passed = apiCompatible))
      .after(ServerTestProgress.Started(ServerTestStep.TILES))
      .after(ServerTestProgress.Finished(ServerTestStep.TILES, passed = tilesAvailable))
      .copy(running = false)
}
