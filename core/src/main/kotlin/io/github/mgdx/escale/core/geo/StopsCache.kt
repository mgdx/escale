package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * SPEC.md § 5.7, règle 4 : « cache par emprise et par palier, en mémoire, avec expiration :
 * 10 minutes pour les arrêts (donnée quasi statique) ».
 */
val STOPS_CACHE_LIFETIME: Duration = Duration.ofMinutes(STOPS_CACHE_LIFETIME_MINUTES)

/** Les dix minutes de la règle 4, écrites une seule fois. */
private const val STOPS_CACHE_LIFETIME_MINUTES = 10L

/**
 * Nombre d'emprises retenues avant d'oublier la plus ancienne.
 *
 * Le cache est en mémoire : le plafonner est le seul moyen d'éviter qu'un long parcours de la carte
 * finisse par retenir une région entière. Seize emprises couvrent largement les allers-retours d'un
 * usager sur une agglomération, chacune valant au plus quelques centaines d'arrêts.
 */
const val STOPS_CACHE_MAX_ENTRIES = 16

/**
 * Une réponse de `/api/v6/map/stops` mémorisée, avec l'emprise et les modes qui l'ont produite.
 *
 * Les modes tiennent lieu de palier : les jeux de modes des paliers de [ZoomTier] sont **emboîtés**
 * — ceux du zoom 11 sont un sous-ensemble de ceux du zoom 13 —, si bien que « l'entrée couvre-t-elle
 * ce palier ? » se lit exactement comme « l'entrée porte-t-elle au moins ces modes-là ? ».
 */
data class StopsCacheEntry(
  val area: BoundingBox,
  val modes: Set<TransitMode>,
  val storedAt: Instant,
  val stops: List<Stop>,
) {

  /**
   * Vrai si cette entrée répond, à elle seule, à une demande sur [area] pour [modes].
   *
   * Les trois conditions de la règle 4, dans l'ordre du moins cher au plus cher à évaluer :
   *
   * 1. **les modes** : une entrée obtenue pour un palier plus riche répond aussi à un palier plus
   *    pauvre. C'est ce qui fait qu'un dézoom suivi d'un déplacement n'émet rien ;
   * 2. **la fraîcheur** : passé [lifetime], l'entrée ne vaut plus rien ;
   * 3. **l'emprise** : *couverte*, c'est-à-dire **incluse**, et non égale. C'est là que se joue
   *    l'économie de requêtes, puisque l'emprise stockée est celle de l'écran élargie de 30 %.
   */
  fun answers(area: BoundingBox, modes: Set<TransitMode>, now: Instant, lifetime: Duration): Boolean =
    coversModes(modes) && !isExpired(now, lifetime) && this.area.covers(area)

  /**
   * Un ensemble vide veut dire « tous les modes » côté API, des deux côtés de la comparaison : une
   * entrée sans mode répond à tout, et une demande sans mode n'est satisfaite que par une telle
   * entrée — jamais par une réponse filtrée sur les seuls modes ferrés.
   */
  private fun coversModes(requested: Set<TransitMode>): Boolean = when {
    modes.isEmpty() -> true
    requested.isEmpty() -> false
    else -> modes.containsAll(requested)
  }

  private fun isExpired(now: Instant, lifetime: Duration): Boolean = !now.isBefore(storedAt.plus(lifetime))
}

/**
 * La première entrée qui répond à la demande, ou `null` s'il faut interroger le serveur.
 *
 * Fonction pure, et c'est délibéré : la décision « faut-il redemander cette emprise ? » est la plus
 * facile à croire juste sans l'avoir vérifiée, et la plus coûteuse quand elle ne l'est pas.
 */
fun List<StopsCacheEntry>.answering(
  area: BoundingBox,
  modes: Set<TransitMode>,
  now: Instant,
  lifetime: Duration = STOPS_CACHE_LIFETIME,
): StopsCacheEntry? = firstOrNull { it.answers(area, modes, now, lifetime) }

/**
 * Le cache mémoire des arrêts de la carte (SPEC.md § 5.7, règle 4).
 *
 * Il vit dans `:core` parce qu'il ne contient qu'une décision, sans la moindre dépendance Android,
 * et que cette décision se teste en JVM. Rien n'y est journalisé : une emprise est une donnée de
 * localisation (SPEC.md § 8 et § 11).
 *
 * @param now source d'horloge, remplacée dans les tests pour faire vieillir une entrée sans attendre.
 */
class StopsCache(
  private val lifetime: Duration = STOPS_CACHE_LIFETIME,
  private val maxEntries: Int = STOPS_CACHE_MAX_ENTRIES,
  private val now: () -> Instant = Instant::now,
) {

  private val mutex = Mutex()

  /** La plus récemment posée en tête : c'est aussi la plus susceptible de répondre. */
  private val entries = ArrayDeque<StopsCacheEntry>()

  /** Les arrêts déjà connus pour cette emprise et ce palier, ou `null` s'il faut demander. */
  suspend fun cached(area: BoundingBox, modes: Set<TransitMode>): List<Stop>? = mutex.withLock {
    entries.answering(area, modes, now(), lifetime)?.stops
  }

  /** Mémorise une réponse du serveur. */
  suspend fun store(area: BoundingBox, modes: Set<TransitMode>, stops: List<Stop>) {
    mutex.withLock {
      entries.addFirst(StopsCacheEntry(area = area, modes = modes, storedAt = now(), stops = stops))
      while (entries.size > maxEntries) entries.removeLast()
    }
  }

  /** Oublie tout : le serveur a changé, les identifiants d'arrêts avec lui (SPEC.md § 5.6.1). */
  suspend fun clear() {
    mutex.withLock { entries.clear() }
  }
}
