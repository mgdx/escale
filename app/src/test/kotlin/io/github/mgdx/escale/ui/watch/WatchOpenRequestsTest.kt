package io.github.mgdx.escale.ui.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La demande d'ouverture venue d'une notification : déposée, consommée une fois (SPEC.md § 5.5.1). */
class WatchOpenRequestsTest {

  private val requests = WatchOpenRequests()

  @Test
  fun `au depart, il n'y a rien a ouvrir`() {
    assertNull(requests.request.value)
  }

  @Test
  fun `une demande porte le trajet demande`() {
    requests.open(journeyId = 4)

    assertEquals(4L, requests.request.value?.journeyId)
  }

  @Test
  fun `une demande consommee ne se rejoue pas`() {
    requests.open(journeyId = 4)

    requests.consume()

    // C'est ce qui empêche une rotation de l'écran de rouvrir le trajet.
    assertNull(requests.request.value)
  }

  @Test
  fun `deux appuis sur la meme notification ouvrent deux fois`() {
    requests.open(journeyId = 4)
    val first = requests.request.value
    requests.consume()

    requests.open(journeyId = 4)

    // Le jeton change : sans lui, une `StateFlow` ne republierait pas une valeur égale et le
    // second appui resterait sans effet.
    assertNotEquals(first?.token, requests.request.value?.token)
  }

  @Test
  fun `deux trajets ne se confondent pas`() {
    requests.open(journeyId = 4)
    val first = requests.request.value

    requests.open(journeyId = 9)

    assertEquals(4L, first?.journeyId)
    assertEquals(9L, requests.request.value?.journeyId)
    assertNotEquals(first?.token, requests.request.value?.token)
  }
}
