package io.github.mgdx.escale.core.query

import java.time.Duration
import java.time.Instant

/** Durée au-delà de laquelle des horaires temps réel sont considérés périmés (SPEC.md § 7.4). */
private const val MAX_AGE_SECONDS = 60L

/**
 * **La règle de fraîcheur du temps réel (SPEC.md § 7.4), et le seul endroit qui la porte.**
 *
 * « Aucun polling. Le rafraîchissement du temps réel est déclenché par l'utilisateur (« tirer pour
 * rafraîchir »), ou au retour au premier plan si les données ont plus de 60 secondes. »
 *
 * Deux déclencheurs, deux traitements distincts :
 * - le **geste** de l'usager rafraîchit toujours, sans condition : il n'a pas à consulter cet objet ;
 * - le **retour au premier plan** ne rafraîchit que si [shouldRefreshOnForeground] le dit. C'est ce
 *   qui empêche un aller-retour vers une autre application d'émettre une requête à chaque fois.
 *
 * La règle vit dans `:core` parce qu'elle est arithmétique et qu'elle doit être vérifiable en JVM
 * (docs/architecture.md § 1). L'écran de résultats fournit les deux instants et n'en décide rien.
 */
object RealtimeRefreshPolicy {

  /** L'âge à partir duquel des horaires sont périmés : 60 secondes, mot pour mot (SPEC.md § 7.4). */
  val MAX_AGE: Duration = Duration.ofSeconds(MAX_AGE_SECONDS)

  /**
   * Faut-il rafraîchir au retour au premier plan ?
   *
   * @param lastLoadedAt instant du dernier chargement réussi, `null` si rien n'a encore été chargé.
   * @param now instant courant.
   * @return `true` si les données ont atteint [MAX_AGE], `false` sinon.
   *
   * Deux cas limites, tranchés ici pour qu'ils ne le soient pas dans chaque écran :
   * - **rien n'a été chargé** : il n'y a rien à rafraîchir, donc `false`. L'écran vide déclenche sa
   *   première recherche par son chemin normal, pas par un rafraîchissement ;
   * - **l'horloge a reculé** (changement de fuseau, heure réglée à la main) : l'âge n'est plus
   *   calculable, on rafraîchit. Une requête de trop vaut mieux qu'un horaire faux affiché comme
   *   temps réel.
   */
  fun shouldRefreshOnForeground(lastLoadedAt: Instant?, now: Instant): Boolean {
    if (lastLoadedAt == null) return false
    if (now.isBefore(lastLoadedAt)) return true
    return Duration.between(lastLoadedAt, now) >= MAX_AGE
  }
}
