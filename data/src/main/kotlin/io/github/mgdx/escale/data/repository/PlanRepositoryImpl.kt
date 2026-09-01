package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.mapper.toDomain
import io.github.mgdx.escale.data.net.MotisClient
import io.github.mgdx.escale.data.net.PlanApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recherche d'itinéraire (SPEC.md § 5.2), sous les règles de sobriété réseau de SPEC.md § 7.
 *
 * Ce qui est garanti ici, et qui n'a donc pas à être refait ailleurs :
 * - **§ 7.2** : une seule requête en vol par onglet. Une nouvelle recherche sur le même onglet
 *   annule celle qui court ; deux appels identiques partagent la même requête au lieu d'en émettre
 *   deux.
 * - **§ 7.3** : aucune requête n'est déclenchée d'elle-même. Un onglet que l'usager n'ouvre pas
 *   n'appelle jamais [plan] : ce dépôt est entièrement passif, il n'a pas de boucle à lui.
 * - **§ 7.5** : les pages obtenues sont mises en cache mémoire pour la durée de la recherche.
 * - **§ 7.8** : expiration de 30 s, reprise unique, aucune reprise sur 4xx — c'est déjà la
 *   politique du [MotisClient], vérifiée avant d'être réécrite ici. Elle ne l'est donc pas.
 *
 * Une recherche par onglet, jamais une requête mixte : côté MOTIS, un trajet en transport en commun
 * plus lent que le meilleur trajet direct est éliminé pendant la recherche, si bien que mélanger
 * `transitModes` et `directModes` ferait disparaître des résultats (SPEC.md § 5.2).
 */
class PlanRepositoryImpl(
  client: MotisClient,
  private val servers: ServerRepository,
  private val cache: PlanCache,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PlanRepository {

  private val api = PlanApi(client)

  private val mutex = Mutex()

  /** La requête qui court, par onglet. Au plus une par catégorie, jamais quatre en parallèle. */
  private val inFlight = mutableMapOf<JourneyCategory, Request>()

  private class Request(val key: PlanCache.Key, val work: Deferred<Outcome<JourneyPage>>)

  // L'annulation d'une requête supplantée est délibérément avalée : ce n'est pas une panne, mais
  // le comportement voulu par SPEC.md § 7.2, et son message ne dirait rien d'utile.
  @Suppress("SwallowedException")
  override suspend fun plan(query: SearchQuery, cursor: String?, detailedLegs: Boolean): Outcome<JourneyPage> {
    val key = PlanCache.Key(servers.current.first().baseUrl, query, cursor, detailedLegs)
    cache.get(key)?.let { return Outcome.Success(it) }

    val request = mutex.withLock {
      val running = inFlight[query.category]
      if (running != null && running.key == key && running.work.isActive) {
        // Même requête déjà en vol : la partager plutôt que d'en émettre une seconde.
        running
      } else {
        // SPEC.md § 7.2 : une nouvelle recherche annule celle qui court sur cet onglet.
        running?.work?.cancel()
        Request(key, scope.async { fetch(key) }).also { inFlight[query.category] = it }
      }
    }

    val outcome = try {
      request.work.await()
    } catch (superseded: CancellationException) {
      // Si c'est l'appelant lui-même qui a été annulé, l'annulation doit continuer de se propager.
      currentCoroutineContext().ensureActive()
      return Outcome.Failure(EscaleError.Superseded)
    }
    if (outcome is Outcome.Success) cache.put(key, outcome.value)
    mutex.withLock { if (inFlight[query.category] === request) inFlight.remove(query.category) }
    return outcome
  }

  override suspend fun refresh(itineraryId: String, detailedLegs: Boolean): Outcome<Journey> {
    val baseUrl = servers.current.first().baseUrl
    // Volontairement hors cache : tout l'intérêt d'un rafraîchissement est d'aller rechercher les
    // données temps réel du moment.
    return when (val outcome = api.refreshItinerary(baseUrl, itineraryId, detailedLegs)) {
      is Outcome.Success -> Outcome.Success(outcome.value.toDomain())
      is Outcome.Failure -> outcome
    }
  }

  override suspend fun clearCache(): Outcome<Unit> {
    cache.clear()
    return Outcome.Success(Unit)
  }

  private suspend fun fetch(key: PlanCache.Key): Outcome<JourneyPage> =
    when (val outcome = api.plan(key.baseUrl, key.query, key.cursor, key.detailedLegs)) {
      is Outcome.Success -> Outcome.Success(outcome.value.toDomain())
      is Outcome.Failure -> outcome
    }
}
