package io.github.mgdx.escale.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {

  @Test
  fun `un succes porte sa valeur`() {
    val outcome: Outcome<Int> = Outcome.Success(42)
    assertEquals(42, outcome.getOrNull())
    assertNull(outcome.errorOrNull())
  }

  @Test
  fun `un echec porte son erreur`() {
    val outcome: Outcome<Int> = Outcome.Failure(EscaleError.NoNetwork)
    assertNull(outcome.getOrNull())
    assertEquals(EscaleError.NoNetwork, outcome.errorOrNull())
  }

  @Test
  fun `map transforme la valeur et laisse l erreur intacte`() {
    val success: Outcome<Int> = Outcome.Success(21)
    assertEquals(42, success.map { it * 2 }.getOrNull())

    val failure: Outcome<Int> = Outcome.Failure(EscaleError.Timeout)
    assertEquals(EscaleError.Timeout, failure.map { it * 2 }.errorOrNull())
  }
}
