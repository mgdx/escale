package io.github.mgdx.escale.core.model

/**
 * Une page de résultats d'itinéraire, décalque de la réponse de `GET /api/v6/plan`.
 *
 * Les deux curseurs alimentent les boutons « Plus tôt » et « Plus tard » : la requête d'origine est
 * renvoyée telle quelle, seul le curseur change (SPEC.md § 5.2).
 */
data class JourneyPage(
  /** Trajets dépendant des horaires, issus du champ `itineraries`. */
  val journeys: List<Journey>,
  /**
   * Trajets sans horaire (marche, vélo, voiture), issus du champ `direct`. Le serveur les renvoie
   * à part parce qu'ils ne dépendent pas de l'heure demandée.
   */
  val direct: List<Journey> = emptyList(),
  val previousPageCursor: String? = null,
  val nextPageCursor: String? = null,
) {
  /** Vrai quand le serveur n'a rien trouvé : l'interface affiche un état vide explicite (SPEC.md § 8). */
  val isEmpty: Boolean
    get() = journeys.isEmpty() && direct.isEmpty()
}

/** Une page de prochains départs, décalque de la réponse de `GET /api/v6/stoptimes`. */
data class StopTimePage(
  val entries: List<StopTimeEntry>,
  /** L'arrêt interrogé, tel que le serveur le décrit. */
  val stop: Stop?,
  val previousPageCursor: String? = null,
  val nextPageCursor: String? = null,
)
