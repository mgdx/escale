package io.github.mgdx.escale.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'heure choisie dans la carte de recherche : sa mise en forme pour le libellé de la troisième ligne
 * (SPEC.md § 5.1 : « Départ jeu. 14:30 », « Arrivée avant ven. 09:00 »).
 *
 * Ce type ne rend **que** la partie datée du libellé. Le mot qui la précède — « Départ », « Arrivée
 * avant » — vient de `strings_search.xml` et est composé par `:app` : concaténer ici produirait du
 * texte codé en dur, que CLAUDE.md interdit.
 *
 * La règle est purement calculatoire, donc testable en JVM, donc à sa place dans `:core`
 * (docs/architecture.md § 1).
 */
object SearchTime {

  /** Au-delà d'une semaine, le jour de la semaine ne suffit plus à situer une date. */
  private const val WEEKDAY_HORIZON_DAYS = 7L

  private const val TIME_24_HOUR = "HH:mm"
  private const val TIME_12_HOUR = "h:mm a"
  private const val WEEKDAY_PREFIX = "EEE "
  private const val DATE_PREFIX = "d MMM "

  /**
   * Le moment [instant] tel qu'il doit s'écrire dans la carte de recherche.
   *
   * - aujourd'hui : l'heure seule, le jour n'apporterait rien ;
   * - dans la semaine qui vient : le jour abrégé et l'heure, comme la spec l'écrit ;
   * - au-delà, ou dans le passé : la date abrégée et l'heure, un jour de semaine seul y serait
   *   ambigu.
   *
   * @param zone le fuseau de l'appareil : une heure de départ se lit toujours en heure locale.
   * @param use24Hour le réglage 12 h / 24 h du système, que `:core` ne peut pas lire lui-même.
   * @param reference l'instant qui sert de « maintenant », paramétrable pour les tests.
   */
  fun format(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
    use24Hour: Boolean,
    reference: Instant = Instant.now(),
  ): String {
    val target = instant.atZone(zone)
    val today = reference.atZone(zone).toLocalDate()
    val date = target.toLocalDate()
    val time = if (use24Hour) TIME_24_HOUR else TIME_12_HOUR
    val prefix = when {
      date == today -> ""
      date.isAfter(today) && date.isBefore(today.plusDays(WEEKDAY_HORIZON_DAYS)) -> WEEKDAY_PREFIX
      else -> DATE_PREFIX
    }
    return DateTimeFormatter.ofPattern(prefix + time, locale).format(target)
  }

  /**
   * L'instant que composent la date rendue par un `DatePicker` et l'heure rendue par un
   * `TimePicker` de Material 3.
   *
   * Un `DatePicker` rend un **millésime UTC de minuit** : le lire dans le fuseau de l'appareil
   * décalerait la date d'un jour pour tout usager à l'ouest de Greenwich. La date se relit donc en
   * UTC, et c'est seulement une fois l'heure posée dessus que [zone] entre en jeu — une heure de
   * départ se saisit toujours en heure locale.
   */
  fun instantAt(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Instant =
    Instant.ofEpochMilli(dateUtcMillis)
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
      .atTime(hour, minute)
      .atZone(zone)
      .toInstant()
}
