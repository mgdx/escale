package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Les paramètres de `/api/v6/stoptimes` et `/api/v6/trip` (SPEC.md § 5.4 et § 5.3). */
class StopTimesQueryBuilderTest {

  private val stopId = "de-DELFI_de:02000:10950"
  private val time: Instant = Instant.parse("2026-09-02T05:48:00Z")

  @Test
  fun `les cinq parametres de la spec partent avec les noms de l'OpenAPI`() {
    val parameters = StopTimesQueryBuilder.stopTimes(stopId, time, count = 20)
    assertEquals(stopId, parameters["stopId"])
    assertEquals("2026-09-02T05:48:00Z", parameters["time"])
    assertEquals("20", parameters["n"])
    assertEquals("true", parameters["withAlerts"])
    assertEquals("LATER", parameters["direction"])
  }

  @Test
  fun `les secondes sont toujours ecrites, meme nulles`() {
    // `Instant.toString()` les omet quand elles valent zéro, ce que tous les analyseurs
    // n'acceptent pas. Même précaution que pour `plan`.
    val parameters = StopTimesQueryBuilder.stopTimes(stopId, Instant.parse("2026-09-02T06:00:00Z"), count = 5)
    assertEquals("2026-09-02T06:00:00Z", parameters["time"])
  }

  @Test
  fun `le filtre par mode n'envoie que des feuilles`() {
    val parameters = StopTimesQueryBuilder.stopTimes(
      stopId,
      time,
      count = 20,
      modes = DepartureModeFilter.TRAIN.requestModes,
    )
    val modes = checkNotNull(parameters["mode"]).split(",").toSet()
    assertEquals(DepartureModeFilter.TRAIN.requestModes.map { it.name }.toSet(), modes)
    // Le piège de docs/architecture.md § 11.5 : `RAIL` couvre `SUBWAY`, l'envoyer élargirait le
    // filtre sans que rien ne le signale.
    assertFalse(modes.contains(TransitMode.RAIL.name))
    assertFalse(modes.contains(TransitMode.SUBWAY.name))
  }

  @Test
  fun `un ensemble de modes vide est omis, jamais envoye vide`() {
    // Côté serveur, ne pas envoyer `mode` veut dire « tous les modes » ; un `mode` vide n'aurait
    // pas le même sens.
    assertNull(StopTimesQueryBuilder.stopTimes(stopId, time, count = 20, modes = emptySet())["mode"])
  }

  @Test
  fun `une page suivante ne part qu'avec son curseur`() {
    val parameters = StopTimesQueryBuilder.stopTimes(stopId, time, count = 20, cursor = "LATER|1788328140")
    assertEquals("LATER|1788328140", parameters["pageCursor"])
    // « This parameter will be ignored in case pageCursor is set » : envoyer les deux ancrages
    // ferait dépendre la page de deux origines contradictoires.
    assertNull(parameters["time"])
    assertNull(parameters["direction"])
    // Le reste de la requête, lui, est renvoyé tel quel — même règle qu'au § 5.2 pour `plan`.
    assertEquals(stopId, parameters["stopId"])
    assertEquals("20", parameters["n"])
  }

  @Test
  fun `demander des arrivees renverse le sens par defaut`() {
    val parameters = StopTimesQueryBuilder.stopTimes(stopId, time, count = 20, arriveBy = true)
    assertEquals("true", parameters["arriveBy"])
    assertEquals("EARLIER", parameters["direction"])
  }

  @Test
  fun `des departs ne demandent pas arriveBy`() {
    // Une requête plus courte est une requête moins fragile : la valeur par défaut du serveur est
    // déjà celle qu'on veut.
    assertNull(StopTimesQueryBuilder.stopTimes(stopId, time, count = 20)["arriveBy"])
  }

  @Test
  fun `sans heure, le serveur prend la sienne`() {
    // Comme pour `plan` avec « Maintenant » : l'heure de la saisie n'est pas celle de la requête.
    assertNull(StopTimesQueryBuilder.stopTimes(stopId, time = null, count = 20)["time"])
  }

  @Test
  fun `la desserte d'une course ne demande pas la geometrie`() {
    // SPEC.md § 7.6 : la géométrie ne se demande que là où elle sert, et cet écran ne trace rien.
    val parameters = StopTimesQueryBuilder.trip("20260902_07:34_de-DELFI_3383408655")
    assertEquals("20260902_07:34_de-DELFI_3383408655", parameters["tripId"])
    assertEquals("false", parameters["detailedLegs"])
    assertTrue(StopTimesQueryBuilder.trip("x", detailedLegs = true)["detailedLegs"] == "true")
  }
}
