package io.github.mgdx.escale.ui.detail

import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowState
import io.github.mgdx.escale.core.follow.FollowTimeline
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.follow.FollowSession
import io.github.mgdx.escale.follow.JourneyFollowing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Un suivi qui note ce qu'on lui demande, sans service derrière. */
class FakeJourneyFollower(private val now: () -> Instant) : JourneyFollowing {

  private val state = MutableStateFlow<FollowSession?>(null)

  override val session: StateFlow<FollowSession?> = state.asStateFlow()

  val started = mutableListOf<Journey>()

  val replaced = mutableListOf<Pair<String, Journey>>()

  var stops = 0

  var reopenItineraryId: String? = null

  override fun start(journey: Journey) {
    started += journey
    val plan = FollowPlan.of(journey)
    state.value = FollowSession(plan, FollowTimeline.stateAt(plan, FollowTimeline.of(plan), now()))
  }

  override fun replace(key: String, journey: Journey) {
    replaced += key to journey
    val current = state.value ?: return
    if (current.plan.key != key) return
    val plan = FollowPlan.of(journey, key)
    state.value = FollowSession(plan, FollowTimeline.stateAt(plan, FollowTimeline.of(plan), now()))
  }

  override fun stop() {
    stops += 1
    state.value = null
  }

  override fun takeReopenItineraryId(): String? = reopenItineraryId.also { reopenItineraryId = null }

  /** Le service avance : l'état publié change sans que l'écran ait rien demandé. */
  fun publish(followState: FollowState) {
    state.value = state.value?.copy(state = followState)
  }
}
