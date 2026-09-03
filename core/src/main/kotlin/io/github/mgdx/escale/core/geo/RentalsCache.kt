package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * SPEC.md § 5.7, règle 4 : « **60 secondes** pour les disponibilités en libre-service (donnée
 * volatile) ».
 *
 * Dix fois moins que les dix minutes de [STOPS_CACHE_LIFETIME], et c'est voulu : un arrêt de bus
 * est encore là demain, un vélo est parti dans la minute. Une disponibilité vieille de dix minutes
 * n'est pas une information un peu ancienne, c'est une information fausse, et l'usager qui s'y fie
 * arrive devant une station vide.
 */
val RENTALS_CACHE_LIFETIME: Duration = Duration.ofSeconds(RENTALS_CACHE_LIFETIME_SECONDS)

/** Les soixante secondes de la règle 4, écrites une seule fois. */
private const val RENTALS_CACHE_LIFETIME_SECONDS = 60L

/**
 * Nombre de réponses retenues avant d'oublier la plus ancienne.
 *
 * Même raison que pour les arrêts : le cache est en mémoire, le plafonner est le seul moyen d'éviter
 * qu'un long parcours de la carte finisse par retenir une région entière.
 */
const val RENTALS_CACHE_MAX_ENTRIES = 16

/** Ce qui a été demandé à `/api/v1/rentals` : les deux formes de filtre que l'application emploie. */
sealed interface RentalsQuery {

  /** Autour d'un point, pour la portion en libre-service d'un trajet (SPEC.md § 5.3). */
  data class Around(val point: LatLon, val radiusMeters: Int) : RentalsQuery

  /** Dans une emprise, pour les marqueurs de la carte (SPEC.md § 5.7). */
  data class Within(val area: BoundingBox) : RentalsQuery
}

/** Une réponse de `/api/v1/rentals` mémorisée, avec la demande qui l'a produite et son heure. */
data class RentalsCacheEntry(val query: RentalsQuery, val storedAt: Instant, val stations: List<RentalAvailability>) {

  /**
   * Vrai si cette entrée répond, à elle seule, à [query].
   *
   * Deux règles, et pas une de plus :
   *
   * 1. **la fraîcheur** : passé [lifetime], l'entrée ne vaut plus rien, quelle que soit la demande ;
   * 2. **la couverture** : une emprise déjà chargée couvre toute emprise incluse dedans, comme pour
   *    les arrêts. Une demande autour d'un point, en revanche, exige la **même** demande — un rayon
   *    plus large contient certes la station cherchée, mais rien ne garantit qu'un rayon plus étroit
   *    ait été centré au même endroit, et l'économie ne vaut pas le risque d'annoncer les vélos
   *    d'une station voisine.
   */
  fun answers(query: RentalsQuery, now: Instant, lifetime: Duration): Boolean =
    !isExpired(now, lifetime) && covers(query)

  private fun covers(requested: RentalsQuery): Boolean = when {
    query is RentalsQuery.Within && requested is RentalsQuery.Within -> query.area.covers(requested.area)
    else -> query == requested
  }

  private fun isExpired(now: Instant, lifetime: Duration): Boolean = !now.isBefore(storedAt.plus(lifetime))
}

/** La première entrée qui répond à la demande, ou `null` s'il faut interroger le serveur. */
fun List<RentalsCacheEntry>.answering(
  query: RentalsQuery,
  now: Instant,
  lifetime: Duration = RENTALS_CACHE_LIFETIME,
): RentalsCacheEntry? = firstOrNull { it.answers(query, now, lifetime) }

/**
 * Le cache mémoire des disponibilités en libre-service (SPEC.md § 5.7, règle 4).
 *
 * Il vit dans `:core` pour la même raison que [StopsCache] : il ne contient qu'une décision, elle se
 * teste en JVM, et c'est cette décision qui garantit qu'aucune donnée périmée n'est affichée comme
 * fraîche. Rien n'y est journalisé (SPEC.md § 8 et § 11).
 *
 * @param now source d'horloge, remplacée dans les tests pour faire vieillir une entrée sans attendre
 *   soixante secondes.
 */
class RentalsCache(
  private val lifetime: Duration = RENTALS_CACHE_LIFETIME,
  private val maxEntries: Int = RENTALS_CACHE_MAX_ENTRIES,
  private val now: () -> Instant = Instant::now,
) {

  private val mutex = Mutex()

  /** La plus récemment posée en tête : c'est aussi la plus susceptible de répondre. */
  private val entries = ArrayDeque<RentalsCacheEntry>()

  /** Les stations déjà connues pour cette demande, ou `null` s'il faut la poser au serveur. */
  suspend fun cached(query: RentalsQuery): List<RentalAvailability>? = mutex.withLock {
    entries.answering(query, now(), lifetime)?.stations
  }

  /** Mémorise une réponse du serveur. */
  suspend fun store(query: RentalsQuery, stations: List<RentalAvailability>) {
    mutex.withLock {
      entries.addFirst(RentalsCacheEntry(query = query, storedAt = now(), stations = stations))
      while (entries.size > maxEntries) entries.removeLast()
    }
  }

  /** Oublie tout : le serveur a changé, ses exploitants et ses identifiants avec lui (§ 5.6.1). */
  suspend fun clear() {
    mutex.withLock { entries.clear() }
  }
}
