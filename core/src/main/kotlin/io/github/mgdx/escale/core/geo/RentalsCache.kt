package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.RentalAvailability
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * SPEC.md § 5.7, règle 4 : « cache par emprise et par palier, en mémoire, avec expiration :
 * 10 minutes pour les arrêts (donnée quasi statique), **60 secondes pour les disponibilités en
 * libre-service** (donnée volatile) ».
 *
 * **Dix fois plus court que [STOPS_CACHE_LIFETIME], et c'est délibéré** : un arrêt de bus est là
 * pour des années, un vélo disponible il y a dix minutes ne l'est plus. Les deux durées sont
 * déclarées séparément, et `RentalsCacheTest` vérifie qu'elles ne se rejoignent jamais.
 */
val RENTALS_CACHE_LIFETIME: Duration = Duration.ofSeconds(RENTALS_CACHE_LIFETIME_SECONDS)

/** Les soixante secondes de la règle 4, écrites une seule fois. */
private const val RENTALS_CACHE_LIFETIME_SECONDS = 60L

/**
 * Nombre d'emprises retenues avant d'oublier la plus ancienne.
 *
 * Plus petit que celui des arrêts : une entrée ne vaut qu'une minute, en garder seize n'aurait pour
 * effet que de retenir de la mémoire au profit d'entrées déjà périmées.
 */
const val RENTALS_CACHE_MAX_ENTRIES = 8

/**
 * Une réponse de `/api/v1/rentals` mémorisée, avec l'emprise qui l'a produite.
 *
 * **Pas de palier ici, à la différence de [StopsCacheEntry], et ce n'est pas un oubli** :
 * `/api/v1/rentals` ne prend pas de filtre équivalent au `modes` de `/api/v6/map/stops`
 * (`docs/motis-openapi.yaml`, opération `rentals` : `min`, `max`, `point`, `radius`, `providers`).
 * Une même réponse porte donc à la fois les stations et les véhicules isolés, et répond aux deux
 * paliers de [RentalMarkerKind] ; c'est le `minzoom` des couches, et lui seul, qui décide de ce qui
 * se voit. Redemander la même emprise en montant du zoom 14 au zoom 16 rendrait exactement les
 * mêmes octets.
 */
data class RentalsCacheEntry(
  val area: BoundingBox,
  val storedAt: Instant,
  val availabilities: List<RentalAvailability>,
) {

  /**
   * Vrai si cette entrée répond, à elle seule, à une demande sur [area].
   *
   * Les deux conditions de la règle 4, dans l'ordre du moins cher au plus cher à évaluer :
   *
   * 1. **la fraîcheur** : passé [lifetime] — soixante secondes —, l'entrée ne vaut plus rien, même
   *    si son emprise couvre encore tout l'écran ;
   * 2. **l'emprise** : *couverte*, c'est-à-dire **incluse**, et non égale. C'est là que se joue
   *    l'économie de requêtes, puisque l'emprise stockée est celle de l'écran élargie de 30 %.
   */
  fun answers(area: BoundingBox, now: Instant, lifetime: Duration): Boolean =
    !isExpired(now, lifetime) && this.area.covers(area)

  private fun isExpired(now: Instant, lifetime: Duration): Boolean = !now.isBefore(storedAt.plus(lifetime))
}

/**
 * La première entrée qui répond à la demande, ou `null` s'il faut interroger le serveur.
 *
 * Fonction pure, et c'est délibéré : la décision « faut-il redemander cette emprise ? » est la plus
 * facile à croire juste sans l'avoir vérifiée, et la plus coûteuse quand elle ne l'est pas.
 */
fun List<RentalsCacheEntry>.answering(
  area: BoundingBox,
  now: Instant,
  lifetime: Duration = RENTALS_CACHE_LIFETIME,
): RentalsCacheEntry? = firstOrNull { it.answers(area, now, lifetime) }

/**
 * Le cache mémoire des disponibilités en libre-service (SPEC.md § 5.7, règle 4).
 *
 * Jumeau de [StopsCache], à une différence près et une seule : **la durée de vie**. Il vit dans
 * `:core` parce qu'il ne contient qu'une décision, sans la moindre dépendance Android, et que cette
 * décision se teste en JVM. Rien n'y est journalisé : une emprise est une donnée de localisation
 * (SPEC.md § 8 et § 11).
 *
 * @param now source d'horloge, remplacée dans les tests pour faire vieillir une entrée sans attendre.
 */
class RentalsCache(
  private val lifetime: Duration = RENTALS_CACHE_LIFETIME,
  private val maxEntries: Int = RENTALS_CACHE_MAX_ENTRIES,
  private val now: () -> Instant = Instant::now,
) {

  private val mutex = Mutex()

  /** La plus récemment posée en tête : c'est aussi la plus susceptible de répondre. */
  private val entries = ArrayDeque<RentalsCacheEntry>()

  /** Les disponibilités déjà connues pour cette emprise, ou `null` s'il faut demander. */
  suspend fun cached(area: BoundingBox): List<RentalAvailability>? = mutex.withLock {
    entries.answering(area, now(), lifetime)?.availabilities
  }

  /** Mémorise une réponse du serveur. */
  suspend fun store(area: BoundingBox, availabilities: List<RentalAvailability>) {
    mutex.withLock {
      entries.addFirst(RentalsCacheEntry(area = area, storedAt = now(), availabilities = availabilities))
      while (entries.size > maxEntries) entries.removeLast()
    }
  }

  /** Oublie tout : le serveur a changé, les exploitants avec lui (SPEC.md § 5.6.1). */
  suspend fun clear() {
    mutex.withLock { entries.clear() }
  }
}
