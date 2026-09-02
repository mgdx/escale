package io.github.mgdx.escale.ui.detail

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.result.EscaleError
import java.time.Instant

/**
 * L'état de l'écran de détail d'un trajet (SPEC.md § 5.3).
 *
 * [journey] est d'abord le trajet **sommaire** choisi dans la liste de résultats, obtenu avec
 * `detailedLegs=false` : il porte les heures, les durées et les lignes, mais ni les arrêts
 * intermédiaires, ni les manœuvres, ni la géométrie (SPEC.md § 7.6). L'écran l'affiche tel quel
 * pendant que la requête détaillée part, plutôt que de montrer une page vide, puis le remplace
 * par le trajet complet dès qu'il arrive — [detailed] passe alors à vrai.
 *
 * Les trois ensembles de dépliage sont indexés sur la position de la portion dans [journey]. Ils
 * sont **le seul état sauvegardé** : un numéro de portion ne dit rien du trajet de l'usager, là où
 * le trajet lui-même n'a rien à faire sur le disque (SPEC.md § 11).
 */
data class DetailUiState(
  val journey: Journey? = null,
  /** Vrai quand le détail complet des portions est arrivé. */
  val detailed: Boolean = false,
  /** Vrai pendant la requête détaillée comme pendant un rafraîchissement. */
  val loading: Boolean = false,
  /** Échec de la requête détaillée. Le trajet sommaire reste affiché sous le bandeau (SPEC.md § 8). */
  val error: EscaleError? = null,
  /** Heure à laquelle le temps réel affiché a été obtenu, `null` tant qu'il ne l'a pas été. */
  val refreshedAt: Instant? = null,
  val expandedLegs: Set<Int> = emptySet(),
  /** Portions dont la liste des arrêts intermédiaires est dépliée. */
  val expandedStops: Set<Int> = emptySet(),
  /** Portions dont les instructions pas-à-pas sont dépliées. Jamais dépliées par défaut (§ 5.3). */
  val expandedSteps: Set<Int> = emptySet(),
  /**
   * Vrai quand il n'y a plus rien à montrer : aucun trajet n'est choisi, typiquement au retour
   * après la mort du processus. L'écran se referme au lieu d'afficher une page vide.
   */
  val closed: Boolean = false,
)
