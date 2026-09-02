package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.ClockFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'heure du jour, au format choisi par l'usager (SPEC.md § 5.6, « format 12 h ou 24 h »).
 *
 * **Le seul endroit du projet qui décide entre 12 h et 24 h.** Le réglage a trois valeurs et
 * l'affichage n'en connaît que deux : c'est [uses24Hour] qui fait la réduction, et elle est ici
 * plutôt que dans un composable parce qu'elle est vérifiable en JVM (docs/architecture.md § 1).
 *
 * `:core` ne peut pas lire le réglage du système — c'est une notion Android : la valeur
 * `ClockFormat.SYSTEM` se résout donc avec le booléen que `:app` lui passe.
 */
object ClockTime {

  /** Motif sur 24 heures : « 13:30 ». */
  const val PATTERN_24_HOUR = "HH:mm"

  /** Motif sur 12 heures : « 1:30 pm ». */
  const val PATTERN_12_HOUR = "h:mm a"

  /** Le motif d'heure à employer, sans la date. */
  fun pattern(use24Hour: Boolean): String = if (use24Hour) PATTERN_24_HOUR else PATTERN_12_HOUR

  /**
   * L'heure de [instant] telle qu'elle doit s'écrire : dans le fuseau de l'appareil, dans sa
   * langue, et au format que l'usager a choisi.
   */
  fun format(instant: Instant, zone: ZoneId, locale: Locale, use24Hour: Boolean): String =
    DateTimeFormatter.ofPattern(pattern(use24Hour), locale).format(instant.atZone(zone))
}

/**
 * Le réglage de SPEC.md § 5.6 réduit à la seule information dont un formateur a besoin.
 *
 * @param systemUses24Hour le réglage de l'appareil, que `:app` lit avec
 *   `android.text.format.DateFormat.is24HourFormat(context)`. Il ne sert que pour
 *   [ClockFormat.SYSTEM] : un choix explicite de l'usager l'emporte toujours sur celui du système,
 *   c'est tout l'objet du réglage.
 */
fun ClockFormat.uses24Hour(systemUses24Hour: Boolean): Boolean = when (this) {
  ClockFormat.SYSTEM -> systemUses24Hour
  ClockFormat.HOURS_12 -> false
  ClockFormat.HOURS_24 -> true
}
