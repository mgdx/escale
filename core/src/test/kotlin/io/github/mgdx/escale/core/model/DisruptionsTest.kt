package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Les perturbations en vigueur, et elles seules (SPEC.md § 5.2). */
class DisruptionsTest {

  private val noon: Instant = Instant.parse("2026-09-02T12:00:00Z")

  @Test
  fun `une perturbation sans periode est toujours en vigueur`() {
    val alert = disruption("Travaux")
    assertTrue(alert.isInEffect(noon))
    assertEquals(listOf(alert), Disruptions.inEffect(listOf(alert), noon))
  }

  @Test
  fun `une perturbation a venir n est pas en vigueur`() {
    val alert = disruption(
      "Triathlon",
      TimeWindow(start = Instant.parse("2026-09-05T06:00:00Z"), end = Instant.parse("2026-09-06T18:00:00Z")),
    )
    assertFalse(alert.isInEffect(noon))
    assertTrue(Disruptions.inEffect(listOf(alert), noon).isEmpty())
    assertEquals(listOf(alert), Disruptions.upcoming(listOf(alert), noon))
  }

  @Test
  fun `une perturbation en cours est en vigueur`() {
    val alert = disruption(
      "Grève",
      TimeWindow(start = Instant.parse("2026-09-02T06:00:00Z"), end = Instant.parse("2026-09-02T20:00:00Z")),
    )
    assertTrue(alert.isInEffect(noon))
  }

  @Test
  fun `une borne nulle est une borne ouverte`() {
    assertTrue(TimeWindow(start = null, end = Instant.parse("2026-09-02T20:00:00Z")).contains(noon))
    assertTrue(TimeWindow(start = Instant.parse("2026-09-02T06:00:00Z"), end = null).contains(noon))
    assertTrue(TimeWindow(start = null, end = null).contains(noon))
  }

  @Test
  fun `la fin de periode est exclue`() {
    // Une perturbation qui se termine à midi n'est plus en vigueur à midi.
    assertFalse(TimeWindow(start = null, end = noon).contains(noon))
    assertTrue(TimeWindow(start = noon, end = null).contains(noon))
  }

  @Test
  fun `plusieurs periodes suffisent si l une couvre l instant`() {
    val alert = disruption(
      "Week-end",
      TimeWindow(start = Instant.parse("2026-09-01T06:00:00Z"), end = Instant.parse("2026-09-01T20:00:00Z")),
      TimeWindow(start = Instant.parse("2026-09-02T06:00:00Z"), end = Instant.parse("2026-09-02T20:00:00Z")),
    )
    assertTrue(alert.isInEffect(noon))
  }

  @Test
  fun `le meme message porte par deux portions n est compte qu une fois`() {
    val alert = disruption("Travaux")
    assertEquals(listOf(alert), Disruptions.inEffect(listOf(alert, alert), noon))
  }

  @Test
  fun `sans instant de reference, tout est rendu`() {
    // Cacher une perturbation faute de savoir l'heure serait le mauvais côté de l'erreur.
    val future = disruption("Plus tard", TimeWindow(start = Instant.parse("2026-10-01T06:00:00Z"), end = null))
    assertEquals(listOf(future), Disruptions.inEffect(listOf(future), at = null))
    assertTrue(Disruptions.upcoming(listOf(future), at = null).isEmpty())
  }

  @Test
  fun `la gravite annoncee est la plus forte des perturbations`() {
    val alerts = listOf(
      disruption("Info").copy(severity = DisruptionSeverity.INFO),
      disruption("Majeure").copy(severity = DisruptionSeverity.SEVERE),
      disruption("Avertissement").copy(severity = DisruptionSeverity.WARNING),
    )
    assertEquals(DisruptionSeverity.SEVERE, Disruptions.worstSeverity(alerts))
    assertNull(Disruptions.worstSeverity(emptyList()))
  }

  @Test
  fun `une gravite absente ne prend pas le pas sur une gravite connue`() {
    val alerts = listOf(
      disruption("Sans gravité"),
      disruption("Information").copy(severity = DisruptionSeverity.INFO),
    )
    assertEquals(DisruptionSeverity.INFO, Disruptions.worstSeverity(alerts))
  }

  @Test
  fun `une perturbation deja annoncee ailleurs n est pas repetee sur l arret`() {
    val course = disruption("Retard prévisible")
    val stop = disruption("Arrêt non desservi")

    assertEquals(listOf(stop), Disruptions.excluding(listOf(course, stop), listOf(course)))
    assertTrue(Disruptions.excluding(listOf(course), listOf(course)).isEmpty())
    assertEquals(listOf(stop), Disruptions.excluding(listOf(stop, stop), emptyList()))
  }

  @Test
  fun `deux messages qui ne different que par leur periode restent deux perturbations`() {
    val today = disruption("Travaux", TimeWindow(start = noon, end = null))
    val tomorrow = disruption("Travaux", TimeWindow(start = noon.plusSeconds(SECONDS_PER_DAY), end = null))

    assertEquals(listOf(tomorrow), Disruptions.excluding(listOf(today, tomorrow), listOf(today)))
  }

  private fun disruption(header: String, vararg periods: TimeWindow) = Disruption(
    headerText = header,
    descriptionText = "Description",
    periods = periods.toList(),
  )

  private companion object {
    const val SECONDS_PER_DAY = 86_400L
  }
}
