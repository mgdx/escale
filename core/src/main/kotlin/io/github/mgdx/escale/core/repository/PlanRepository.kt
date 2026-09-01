package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.result.Outcome

/** Calcul d'itinéraire (`/api/v6/plan`, `/api/v6/refresh-itinerary`). */
interface PlanRepository {
  /**
   * Calcule les trajets d'une catégorie.
   *
   * Une seule requête par catégorie peut être en vol : l'implémentation annule la précédente
   * (SPEC.md § 7.2). Les résultats sont mis en cache mémoire pour la durée de la recherche.
   *
   * @param cursor `previousPageCursor` ou `nextPageCursor` d'une page déjà obtenue, pour les
   *   boutons « Plus tôt » et « Plus tard ». La requête est par ailleurs renvoyée telle quelle.
   * @param detailedLegs `false` pour la liste de résultats, `true` seulement à l'ouverture d'un
   *   trajet : la réponse est nettement plus lourde (SPEC.md § 7.6).
   */
  suspend fun plan(query: SearchQuery, cursor: String? = null, detailedLegs: Boolean = false): Outcome<JourneyPage>

  /**
   * Recalcule un trajet déjà obtenu avec les données temps réel du moment, sans relancer une
   * recherche complète.
   *
   * [itineraryId] est marqué « expérimental » côté MOTIS et peut devenir invalide après une mise à
   * jour d'horaires : un échec en 400 ou 404 impose de rejouer la requête `plan` d'origine
   * (SPEC.md § 5.5.1).
   */
  suspend fun refresh(itineraryId: String, detailedLegs: Boolean = true): Outcome<Journey>
}
