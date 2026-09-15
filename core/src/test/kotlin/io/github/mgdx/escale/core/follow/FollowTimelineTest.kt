package io.github.mgdx.escale.core.follow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowTimelineTest {

  private val plan = FollowPlan.of(referenceJourney())
  private val events = FollowTimeline.of(plan)

  @Test
  fun `les echeances sont le depart des portions, le depart des arrets et l'arrivee des portions`() {
    val expected = listOf(0L, 5, 10, 12, 14, 16, 18, 20, 22, 22, 26, 30, 32, 34, 34, 40).map(::at)
    assertEquals(expected, events.map { it.at })
  }

  @Test
  fun `avant le depart, le suivi attend et nomme le premier vehicule`() {
    val state = FollowTimeline.stateAt(plan, events, at(-30))
    assertTrue(state is FollowState.Waiting)
    state as FollowState.Waiting
    assertEquals("4", state.firstTransit?.lineLabel)
    assertEquals("2", state.firstTransit?.track)
  }

  @Test
  fun `a bord, le dernier arret franchi est le dernier dont le depart est passe`() {
    val state = FollowTimeline.stateAt(plan, events, at(15)) as FollowState.OnBoard
    assertEquals(1, state.legIndex)
    // Cité et Saint-Michel sont franchis : le prochain est Odéon, il reste Odéon, Saint-Germain,
    // Saint-Placide et la descente à Montparnasse.
    assertEquals("Odéon", state.nextStopName)
    assertEquals(4, state.stopsRemaining)
  }

  @Test
  fun `a la montee, tous les arrets restent a atteindre, descente comprise`() {
    val state = FollowTimeline.stateAt(plan, events, at(10)) as FollowState.OnBoard
    assertEquals("Cité", state.nextStopName)
    assertEquals(6, state.stopsRemaining)
  }

  @Test
  fun `apres le dernier arret intermediaire, le prochain arret est la descente`() {
    val state = FollowTimeline.stateAt(plan, events, at(21)) as FollowState.OnBoard
    assertNull(state.nextStopIndex)
    assertEquals("Montparnasse", state.nextStopName)
    assertEquals(1, state.stopsRemaining)
  }

  @Test
  fun `l'alerte trois arrets tombe au depart de l'arret qui laisse trois arrets a atteindre`() {
    val event = events.single { it.alert is FollowAlert.StopsBefore }
    // Au départ d'Odéon (16) restent Saint-Germain, Saint-Placide et Montparnasse.
    assertEquals(at(16), event.at)
    assertEquals(FollowAlert.StopsBefore(stops = 3, stopName = "Montparnasse"), event.alert)
  }

  @Test
  fun `l'alerte prochain arret tombe au depart du dernier arret intermediaire`() {
    val alerts = events.filter { it.alert is FollowAlert.NextStop }
    assertEquals(listOf(at(20), at(32)), alerts.map { it.at })
    assertEquals(FollowAlert.NextStop("Montparnasse"), alerts[0].alert)
    assertEquals(FollowAlert.NextStop("Pasteur"), alerts[1].alert)
  }

  @Test
  fun `l'alerte trois arrets est omise sur une portion trop courte`() {
    // La ligne 6 n'a qu'un arrêt intermédiaire : deux arrêts à atteindre à la montée, jamais trois.
    val short = events.filter { it.at >= at(30) && it.alert is FollowAlert.StopsBefore }
    assertTrue(short.isEmpty())
  }

  @Test
  fun `la descente annonce l'arret et ce qui vient ensuite`() {
    val alight = events.single { it.at == at(22) && it.alert != null }.alert as FollowAlert.Alight
    assertEquals("Montparnasse", alight.stopName)
    assertEquals(2, alight.then.streetIndex)
    assertEquals(StreetKind.WALK, alight.then.street?.kind)
    assertEquals("6", alight.then.nextTransit?.lineLabel)
  }

  @Test
  fun `entre deux vehicules, l'etat dit le cheminement et le prochain vehicule`() {
    val state = FollowTimeline.stateAt(plan, events, at(24)) as FollowState.Connecting
    assertEquals(2, state.streetIndex)
    assertEquals("Nation", state.nextTransit?.headsign)
    // Le cheminement est fini, le véhicule pas encore là : on attend sur place.
    val waiting = FollowTimeline.stateAt(plan, events, at(28)) as FollowState.Connecting
    assertNull(waiting.street)
    assertEquals("6", waiting.nextTransit?.lineLabel)
  }

  @Test
  fun `le dernier cheminement mene a l'arrivee, sans vehicule suivant`() {
    val state = FollowTimeline.stateAt(plan, events, at(36)) as FollowState.Connecting
    assertNull(state.nextTransit)
    assertEquals("Bureau", state.street?.toName)
    assertEquals(FollowState.Arrived, FollowTimeline.stateAt(plan, events, at(40)))
    assertEquals(FollowAlert.Arrived("Bureau"), events.last().alert)
  }

  @Test
  fun `la prochaine echeance est strictement posterieure a l'instant courant`() {
    assertEquals(at(12), FollowTimeline.nextAfter(events, at(10))?.at)
    assertEquals(at(12), FollowTimeline.nextAfter(events, at(11))?.at)
    assertNull(FollowTimeline.nextAfter(events, at(40)))
  }

  @Test
  fun `un suivi lance en route ne rejoue pas les alertes passees`() {
    // Lancé à 8 h 17 : l'alerte « trois arrêts » de 8 h 16 est passée, la suivante est celle de 8 h 20.
    val alerts = FollowTimeline.alertsBetween(events, after = at(17), until = at(20))
    assertEquals(listOf<FollowAlert>(FollowAlert.NextStop("Montparnasse")), alerts)
  }

  @Test
  fun `un arret supprime ne se compte pas et ne s'annonce pas`() {
    val cancelled = journey(
      legs = listOf(
        transit(
          from = "A",
          to = "E",
          startMinute = 0,
          endMinute = 8,
          stops = listOf(stop("B", 2), stop("C", 4, cancelled = true), stop("D", 6)),
        ),
      ),
    )
    val cancelledPlan = FollowPlan.of(cancelled)
    val cancelledEvents = FollowTimeline.of(cancelledPlan)

    val boarding = FollowTimeline.stateAt(cancelledPlan, cancelledEvents, at(0)) as FollowState.OnBoard
    assertEquals(3, boarding.stopsRemaining)
    // Au départ de B, C n'est pas desservi : le prochain arrêt est D.
    val afterB = FollowTimeline.stateAt(cancelledPlan, cancelledEvents, at(3)) as FollowState.OnBoard
    assertEquals("D", afterB.nextStopName)
    assertEquals(2, afterB.stopsRemaining)
    assertTrue(cancelledEvents.none { it.at == at(4) })
  }

  @Test
  fun `deux alertes au meme instant n'en font qu'une, la plus pressante`() {
    // La descente a lieu à l'instant même où le véhicule quitte le dernier arrêt intermédiaire.
    val tight = journey(
      legs = listOf(transit(from = "A", to = "C", startMinute = 0, endMinute = 5, stops = listOf(stop("B", 5)))),
    )
    val tightEvents = FollowTimeline.of(FollowPlan.of(tight))

    val alerts = tightEvents.filter { it.at == at(5) }.mapNotNull { it.alert }
    assertEquals(listOf<FollowAlert>(FollowAlert.Arrived("C")), alerts)
  }

  @Test
  fun `deux vehicules sans cheminement entre eux se suivent par une attente sur place`() {
    val direct = journey(
      legs = listOf(
        transit(from = "A", to = "B", startMinute = 0, endMinute = 10, line = "1"),
        transit(from = "B", to = "C", startMinute = 15, endMinute = 25, line = "2"),
      ),
    )
    val directPlan = FollowPlan.of(direct)
    val directEvents = FollowTimeline.of(directPlan)

    val between = FollowTimeline.stateAt(directPlan, directEvents, at(12)) as FollowState.Connecting
    assertNull(between.street)
    assertEquals("2", between.nextTransit?.lineLabel)
    val alight = directEvents.single { it.at == at(10) }.alert as FollowAlert.Alight
    assertEquals("2", alight.then.nextTransit?.lineLabel)
    assertTrue(FollowTimeline.stateAt(directPlan, directEvents, at(15)) is FollowState.OnBoard)
  }
}
