package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.geo.StopsCache
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.repository.StopsRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.StopsApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Les arrêts de la carte, avec le cache par emprise et par palier de SPEC.md § 5.7, règle 4.
 *
 * La décision « cette emprise est-elle déjà couverte ? » n'est pas ici : elle est dans
 * [StopsCache], en Kotlin pur, où elle se vérifie en JVM. Ce dépôt ne fait que l'interroger avant
 * d'appeler le serveur, et lui remettre ce que le serveur a rendu.
 *
 * Le serveur n'est pas retenu à la construction mais relu à chaque appel : l'usager peut en changer
 * pendant que la carte est ouverte (SPEC.md § 5.6.1). Un changement de serveur **vide le cache** —
 * les identifiants d'arrêts d'un serveur ne veulent rien dire sur un autre.
 *
 * Rien n'est journalisé : une emprise est une donnée de localisation (SPEC.md § 8 et § 11).
 */
class StopsRepositoryImpl(
  private val api: StopsApi,
  private val serverRepository: ServerRepository,
  private val cache: StopsCache = StopsCache(),
) : StopsRepository {

  private val serverMutex = Mutex()
  private var lastBaseUrl: String? = null

  override suspend fun stopsIn(area: BoundingBox, modes: Set<TransitMode>, grouped: Boolean): Outcome<List<Stop>> {
    val baseUrl = baseUrl()
    cache.cached(area, modes)?.let { return Outcome.Success(it) }
    val outcome = api.mapStops(baseUrl, area, modes, grouped)
    if (outcome is Outcome.Success) cache.store(area, modes, outcome.value)
    return outcome
  }

  override suspend fun stop(stopId: String): Outcome<Stop> = api.stop(baseUrl(), stopId)

  /**
   * La racine du serveur courant, et le vidage du cache quand elle vient de changer.
   *
   * Le contrôle est fait ici plutôt que dans une collecte du `Flow` : le dépôt n'a pas de portée de
   * coroutine à lui, et un cache d'arrêts qui survivrait à un changement de serveur poserait sur la
   * carte des marqueurs menant à des identifiants que le nouveau serveur ne connaît pas.
   */
  private suspend fun baseUrl(): String {
    val current = serverRepository.current.first().baseUrl
    serverMutex.withLock {
      if (lastBaseUrl != null && lastBaseUrl != current) cache.clear()
      lastBaseUrl = current
    }
    return current
  }
}
