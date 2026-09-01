package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La frise de SPEC.md § 5.2 : « à l'échelle de leur durée », mais lisible — d'où le plancher.
 */
class JourneyTimelineTest {

  private val tolerance = 1e-4f

  @Test
  fun `un trajet sans portion ne produit aucune frise`() {
    assertEquals(emptyList<TimelineSegment>(), JourneyTimeline.of(emptyList()))
  }

  @Test
  fun `les parts sont proportionnelles a la duree quand aucune portion n est trop courte`() {
    val segments = JourneyTimeline.of(listOf(walk(0, 20), transit(20, 60), walk(80, 20)))
    assertEquals(0.2f, segments[0].weight, tolerance)
    assertEquals(0.6f, segments[1].weight, tolerance)
    assertEquals(0.2f, segments[2].weight, tolerance)
  }

  @Test
  fun `la somme des parts vaut toujours un`() {
    val segments = JourneyTimeline.of(listOf(walk(0, 1), transit(1, 118), walk(119, 1)))
    assertEquals(1f, segments.sumOf { it.weight.toDouble() }.toFloat(), tolerance)
  }

  @Test
  fun `une portion tres courte garde une part visible`() {
    // Une correspondance d'une minute dans un trajet de deux heures : sans plancher, elle vaudrait
    // moins d'un pour cent de la largeur, c'est-à-dire un trait invisible.
    val segments = JourneyTimeline.of(listOf(transit(0, 60), walk(60, 1), transit(61, 59)))
    assertEquals(JourneyTimeline.MINIMUM_WEIGHT, segments[1].weight, tolerance)
    assertTrue(segments[0].weight > segments[2].weight)
  }

  @Test
  fun `la place reprise aux portions longues respecte leurs proportions`() {
    val segments = JourneyTimeline.of(listOf(transit(0, 90), walk(90, 1), transit(91, 30)))
    // Les deux portions longues gardent leur rapport de durée, à la place cédée près.
    assertTrue(segments[0].weight > segments[2].weight * 2f)
    assertEquals(1f, segments.sumOf { it.weight.toDouble() }.toFloat(), tolerance)
  }

  @Test
  fun `des portions de duree nulle se partagent la frise a parts egales`() {
    val segments = JourneyTimeline.of(listOf(walk(0, 0), walk(0, 0)))
    assertEquals(0.5f, segments[0].weight, tolerance)
    assertEquals(0.5f, segments[1].weight, tolerance)
  }

  @Test
  fun `une frise plus longue que le plancher ne le permet reste equitable`() {
    // Vingt portions à 6 % feraient 120 % : le plancher cède, et personne n'est privilégié.
    val legs = List(20) { walk(it.toLong(), 1) }
    val segments = JourneyTimeline.of(legs)
    segments.forEach { assertEquals(0.05f, it.weight, tolerance) }
  }
}
