package io.github.mgdx.escale.follow

import io.github.mgdx.escale.core.follow.FollowLeg
import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowStop
import io.github.mgdx.escale.core.follow.StreetKind
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FollowPlanCodecTest {

  private val origin = Instant.parse("2026-09-01T08:00:00Z")

  private val plan = FollowPlan(
    key = "it-1",
    itineraryId = "it-1",
    legs = listOf(
      FollowLeg.Street(
        start = origin,
        end = origin.plusSeconds(300),
        fromName = "Maison",
        toName = "Châtelet",
        cancelled = false,
        kind = StreetKind.WALK,
        duration = Duration.ofMinutes(5),
      ),
      FollowLeg.Transit(
        start = origin.plusSeconds(600),
        end = origin.plusSeconds(1_320),
        fromName = "Châtelet",
        toName = "Montparnasse",
        cancelled = false,
        mode = TransitMode.SUBWAY,
        lineLabel = "4",
        headsign = "Porte d'Orléans",
        track = "2",
        stops = listOf(
          FollowStop(name = "Cité", time = origin.plusSeconds(720), cancelled = false),
          FollowStop(name = "Saint-Michel", time = origin.plusSeconds(840), cancelled = true),
        ),
      ),
    ),
  )

  @Test
  fun `un plan encode se relit a l'identique`() {
    assertEquals(plan, decodeFollowPlan(encodeFollowPlan(plan)))
  }

  @Test
  fun `un plan sans identifiant d'itineraire se relit aussi`() {
    val anonymous = plan.copy(key = "empreinte", itineraryId = null)
    assertEquals(anonymous, decodeFollowPlan(encodeFollowPlan(anonymous)))
  }

  @Test
  fun `une chaine inexploitable rend null plutot que de faire planter le service`() {
    assertNull(decodeFollowPlan("pas du json"))
    assertNull(decodeFollowPlan("{\"key\":\"x\"}"))
  }
}
