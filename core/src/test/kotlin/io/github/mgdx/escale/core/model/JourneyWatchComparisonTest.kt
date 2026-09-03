package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.ORIGIN
import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.transit
import io.github.mgdx.escale.core.format.walk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * La décision de notifier (SPEC.md § 5.5.1), cas par cas.
 *
 * Le cas le plus important est celui qui ne produit **rien** : « rien à signaler : aucune
 * notification ». Une fonction qui réveille l'usager pour un trajet normal serait désinstallée
 * avant d'avoir servi.
 */
class JourneyWatchComparisonTest {

  /** L'heure de départ surveillée, celle que l'usager a fixée. */
  private val expected: Instant = ORIGIN

  @Test
  fun `un trajet a l'heure ne produit aucune notification`() {
    val trip = journey(id = "x", legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true)))

    assertNull(JourneyWatchComparison.compare(expected, trip))
  }

  @Test
  fun `un retard inferieur au seuil ne produit aucune notification`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true, delayMinutes = 3)))

    assertNull(JourneyWatchComparison.compare(expected, trip))
  }

  @Test
  fun `un retard superieur au seuil annonce la ligne et l'heure conseillee`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true, delayMinutes = 8)))

    val notice = JourneyWatchComparison.compare(expected, trip)

    assertEquals(WatchIssue.DELAYED, notice?.issue)
    assertEquals("21", notice?.lineName)
    assertEquals(Duration.ofMinutes(8), notice?.delay)
    assertEquals(ORIGIN.plus(Duration.ofMinutes(8)), notice?.suggestedDeparture)
  }

  @Test
  fun `le retard exactement egal au seuil est annonce`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true, delayMinutes = 5)))

    assertEquals(WatchIssue.DELAYED, JourneyWatchComparison.compare(expected, trip)?.issue)
  }

  @Test
  fun `le seuil est reglable`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true, delayMinutes = 3)))
    val settings = WatchAlertSettings(delayThreshold = Duration.ofMinutes(2))

    assertEquals(WatchIssue.DELAYED, JourneyWatchComparison.compare(expected, trip, settings)?.issue)
  }

  @Test
  fun `un trajet de repli qui part plus tard est un retard, meme sans temps reel`() {
    // Le repli sur `plan` a rendu un autre trajet : celui de l'usager n'existe plus, et le plus
    // proche part vingt minutes après. Aucun retard n'est annoncé sur ce trajet-là, et pourtant la
    // différence est utile.
    val trip = journey(legs = listOf(transit(afterMinutes = 20, minutes = 20)))

    val notice = JourneyWatchComparison.compare(expected, trip)

    assertEquals(WatchIssue.DELAYED, notice?.issue)
    assertEquals(ORIGIN.plus(Duration.ofMinutes(20)), notice?.suggestedDeparture)
  }

  @Test
  fun `un trajet de repli qui part plus tot ne reveille personne`() {
    val trip = journey(legs = listOf(transit(afterMinutes = -20, minutes = 20)))

    assertNull(JourneyWatchComparison.compare(expected, trip))
  }

  @Test
  fun `une course supprimee prime sur le retard qu'elle provoque`() {
    val trip = journey(
      legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true, delayMinutes = 30, cancelled = true)),
    )

    val notice = JourneyWatchComparison.compare(expected, trip)

    assertEquals(WatchIssue.CANCELLED, notice?.issue)
    assertEquals("21", notice?.lineName)
  }

  @Test
  fun `une perturbation en vigueur sur une portion est annoncee`() {
    val leg = transit(afterMinutes = 0, minutes = 20, lineName = "M4")
      .copy(alerts = listOf(alert(DisruptionEffect.SIGNIFICANT_DELAYS)))
    val trip = journey(legs = listOf(walk(afterMinutes = 0, minutes = 0), leg))

    val notice = JourneyWatchComparison.compare(expected, trip)

    assertEquals(WatchIssue.DISRUPTED, notice?.issue)
    assertEquals("M4", notice?.lineName)
    // Le message du transporteur est repris tel quel : la notification dit la nature du problème.
    assertEquals("Travaux", notice?.detail)
  }

  @Test
  fun `une perturbation sans effet sur le service ne notifie pas`() {
    val leg = transit(afterMinutes = 0, minutes = 20)
      .copy(alerts = listOf(alert(DisruptionEffect.NO_EFFECT), alert(DisruptionEffect.ADDITIONAL_SERVICE)))
    val trip = journey(legs = listOf(leg))

    assertNull(JourneyWatchComparison.compare(expected, trip))
  }

  @Test
  fun `une perturbation hors de sa periode d'impact ne notifie pas`() {
    val nextMonth = TimeWindow(
      start = ORIGIN.plus(Duration.ofDays(30)),
      end = ORIGIN.plus(Duration.ofDays(31)),
    )
    val leg = transit(afterMinutes = 0, minutes = 20)
      .copy(alerts = listOf(alert(DisruptionEffect.DETOUR, periods = listOf(nextMonth))))
    val trip = journey(legs = listOf(leg))

    assertNull(JourneyWatchComparison.compare(expected, trip))
  }

  @Test
  fun `un trajet devenu impossible est annonce sans ligne ni heure`() {
    val notice = JourneyWatchComparison.compare(expected, refreshed = null)

    assertEquals(WatchIssue.IMPOSSIBLE, notice?.issue)
    assertNull(notice?.lineName)
    assertNull(notice?.suggestedDeparture)
  }

  @Test
  fun `le reglage me prevenir meme si tout va bien est desactive par defaut`() {
    assertEquals(false, WatchAlertSettings().notifyWhenNothingChanged)
    assertEquals(Duration.ofMinutes(5), WatchAlertSettings().delayThreshold)
  }

  @Test
  fun `active, il fait notifier un trajet normal`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 20, realTime = true)))
    val settings = WatchAlertSettings(notifyWhenNothingChanged = true)

    val notice = JourneyWatchComparison.compare(expected, trip, settings)

    assertEquals(WatchIssue.NOTHING, notice?.issue)
    assertEquals(ORIGIN, notice?.suggestedDeparture)
  }

  private fun alert(effect: DisruptionEffect, periods: List<TimeWindow> = emptyList()) = Disruption(
    headerText = "Travaux",
    descriptionText = "Ligne interrompue entre deux stations.",
    severity = DisruptionSeverity.WARNING,
    effect = effect,
    periods = periods,
  )
}
