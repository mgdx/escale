package io.github.mgdx.escale.work

import io.github.mgdx.escale.core.model.WatchedJourney
import java.time.Instant

/**
 * La programmation des vérifications, vue par ceux qui la demandent (SPEC.md § 5.5.1).
 *
 * L'interface existe pour que l'interface graphique et la tâche de fond dépendent de ce qu'elles
 * utilisent, et non de `WorkManager` : c'est ce qui rend leur enchaînement vérifiable en JVM
 * (docs/architecture.md § 10). L'unique implémentation est [WatchScheduler].
 *
 * Elle ne propose **aucune** programmation périodique, et c'est délibéré : SPEC.md § 7.7 n'autorise
 * qu'une exécution unique par occurrence.
 */
interface WatchAlarms {
  /**
   * Programme la prochaine vérification de [watch], ou l'annule s'il n'y en a plus.
   *
   * @param after la dernière occurrence traitée, quand la tâche se replanifie elle-même.
   * @return le départ surveillé, ou `null` quand il n'y a plus d'occurrence.
   */
  fun schedule(watch: WatchedJourney, after: Instant = Instant.now()): Instant?

  /** Arrête la surveillance de [journeyId] : plus aucune tâche ne reste programmée pour lui. */
  fun cancel(journeyId: Long)

  /** Remet en place les échéances de [watched], par exemple après un redémarrage de l'appareil. */
  fun sync(watched: List<WatchedJourney>)
}
