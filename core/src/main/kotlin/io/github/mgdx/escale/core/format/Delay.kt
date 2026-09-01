package io.github.mgdx.escale.core.format

import java.time.Duration
import java.time.Instant

/**
 * Qualification d'un écart à l'horaire (SPEC.md § 5.2).
 *
 * L'interface double toujours cette information d'un texte et d'une icône : SPEC.md § 9 interdit
 * qu'une information ne soit portée que par la couleur.
 */
enum class DelayQuality {
  /** Trajet en avance sur l'horaire. */
  EARLY,

  /** À l'heure, à moins d'une minute près. */
  ON_TIME,

  /** Retard léger, à afficher en orange. */
  SLIGHT,

  /** Retard important, à afficher en rouge. */
  SEVERE,
}

/**
 * Écart entre l'heure réelle et l'heure théorique.
 *
 * [difference] est positif en cas de retard, négatif en cas d'avance.
 */
data class Delay(val difference: Duration, val quality: DelayQuality) {

  /** Valeur absolue de l'écart, découpée pour l'affichage. */
  val formatted: FormattedDuration
    get() = FormattedDuration.of(difference)

  companion object {
    /** En deçà, on considère que le trajet est à l'heure. */
    val ON_TIME_THRESHOLD: Duration = Duration.ofMinutes(1)

    /** Au-delà, le retard est signalé comme important. */
    val SEVERE_THRESHOLD: Duration = Duration.ofMinutes(5)

    /**
     * Calcule l'écart entre [actual] et [scheduled].
     *
     * Rend `null` si [realTime] est faux : sans donnée temps réel, les deux heures sont égales par
     * construction et annoncer « à l'heure » serait mensonger (SPEC.md § 5.2).
     */
    fun between(actual: Instant, scheduled: Instant, realTime: Boolean): Delay? {
      if (!realTime) return null
      val difference = Duration.between(scheduled, actual)
      return Delay(difference, qualify(difference))
    }

    private fun qualify(difference: Duration): DelayQuality = when {
      difference <= ON_TIME_THRESHOLD.negated() -> DelayQuality.EARLY
      difference < ON_TIME_THRESHOLD -> DelayQuality.ON_TIME
      difference < SEVERE_THRESHOLD -> DelayQuality.SLIGHT
      else -> DelayQuality.SEVERE
    }
  }
}
