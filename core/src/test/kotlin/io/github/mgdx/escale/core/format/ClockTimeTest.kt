package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.ClockFormat
import io.github.mgdx.escale.core.model.DisplayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ClockTimeTest {

  private val paris = ZoneId.of("Europe/Paris")
  private val locale = Locale.UK

  /** 13:30 heure de Paris, en été. */
  private val instant = Instant.parse("2025-06-05T11:30:00Z")

  @Test
  fun `suivre le systeme reprend le reglage de l appareil, dans les deux sens`() {
    assertTrue(ClockFormat.SYSTEM.uses24Hour(systemUses24Hour = true))
    assertFalse(ClockFormat.SYSTEM.uses24Hour(systemUses24Hour = false))
  }

  @Test
  fun `un choix explicite l emporte sur le reglage du systeme`() {
    // C'est tout l'objet du réglage : quelqu'un dont le téléphone est sur 24 h doit pouvoir lire
    // ses horaires sur 12 h, et l'inverse.
    assertFalse(ClockFormat.HOURS_12.uses24Hour(systemUses24Hour = true))
    assertTrue(ClockFormat.HOURS_24.uses24Hour(systemUses24Hour = false))
  }

  @Test
  fun `sans reglage, le format suit le systeme`() {
    assertEquals(ClockFormat.SYSTEM, DisplayPreferences().clockFormat)
  }

  @Test
  fun `le format 24 heures ecrit l heure sur deux chiffres`() {
    assertEquals("13:30", ClockTime.format(instant, paris, locale, use24Hour = true))
  }

  @Test
  fun `le format 12 heures ecrit le moment de la journee`() {
    val ecrit = ClockTime.format(instant, paris, locale, use24Hour = false)
    assertTrue(ecrit.startsWith("1:30"))
    assertTrue(ecrit.lowercase(locale).contains("pm"))
  }

  @Test
  fun `l heure se lit dans le fuseau demande`() {
    assertEquals("07:30", ClockTime.format(instant, ZoneId.of("America/New_York"), locale, use24Hour = true))
  }

  @Test
  fun `le motif d heure est le meme pour toute l application`() {
    assertEquals(ClockTime.PATTERN_24_HOUR, ClockTime.pattern(use24Hour = true))
    assertEquals(ClockTime.PATTERN_12_HOUR, ClockTime.pattern(use24Hour = false))
  }
}
