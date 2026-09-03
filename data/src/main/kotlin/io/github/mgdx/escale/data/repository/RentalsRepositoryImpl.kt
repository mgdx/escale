package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.geo.RentalsCache
import io.github.mgdx.escale.core.geo.RentalsQuery
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.RentalsApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Le libre-service, avec le cache de **soixante secondes** de SPEC.md § 5.7, règle 4.
 *
 * C'est la seule différence de fond avec [StopsRepositoryImpl], et elle est décisive : dix minutes
 * pour un arrêt de bus, soixante secondes pour une disponibilité. Une station annoncée à sept vélos
 * il y a dix minutes est peut-être vide ; l'usager qui s'y rend n'a aucun moyen de le savoir, et
 * c'est pour cela que la spec impose à la fois cette expiration, l'heure du relevé à l'écran et un
 * bouton de rafraîchissement.
 *
 * La décision « cette demande est-elle déjà couverte ? » n'est pas ici mais dans [RentalsCache], en
 * Kotlin pur, où elle se vérifie en JVM. Ce dépôt ne fait que l'interroger avant d'appeler le
 * serveur, et lui remettre ce que le serveur a rendu.
 *
 * Comme pour les arrêts, le serveur est relu à chaque appel plutôt que retenu à la construction :
 * l'usager peut en changer pendant que l'écran est ouvert, et un changement de serveur **vide le
 * cache** — les exploitants d'un serveur ne sont pas ceux d'un autre.
 *
 * Rien n'est journalisé : une station de libre-service est une position précise (SPEC.md § 8, § 11).
 *
 * @param now source d'horloge du relevé, remplacée dans les tests. C'est elle qui date la réponse
 *   affichée, et le cache partage la sienne pour que les deux vieillissent ensemble.
 */
class RentalsRepositoryImpl(
  private val api: RentalsApi,
  private val serverRepository: ServerRepository,
  private val cache: RentalsCache = RentalsCache(),
  private val now: () -> Instant = Instant::now,
) : RentalsRepository {

  private val serverMutex = Mutex()
  private var lastBaseUrl: String? = null

  override suspend fun stationsIn(area: BoundingBox): Outcome<List<RentalAvailability>> {
    val query = RentalsQuery.Within(area)
    return load(query) { baseUrl, retrievedAt -> api.within(baseUrl, area, retrievedAt) }
  }

  override suspend fun availabilityNear(point: LatLon, radiusMeters: Int): Outcome<List<RentalAvailability>> {
    val query = RentalsQuery.Around(point, radiusMeters)
    return load(query) { baseUrl, retrievedAt -> api.around(baseUrl, point, radiusMeters, retrievedAt) }
  }

  /**
   * Le cache d'abord, le serveur ensuite, et jamais l'inverse.
   *
   * L'heure du relevé est prise **avant** l'appel et transmise au serveur comme au cache : c'est
   * elle qui s'affiche, et elle doit dater la réponse et son entrée de cache de la même valeur,
   * sans quoi une disponibilité rendue par le cache paraîtrait plus fraîche qu'elle ne l'est.
   */
  private suspend fun load(
    query: RentalsQuery,
    request: suspend (baseUrl: String, retrievedAt: Instant) -> Outcome<List<RentalAvailability>>,
  ): Outcome<List<RentalAvailability>> {
    val baseUrl = baseUrl()
    cache.cached(query)?.let { return Outcome.Success(it) }
    val outcome = request(baseUrl, now())
    if (outcome is Outcome.Success) cache.store(query, outcome.value)
    return outcome
  }

  /** La racine du serveur courant, et le vidage du cache quand elle vient de changer. */
  private suspend fun baseUrl(): String {
    val current = serverRepository.current.first().baseUrl
    serverMutex.withLock {
      if (lastBaseUrl != null && lastBaseUrl != current) cache.clear()
      lastBaseUrl = current
    }
    return current
  }
}
