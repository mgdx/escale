package io.github.mgdx.escale.core.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RealtimeRefreshPolicyTest {

  private val chargement = Instant.parse("2025-06-01T08:00:00Z")

  @Test
  fun `le seuil est celui de la spec, soixante secondes`() {
    assertEquals(Duration.ofSeconds(60), RealtimeRefreshPolicy.MAX_AGE)
  }

  @Test
  fun `des donnees fraiches ne declenchent rien au retour au premier plan`() {
    assertFalse(
      RealtimeRefreshPolicy.shouldRefreshOnForeground(chargement, chargement.plusSeconds(59)),
    )
  }

  @Test
  fun `soixante secondes tout juste atteintes declenchent un rafraichissement`() {
    assertTrue(
      RealtimeRefreshPolicy.shouldRefreshOnForeground(chargement, chargement.plusSeconds(60)),
    )
  }

  @Test
  fun `des donnees nettement perimees declenchent un rafraichissement`() {
    assertTrue(
      RealtimeRefreshPolicy.shouldRefreshOnForeground(chargement, chargement.plus(Duration.ofHours(2))),
    )
  }

  @Test
  fun `sans chargement anterieur il n y a rien a rafraichir`() {
    assertFalse(RealtimeRefreshPolicy.shouldRefreshOnForeground(null, chargement))
  }

  @Test
  fun `une horloge qui recule fait rafraichir plutot que de croire des horaires perimes`() {
    assertTrue(
      RealtimeRefreshPolicy.shouldRefreshOnForeground(chargement, chargement.minusSeconds(1)),
    )
  }
}
