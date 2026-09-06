package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.transit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le tri de la liste d'un onglet (SPEC.md § 5.2) : par heure de départ, par durée, ou par nombre de
 * correspondances puis durée.
 */
class JourneySortTest {

  /** Part en premier, mais dure une heure. */
  private val slow = journey(id = "lent", legs = listOf(transit(afterMinutes = 0, minutes = 60)))

  /** Part en second et arrive avant tout le monde. */
  private val quick = journey(id = "rapide", legs = listOf(transit(afterMinutes = 10, minutes = 15)))

  private val medium = journey(id = "moyen", legs = listOf(transit(afterMinutes = 20, minutes = 30)))

  private fun feedOf(vararg journeys: Journey) = JourneyFeed(journeys = journeys.toList())

  @Test
  fun `l ordre des departs rend la liste telle quelle`() {
    val feed = feedOf(slow, quick, medium)

    // La liste est déjà recollée par heure de départ : le tri par défaut n'a rien à réordonner.
    assertEquals(feed.journeys, feed.sorted(JourneySort.DEPARTURE))
  }

  @Test
  fun `le tri par duree remonte le trajet le plus court`() {
    val sorted = feedOf(slow, quick, medium).sorted(JourneySort.DURATION)

    assertEquals(listOf("rapide", "moyen", "lent"), sorted.map { it.id })
  }

  @Test
  fun `a duree egale, l ordre des departs est conserve`() {
    val first = journey(id = "premier", legs = listOf(transit(afterMinutes = 0, minutes = 30)))
    val second = journey(id = "second", legs = listOf(transit(afterMinutes = 10, minutes = 30)))
    val third = journey(id = "troisieme", legs = listOf(transit(afterMinutes = 20, minutes = 30)))

    // Le tri est stable : trois trajets d'une demi-heure restent dans l'ordre où ils partent.
    val sorted = feedOf(first, second, third).sorted(JourneySort.DURATION)

    assertEquals(listOf("premier", "second", "troisieme"), sorted.map { it.id })
  }

  @Test
  fun `le tri par correspondances departage les ex aequo par la duree`() {
    val direct = journey(id = "direct", legs = listOf(transit(afterMinutes = 0, minutes = 50)))
    val oneChangeLong = journey(id = "une-longue", legs = listOf(transit(afterMinutes = 5, minutes = 40)))
      .copy(transfers = 1)
    val oneChangeShort = journey(id = "une-courte", legs = listOf(transit(afterMinutes = 10, minutes = 20)))
      .copy(transfers = 1)
    val twoChanges = journey(id = "deux", legs = listOf(transit(afterMinutes = 15, minutes = 10)))
      .copy(transfers = 2)

    val sorted = feedOf(direct, oneChangeLong, oneChangeShort, twoChanges).sorted(JourneySort.TRANSFERS)

    // Le trajet à deux correspondances est le plus court : la durée ne départage que les trajets
    // qui demandent le même nombre de changements.
    assertEquals(listOf("direct", "une-courte", "une-longue", "deux"), sorted.map { it.id })
  }

  @Test
  fun `un trajet a portion supprimee ne remonte pas en tete`() {
    val cancelled = journey(
      id = "supprime",
      legs = listOf(transit(afterMinutes = 30, minutes = 5, cancelled = true)),
    )

    // Cinq minutes, mais le trajet ne circule pas : le présenter comme le plus rapide serait la
    // promesse fausse que SPEC.md § 5.2 interdit déjà sous la languette de l'onglet.
    assertEquals(
      listOf("rapide", "moyen", "lent", "supprime"),
      feedOf(slow, quick, medium, cancelled).sorted(JourneySort.DURATION).map { it.id },
    )
    assertEquals(
      listOf("rapide", "moyen", "lent", "supprime"),
      feedOf(slow, quick, medium, cancelled).sorted(JourneySort.TRANSFERS).map { it.id },
    )
  }

  @Test
  fun `un trajet a portion supprimee garde sa place dans l ordre des departs`() {
    val cancelled = journey(
      id = "supprime",
      legs = listOf(transit(afterMinutes = 5, minutes = 5, cancelled = true)),
    )
    val feed = feedOf(slow, cancelled, quick)

    // L'ordre des départs ne promet qu'une heure de départ : rien à y corriger.
    assertEquals(listOf("lent", "supprime", "rapide"), feed.sorted(JourneySort.DEPARTURE).map { it.id })
  }

  @Test
  fun `une liste vide reste vide, quel que soit le tri`() {
    JourneySort.entries.forEach { sort ->
      assertEquals(emptyList<Journey>(), JourneyFeed().sorted(sort))
    }
  }
}
