package io.github.mgdx.escale.ui.detail

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.RentalAvailability
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
   * Disponibilité des stations de libre-service, par position de portion (SPEC.md § 5.3).
   *
   * Une portion absente de cette table n'a **rien demandé** : soit elle n'a pas été dépliée, soit
   * elle décrit un véhicule en free-floating, qui n'a pas de station dont compter les vélos.
   */
  val rentals: Map<Int, RentalLegAvailability> = emptyMap(),
  /**
   * Vrai quand il n'y a plus rien à montrer : aucun trajet n'est choisi, typiquement au retour
   * après la mort du processus. L'écran se referme au lieu d'afficher une page vide.
   */
  val closed: Boolean = false,
)

/**
 * Ce que l'on sait de la disponibilité d'une portion en libre-service, à un instant donné.
 *
 * Les deux extrémités sont distinctes et le restent : la station de prise répond « combien de
 * véhicules », celle de retour « combien de places libres », et une seule des deux peut exister —
 * un vélo pris en free-floating et rendu en station, par exemple.
 *
 * L'heure du relevé n'est pas ici mais sur [RentalAvailability.retrievedAt] : elle vient du dépôt,
 * qui sait quand la réponse a été obtenue, et pas de l'écran, qui ne sait que quand il l'affiche.
 * C'est la différence entre dater une information et dater son affichage (SPEC.md § 5.3).
 */
data class RentalLegAvailability(
  /** Vrai pendant la requête, y compris lors d'un rafraîchissement d'une valeur déjà affichée. */
  val loading: Boolean = false,
  /** Station de prise, ou `null` si la portion n'en a pas ou si le serveur ne l'a pas rendue. */
  val pickup: RentalAvailability? = null,
  /** Station de retour, ou `null` si la portion n'en a pas ou si le serveur ne l'a pas rendue. */
  val dropoff: RentalAvailability? = null,
  /** Échec de la requête. La valeur précédemment obtenue, si elle existe, reste affichée (§ 8). */
  val error: EscaleError? = null,
)
