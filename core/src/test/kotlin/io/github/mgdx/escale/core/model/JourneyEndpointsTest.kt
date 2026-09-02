package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.ORIGIN
import io.github.mgdx.escale.core.format.journey
import io.github.mgdx.escale.core.format.place
import io.github.mgdx.escale.core.format.transit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class JourneyEndpointsTest {

  @Test
  fun `une extremite sans nom recoit le libelle saisi par l'usager`() {
    val trip = journey(legs = listOf(anonymousWalk(), transit(afterMinutes = 10, minutes = 15)))

    val named = trip.withEndpointNames(origin = "12 rue de Bercy", destination = "Nation")

    assertEquals("12 rue de Bercy", named.legs.first().from.name)
  }

  @Test
  fun `un nom deja donne par le serveur n'est jamais ecrase`() {
    val trip = journey(legs = listOf(transit(afterMinutes = 0, minutes = 15)))

    val named = trip.withEndpointNames(origin = "ma saisie", destination = "ma saisie")

    assertEquals("A", named.legs.first().from.name)
    assertEquals("B", named.legs.last().to.name)
  }

  @Test
  fun `les deux extremites d'un trajet d'une seule portion sont nommees`() {
    val trip = journey(legs = listOf(anonymousWalk()))

    val named = trip.withEndpointNames(origin = "Bercy", destination = "Châtelet")

    assertEquals("Bercy", named.legs.single().from.name)
    assertEquals("Châtelet", named.legs.single().to.name)
  }

  @Test
  fun `sans libelle saisi, l'extremite reste anonyme plutot que de recevoir un nom faux`() {
    val trip = journey(legs = listOf(anonymousWalk()))

    val named = trip.withEndpointNames(origin = null, destination = "   ")

    assertEquals("", named.legs.single().from.name)
    assertEquals("", named.legs.single().to.name)
  }

  @Test
  fun `seules les deux extremites du trajet sont nommees, jamais les points intermediaires`() {
    val trip = journey(legs = listOf(anonymousWalk(), transit(afterMinutes = 10, minutes = 15)))

    val named = trip.withEndpointNames(origin = "Bercy", destination = "Nation")

    // La fin de la marche est un point intermédiaire : c'est au serveur de le nommer, pas à
    // l'usager, qui n'a jamais saisi ce point-là.
    assertEquals("", named.legs.first().to.name)
    assertEquals("A", named.legs.last().from.name)
  }

  @Test
  fun `un trajet sans portion traverse la fonction intact`() {
    val empty = Journey(
      id = null,
      startTime = ORIGIN,
      endTime = ORIGIN,
      scheduledStartTime = ORIGIN,
      scheduledEndTime = ORIGIN,
      duration = Duration.ZERO,
      transfers = 0,
      legs = emptyList(),
    )

    assertEquals(empty, empty.withEndpointNames(origin = "Bercy", destination = "Nation"))
  }

  /** Une portion à pied dont les deux bouts sont anonymes : le cas d'un trajet direct par adresse. */
  private fun anonymousWalk(): JourneyLeg.Walk {
    val end = ORIGIN.plus(Duration.ofMinutes(10))
    return JourneyLeg.Walk(
      startTime = ORIGIN,
      endTime = end,
      scheduledStartTime = ORIGIN,
      scheduledEndTime = end,
      duration = Duration.ofMinutes(10),
      from = place("", ORIGIN),
      to = place("", end),
    )
  }
}
