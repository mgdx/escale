package io.github.mgdx.escale.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.github.mgdx.escale.core.model.WatchPlanning
import io.github.mgdx.escale.core.model.WatchedJourney
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * La programmation des vérifications de trajets surveillés (SPEC.md § 5.5.1).
 *
 * **Une tâche à exécution unique, et rien d'autre.** SPEC.md § 7.7 n'autorise qu'une seule
 * exception à l'interdiction du travail de fond : « une requête unique une heure avant un départ
 * configuré par l'utilisateur ». Ce fichier n'emploie donc que [OneTimeWorkRequest] : il n'existe
 * nulle part dans le projet de `PeriodicWorkRequest`, et une tâche « toutes les 24 h » serait la
 * même violation sous un autre nom. La suite est obtenue autrement : après chaque exécution, la
 * tâche en programme **une** pour l'occurrence suivante, calculée par `WatchPlanning`.
 *
 * Les contraintes sont celles de la spec : réseau disponible, pas de batterie faible. **Aucune
 * alarme exacte** : `SCHEDULE_EXACT_ALARM` n'est demandée nulle part, et le décalage de quelques
 * minutes que `WorkManager` s'autorise est explicitement accepté par la spec.
 */
class WatchScheduler(
  private val context: Context,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
  private val clock: () -> Instant = Instant::now,
) : WatchAlarms {

  /**
   * Programme la prochaine vérification de [watch], ou l'annule s'il n'y en a plus.
   *
   * @param after la dernière occurrence traitée, quand la tâche se replanifie elle-même. Par
   *   défaut l'instant présent, ce qui est le cas d'une surveillance qu'on vient d'activer.
   * @return le départ surveillé, ou `null` quand il n'y a plus d'occurrence — une surveillance à
   *   date unique dont la date est passée « se désactive d'elle-même » (SPEC.md § 5.5.1).
   */
  override fun schedule(watch: WatchedJourney, after: Instant): Instant? {
    val now = clock()
    val next = WatchPlanning.nextRun(watch.schedule, after = after, now = now, zone = zone())
    if (next == null) {
      cancel(watch.journeyId)
      return null
    }
    val request = OneTimeWorkRequest.Builder(JourneyWatchWorker::class.java)
      .setInitialDelay(Duration.between(now, next.runAt).coerceAtLeast(Duration.ZERO))
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .setRequiresBatteryNotLow(true)
          .build(),
      )
      // Une seule reprise, cinq minutes plus tard : la politique de `WorkManager` fixe le délai,
      // c'est `WatchPlanning.shouldRetry` qui interdit la deuxième (SPEC.md § 5.5.1).
      .setBackoffCriteria(BackoffPolicy.LINEAR, WatchPlanning.RETRY_DELAY)
      .setInputData(
        Data.Builder()
          .putLong(KEY_JOURNEY_ID, watch.journeyId)
          .putLong(KEY_DEPARTURE, next.departure.toEpochMilli())
          .build(),
      )
      .addTag(TAG)
      .build()
    // `REPLACE` : reconfigurer une surveillance remplace son échéance au lieu d'en ajouter une
    // seconde. Le nom unique par trajet est ce qui garantit qu'il n'y a **jamais** plus d'une
    // vérification programmée par trajet surveillé.
    workManager().enqueueUniqueWork(workName(watch.journeyId), ExistingWorkPolicy.REPLACE, request)
    return next.departure
  }

  /** Arrête la surveillance de [journeyId] : plus aucune tâche ne reste programmée pour lui. */
  override fun cancel(journeyId: Long) {
    workManager().cancelUniqueWork(workName(journeyId))
  }

  /**
   * Reprogramme toutes les surveillances de [watched].
   *
   * Appelée à l'ouverture de l'écran qui les configure. Elle sert surtout après un redémarrage de
   * l'appareil : `RECEIVE_BOOT_COMPLETED` n'est pas demandée (SPEC.md § 11), le
   * `RescheduleReceiver` d'androidx.work ne reçoit donc rien, et c'est l'application qui remet ses
   * échéances en place lorsqu'elle est ouverte. L'opération est sans effet de bord : réenregistrer
   * une échéance déjà en place la recalcule à l'identique.
   */
  override fun sync(watched: List<WatchedJourney>) {
    watched.forEach { schedule(it, after = clock()) }
  }

  /**
   * Obtenu à chaque appel plutôt que gardé : `WorkManager.getInstance` est un singleton du
   * processus, et le retenir dans un champ ferait vivre un contexte plus longtemps que nécessaire.
   */
  private fun workManager() = WorkManager.getInstance(context)

  companion object {
    /** Nom unique de la tâche d'un trajet : un trajet surveillé, au plus une tâche programmée. */
    fun workName(journeyId: Long): String = "$TAG.$journeyId"

    /** Étiquette commune, qui permet d'observer ou d'annuler l'ensemble des surveillances. */
    const val TAG = "escale.watch"

    internal const val KEY_JOURNEY_ID = "journeyId"
    internal const val KEY_DEPARTURE = "departureEpochMilli"
  }
}
