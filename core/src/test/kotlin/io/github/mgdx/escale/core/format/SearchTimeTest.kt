package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Le libellé daté de la troisième ligne de la carte de recherche (SPEC.md § 5.1). */
class SearchTimeTest {

  private val paris = ZoneId.of("Europe/Paris")

  /** Jeudi 4 septembre 2025, 10 h 00 à Paris. */
  private val reference = instant(2025, 9, 4, 10, 0)

  private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(paris).toInstant()

  private fun format(instant: Instant, locale: Locale = Locale.FRENCH, use24Hour: Boolean = true) =
    SearchTime.format(instant, paris, locale, use24Hour, reference)

  @Test
  fun `aujourd'hui, l'heure seule`() {
    assertEquals("14:30", format(instant(2025, 9, 4, 14, 30)))
  }

  @Test
  fun `dans la semaine, le jour abrege precede l'heure`() {
    // Vendredi 5 septembre : la spec écrit « Arrivée avant ven. 09:00 ».
    assertEquals("ven. 09:00", format(instant(2025, 9, 5, 9, 0)))
  }

  @Test
  fun `au dela d'une semaine, la date abregee remplace le jour`() {
    assertEquals("15 sept. 08:05", format(instant(2025, 9, 15, 8, 5)))
  }

  @Test
  fun `une date passee est datee, jamais reduite a un jour de semaine`() {
    assertEquals("1 sept. 07:45", format(instant(2025, 9, 1, 7, 45)))
  }

  @Test
  fun `le reglage 12 heures du systeme est respecte`() {
    // CLDR écrit « pm » en minuscules pour l'anglais britannique : c'est la forme attendue.
    assertEquals("2:30 pm", format(instant(2025, 9, 4, 14, 30), Locale.UK, use24Hour = false))
  }

  @Test
  fun `la langue de l'interface nomme le jour`() {
    assertEquals("Fri 09:00", format(instant(2025, 9, 5, 9, 0), Locale.UK))
  }

  @Test
  fun `le fuseau de l'appareil decale l'heure affichee`() {
    val midnightUtc = Instant.parse("2025-09-04T22:00:00Z")
    // 22 h UTC, c'est minuit à Paris : le libellé passe au lendemain, donc au jour de la semaine.
    assertEquals("ven. 00:00", format(midnightUtc))
  }

  @Test
  fun `la date du selecteur se relit en UTC et l'heure dans le fuseau de l'appareil`() {
    // Minuit UTC le 4 septembre 2025, tel que le rend un `DatePicker` de Material 3.
    val picked = LocalDateTime.of(2025, 9, 4, 0, 0).atZone(ZoneId.of("UTC")).toInstant()
    val composed = SearchTime.instantAt(picked.toEpochMilli(), hour = 14, minute = 30, zone = paris)
    assertEquals(instant(2025, 9, 4, 14, 30), composed)
  }

  @Test
  fun `un fuseau a l'ouest de Greenwich ne decale pas la date choisie`() {
    val newYork = ZoneId.of("America/New_York")
    val picked = LocalDateTime.of(2025, 9, 4, 0, 0).atZone(ZoneId.of("UTC")).toInstant()
    val composed = SearchTime.instantAt(picked.toEpochMilli(), hour = 8, minute = 5, zone = newYork)
    assertEquals(LocalDateTime.of(2025, 9, 4, 8, 5).atZone(newYork).toInstant(), composed)
  }
}
