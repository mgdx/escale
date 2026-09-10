package io.github.mgdx.escale.core.format

import java.time.Duration
import kotlin.math.absoluteValue

/**
 * Une durée découpée en heures et minutes, prête à être injectée dans un libellé de `strings.xml`.
 *
 * Ce type ne rend **pas** de chaîne : la composition (« 1 h 20 » en français, « 1 hr 20 min » en
 * anglais) appartient à `:app`, qui seul connaît les ressources et la langue.
 */
data class FormattedDuration(
  val hours: Long,
  val minutes: Long,
  /** Vrai lorsque la durée d'origine était négative, cas d'une avance sur l'horaire. */
  val negative: Boolean = false,
) {
  /** Vrai si la durée arrondie est nulle : l'interface affiche alors « moins d'une minute ». */
  val isZero: Boolean
    get() = hours == 0L && minutes == 0L

  /**
   * Vrai lorsque la durée s'écrit avec ses **deux** unités en toutes lettres (« 1 h 17 min »).
   *
   * C'est le seul cas où le libellé complet est assez long pour ne pas tenir dans une largeur
   * contrainte — un onglet du bandeau de résultats vaut le quart de l'écran (SPEC.md § 5.2), et le
   * libellé s'y coupait en deux lignes dès 130 % d'agrandissement. L'interface lui préfère alors
   * la forme courte, « 1 h 17 », qui dit la même chose sans la seconde unité.
   *
   * En dessous de l'heure (« 18 min ») comme sur une heure ronde (« 2 h »), les deux formes ont la
   * même longueur : rien à gagner à raccourcir, et l'unité en toutes lettres est plus claire.
   */
  val spellsBothUnits: Boolean
    get() = hours > 0L && minutes > 0L

  companion object {
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val HALF_MINUTE = 30L

    /**
     * Découpe [duration] en heures et minutes, à la minute la plus proche.
     *
     * L'arrondi au plus proche, et non par défaut, évite qu'une portion de 59 secondes s'affiche
     * « 0 min » à côté d'une heure de départ et d'une heure d'arrivée qui, elles, diffèrent.
     */
    fun of(duration: Duration): FormattedDuration {
      val totalSeconds = duration.seconds.absoluteValue
      val totalMinutes = (totalSeconds + HALF_MINUTE) / SECONDS_PER_MINUTE
      return FormattedDuration(
        hours = totalMinutes / MINUTES_PER_HOUR,
        minutes = totalMinutes % MINUTES_PER_HOUR,
        negative = duration.isNegative,
      )
    }
  }
}
