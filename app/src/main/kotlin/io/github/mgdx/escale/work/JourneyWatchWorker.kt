package io.github.mgdx.escale.work

import android.content.Context
import android.text.format.DateFormat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.EscaleApplication
import io.github.mgdx.escale.core.format.uses24Hour
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.JourneyWatchCheck
import io.github.mgdx.escale.core.model.WatchCheck
import io.github.mgdx.escale.core.model.WatchPlanning
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.ui.watch.WatchCheckRecord
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * La vérification d'un trajet surveillé, une heure avant son départ (SPEC.md § 5.5.1).
 *
 * **C'est le seul travail de fond de l'application** (SPEC.md § 7.7), et il est réduit à ce qui ne
 * peut pas être ailleurs : lire l'état, appeler la règle, notifier, reprogrammer l'occurrence
 * suivante. Toute la décision — faut-il vérifier, que demander au serveur, y a-t-il quelque chose à
 * dire — est dans `:core` (`JourneyWatchCheck`, `JourneyWatchComparison`, `WatchPlanning`), où elle
 * est vérifiable en JVM. Ce fichier-ci n'a pas de règle à lui.
 *
 * **Une seule requête réseau par occurrence** dans le cas normal, une seconde uniquement si le
 * serveur a refusé l'identifiant d'itinéraire. En cas d'échec, **une seule reprise** cinq minutes
 * plus tard, puis abandon **silencieux** : rien ne s'affiche, et surtout rien ne se journalise.
 *
 * **Aucune journalisation** (SPEC.md § 11) : ce code manipule le domicile et le lieu de travail de
 * quelqu'un, dans un processus que personne ne regarde. C'est exactement là qu'une trace oubliée ne
 * se verrait jamais.
 */
class JourneyWatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

  override suspend fun doWork(): Result {
    val container = (applicationContext as? EscaleApplication)?.container ?: return Result.success()
    val journeyId = inputData.getLong(WatchScheduler.KEY_JOURNEY_ID, MISSING)
    val departure = inputData.getLong(WatchScheduler.KEY_DEPARTURE, MISSING)
    if (journeyId == MISSING || departure == MISSING) return Result.success()
    return check(container, journeyId, Instant.ofEpochMilli(departure))
  }

  /**
   * Vérifie l'occurrence qui part à [departure], puis programme la suivante.
   *
   * Un trajet dont la surveillance ou le favori a disparu entre la programmation et l'exécution ne
   * produit rien et ne reprogramme rien : la tâche s'éteint d'elle-même.
   */
  private suspend fun check(container: AppContainer, journeyId: Long, departure: Instant): Result {
    val target = target(container, journeyId) ?: return Result.success()
    val (watch, favorite) = target
    val now = Instant.now()
    val outcome = JourneyWatchCheck(container.planRepository, container.watchedJourneysRepository).run(
      watch = watch,
      favorite = favorite,
      departure = departure,
      now = now,
      settings = container.watchSettingsStore.settings.first(),
      preferences = container.preferencesRepository.searchPreferences.first(),
    )
    // « En cas d'échec, une seule reprise après 5 minutes, puis abandon silencieux. » Le délai est
    // porté par la politique de reprise de la tâche, le « une seule » par cette condition.
    if (outcome is WatchCheck.Failed && WatchPlanning.shouldRetry(runAttemptCount)) return Result.retry()
    report(container, journeyId, outcome, now)
    reschedule(container, watch, departure)
    return Result.success()
  }

  /** La surveillance et le favori correspondant, ou `null` si l'un des deux n'existe plus. */
  private suspend fun target(container: AppContainer, journeyId: Long): Pair<WatchedJourney, FavoriteJourney>? {
    val watch = container.watchedJourneysRepository.watched.first()
      .firstOrNull { it.journeyId == journeyId } ?: return null
    val favorite = container.favoritesRepository.journeys.first()
      .firstOrNull { it.id == journeyId } ?: return null
    return watch to favorite
  }

  /**
   * Notifie s'il y a lieu, et retient le résultat pour l'application.
   *
   * Le résultat est enregistré **même quand la notification n'est pas partie** : SPEC.md § 5.5.1
   * veut qu'un refus de `POST_NOTIFICATIONS` laisse « l'état visible à l'ouverture de
   * l'application », et c'est cet enregistrement qui le rend possible.
   *
   * Un échec, lui, n'écrit rien : « un échec du repli n'affiche rien ».
   */
  private suspend fun report(container: AppContainer, journeyId: Long, outcome: WatchCheck, now: Instant) {
    when (outcome) {
      is WatchCheck.Notify -> {
        val notified = container.watchNotifications.notify(journeyId, outcome.notice, uses24Hour(container))
        container.watchSettingsStore.record(
          journeyId,
          WatchCheckRecord(
            checkedAt = now,
            issue = outcome.notice.issue,
            lineName = outcome.notice.lineName,
            delay = outcome.notice.delay,
            suggestedDeparture = outcome.notice.suggestedDeparture,
            notified = notified,
          ),
        )
      }

      WatchCheck.NothingToReport ->
        container.watchSettingsStore.record(journeyId, WatchCheckRecord(checkedAt = now, issue = null))

      // Vérification écartée faute d'utilité, ou échec : dans les deux cas, rien ne s'est produit
      // qui mérite d'être annoncé.
      WatchCheck.Skipped, WatchCheck.Failed -> Unit
    }
  }

  /**
   * Programme l'occurrence suivante — **une seule**, à exécution unique (SPEC.md § 7.7).
   *
   * `after = departure` est ce qui empêche la replanification de retomber sur l'occurrence qui
   * vient d'être traitée. Quand il n'y en a plus, la surveillance à date unique est retirée : elle
   * « se désactive d'elle-même ». Le résultat de la dernière vérification, lui, reste consultable.
   *
   * Reprogrammer sous le même nom unique annule la tâche en cours — c'est-à-dire celle-ci, arrivée
   * à son terme. C'est sans conséquence : la notification est déjà partie et le résultat déjà
   * enregistré.
   */
  private suspend fun reschedule(container: AppContainer, watch: WatchedJourney, departure: Instant) {
    val next = container.watchScheduler.schedule(watch, after = departure)
    if (next == null) container.watchedJourneysRepository.unwatch(watch.journeyId)
  }

  /** Le format d'heure choisi par l'usager, à défaut celui de l'appareil (SPEC.md § 5.6). */
  private suspend fun uses24Hour(container: AppContainer): Boolean = container.preferencesRepository
    .displayPreferences.first()
    .clockFormat
    .uses24Hour(DateFormat.is24HourFormat(applicationContext))

  private companion object {
    /** Une entrée absente : la tâche n'a alors rien à vérifier et s'arrête sans bruit. */
    const val MISSING = -1L
  }
}
