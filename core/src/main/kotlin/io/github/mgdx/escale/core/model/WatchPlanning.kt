package io.github.mgdx.escale.core.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Une exécution de surveillance : le départ surveillé, et le moment où la vérifier.
 *
 * Les deux dates sont distinctes parce qu'elles ne servent pas à la même chose : [runAt] est ce que
 * `WorkManager` reçoit, [departure] est la référence à laquelle le trajet rafraîchi sera comparé.
 */
data class WatchRun(val departure: Instant, val runAt: Instant)

/**
 * Quand une surveillance s'exécute, et quand elle n'a pas lieu d'être (SPEC.md § 5.5.1).
 *
 * Tout est ici, en Kotlin pur, parce que **c'est du code qui tourne sans témoin** : une tâche de
 * fond programmée au mauvais moment ne se voit pas à l'écran, elle se voit dans un test
 * (docs/architecture.md § 1).
 *
 * **Rien ici ne planifie quoi que ce soit de périodique.** [nextRun] rend *une* échéance, celle de
 * la prochaine occurrence, que l'appelant programme en tâche à exécution unique et redemande après
 * chaque exécution — c'est la seule forme de travail de fond que SPEC.md § 7.7 autorise.
 */
object WatchPlanning {

  /** « Une tâche à exécution unique est planifiée pour T − 60 minutes. » */
  val LEAD_TIME: Duration = Duration.ofMinutes(LEAD_MINUTES)

  /**
   * « La surveillance ne s'exécute pas si le trajet a déjà été consulté dans l'application dans les
   * 30 dernières minutes : la donnée est déjà fraîche. »
   */
  val FRESHNESS: Duration = Duration.ofMinutes(FRESHNESS_MINUTES)

  /** « En cas d'échec, une seule reprise après 5 minutes, puis abandon silencieux. » */
  val RETRY_DELAY: Duration = Duration.ofMinutes(RETRY_MINUTES)

  /** L'exécution initiale et l'unique reprise. Au-delà, on abandonne sans rien dire. */
  const val MAX_RUNS = 2

  private const val LEAD_MINUTES = 60L
  private const val FRESHNESS_MINUTES = 30L
  private const val RETRY_MINUTES = 5L

  /**
   * Horizon de recherche d'une occurrence récurrente : une semaine, plus le jour courant.
   *
   * Une semaine suffit toujours — [WatchSchedule.days] n'est jamais vide dans ce cas — et borner
   * la boucle vaut mieux qu'une boucle qui ne s'arrête que sur une condition.
   */
  private const val HORIZON_DAYS = 8

  /**
   * La prochaine exécution de [schedule], ou `null` s'il n'y en a plus.
   *
   * @param after la dernière occurrence déjà traitée, ou l'instant de la configuration. Le prochain
   *   départ est cherché **strictement après** : sans cela, la replanification qui suit une
   *   exécution retomberait sur l'occurrence qui vient d'être traitée et la rejouerait en boucle.
   * @param now l'instant présent, qui borne [WatchRun.runAt] par le bas. Activer une surveillance
   *   quarante minutes avant le départ ne la reporte donc pas au lendemain : elle s'exécute
   *   aussitôt, ce qui reste **une seule requête pour cette occurrence**. La règle des trente
   *   minutes de [isFresh] écarte d'elle-même le cas où l'usager vient de consulter le trajet.
   */
  fun nextRun(schedule: WatchSchedule, after: Instant, now: Instant, zone: ZoneId): WatchRun? {
    val departure = nextDeparture(schedule, after, zone) ?: return null
    val runAt = departure.minus(LEAD_TIME)
    return WatchRun(departure = departure, runAt = maxOf(runAt, now))
  }

  /**
   * Le prochain départ de [schedule] strictement après [after], dans [zone].
   *
   * L'heure est une heure locale et le fuseau est appliqué **au jour concerné** : « 8 h 10 » reste
   * 8 h 10 de part et d'autre d'un changement d'heure d'été, là où un décalage figé à
   * l'enregistrement aurait dérivé d'une heure.
   *
   * Une surveillance sans récurrence dont la date est passée ne rend plus rien : c'est ainsi
   * qu'elle « se désactive d'elle-même » (SPEC.md § 5.5.1).
   */
  fun nextDeparture(schedule: WatchSchedule, after: Instant, zone: ZoneId): Instant? {
    val date = schedule.date
    if (!schedule.isRecurring) {
      return date?.let { ZonedDateTime.of(it, schedule.departureTime, zone).toInstant() }?.takeIf { it.isAfter(after) }
    }
    val from = after.atZone(zone).toLocalDate()
    for (offset in 0 until HORIZON_DAYS) {
      val day = from.plusDays(offset.toLong())
      if (day.dayOfWeek !in schedule.days) continue
      val candidate = ZonedDateTime.of(day, schedule.departureTime, zone).toInstant()
      if (candidate.isAfter(after)) return candidate
    }
    return null
  }

  /**
   * Vrai si le trajet a été consulté dans l'application il y a moins de [FRESHNESS].
   *
   * Le seuil est fermé du côté du passé : consulté il y a exactement trente minutes, le trajet
   * n'est plus considéré comme frais et la vérification a lieu. Une date de consultation nulle —
   * le trajet n'a jamais été ouvert — ne dispense évidemment de rien.
   */
  fun isFresh(lastViewedAt: Instant?, now: Instant): Boolean {
    if (lastViewedAt == null) return false
    return Duration.between(lastViewedAt, now) < FRESHNESS
  }

  /**
   * Vrai s'il reste une reprise après [runAttemptCount] exécutions déjà faites.
   *
   * `WorkManager` compte les exécutions à partir de zéro : la première vaut 0, l'unique reprise
   * vaut 1. « Pas de nouvelle tentative en boucle » (SPEC.md § 5.5.1) tient dans cette comparaison,
   * et pas dans la seule politique de reprise de la bibliothèque, qui réessaierait indéfiniment.
   */
  fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount < MAX_RUNS - 1
}
