package io.github.mgdx.escale.ui.common

import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.result.EscaleError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** SPEC.md § 8 : chaque échec que l'usager doit distinguer porte son propre libellé. */
class ErrorMessageTest {

  @Test
  fun `un hote introuvable ne se dit plus le serveur ne repond pas`() {
    // Anomalie A1, relevée sur téléphone : saisir « example.invalid » dans l'écran « Serveur
    // MOTIS » affichait « Le serveur ne répond pas. », qui envoyait chercher une panne inexistante.
    assertEquals(R.string.error_host_not_found, EscaleError.HostNotFound.messageRes())
    assertNotEquals(
      R.string.error_server_unreachable,
      EscaleError.HostNotFound.messageRes(),
    )
  }

  @Test
  fun `un hote introuvable ne se dit pas non plus absence de reseau`() {
    // Le cas est ambigu : le libellé couvre les deux hypothèses au lieu d'en trancher une.
    assertNotEquals(EscaleError.NoNetwork.messageRes(), EscaleError.HostNotFound.messageRes())
  }

  @Test
  fun `les cas de la spec ont chacun leur libelle`() {
    val cases = listOf(
      EscaleError.NoNetwork,
      EscaleError.HostNotFound,
      EscaleError.Timeout,
      EscaleError.ServerUnreachable(statusCode = 500),
      EscaleError.ApiVersionTooOld("/api/v6/plan"),
    )
    assertEquals(cases.size, cases.map { it.messageRes() }.toSet().size)
  }

  @Test
  fun `un refus du serveur retombe sur le libelle generique sans son message`() {
    // Le message du serveur, quand il existe, est affiché tel quel par `asMessage`.
    assertEquals(R.string.error_unknown, EscaleError.BadRequest(serverMessage = null).messageRes())
  }
}
