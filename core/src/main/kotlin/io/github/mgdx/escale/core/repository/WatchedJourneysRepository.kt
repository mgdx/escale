package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.WatchDecision
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Les trajets favoris sous surveillance (SPEC.md § 5.5.1).
 *
 * **Ce dépôt ne fait que persister.** Il ne planifie rien, n'appelle pas le serveur et ne notifie
 * personne : `WorkManager`, `refresh-itinerary` et les notifications appartiennent au lot suivant,
 * qui se branchera ici.
 *
 * Il est distinct de [FavoritesRepository] plutôt qu'ajouté à son contrat, parce que ce contrat est
 * figé depuis le jalon 1 et que la surveillance a son propre cycle de vie ; les deux dépôts
 * partagent la même base, et supprimer un trajet favori supprime sa surveillance.
 */
interface WatchedJourneysRepository {
  /** Les surveillances en cours, cinq au plus (voir `JourneyWatchLimit`). */
  val watched: Flow<List<WatchedJourney>>

  /**
   * Met [journeyId] sous surveillance, ou reconfigure la surveillance existante.
   *
   * Le refus n'est pas une erreur technique : atteindre la limite de cinq est un cas normal, que
   * l'appelant traduit en message. D'où un [WatchDecision] rendu en succès, et non un
   * `Outcome.Failure`.
   */
  suspend fun watch(journeyId: Long, schedule: WatchSchedule): Outcome<WatchDecision>

  /** Retire la surveillance sans toucher au favori lui-même. */
  suspend fun unwatch(journeyId: Long): Outcome<Unit>

  /** Retient l'`id` d'itinéraire à rejouer, ou l'oublie quand le serveur l'a rejeté. */
  suspend fun rememberItinerary(journeyId: Long, itineraryId: String?, capturedAt: Instant): Outcome<Unit>

  /** Note que le trajet vient d'être consulté : la vérification des 30 minutes s'en sert. */
  suspend fun markViewed(journeyId: Long, viewedAt: Instant): Outcome<Unit>
}
