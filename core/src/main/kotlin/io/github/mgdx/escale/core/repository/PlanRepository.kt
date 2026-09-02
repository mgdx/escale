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
   * @param fresh `true` pour ignorer le cache mémoire et redemander les horaires au serveur. C'est
   *   le seul moyen de rafraîchir le temps réel d'une liste de résultats (SPEC.md § 7.4) : sans
   *   lui, la réponse mise en cache serait rendue telle quelle et le geste de l'usager n'aurait
   *   aucun effet. Réservé aux deux déclencheurs du § 7.4 — « tirer pour rafraîchir » et le retour
   *   au premier plan sur des données périmées — jamais à un chargement ordinaire.
   */
  suspend fun plan(
    query: SearchQuery,
    cursor: String? = null,
    detailedLegs: Boolean = false,
    fresh: Boolean = false,
  ): Outcome<JourneyPage>

  /**
   * Recalcule un trajet déjà obtenu avec les données temps réel du moment, sans relancer une
   * recherche complète.
   *
   * [itineraryId] est marqué « expérimental » côté MOTIS et peut devenir invalide après une mise à
   * jour d'horaires : un échec en 400 ou 404 impose de rejouer la requête `plan` d'origine
   * (SPEC.md § 5.5.1).
   */
  suspend fun refresh(itineraryId: String, detailedLegs: Boolean = true): Outcome<Journey>

  /**
   * Vide le cache mémoire des résultats de recherche (SPEC.md § 7.5).
   *
   * L'écran « Serveur MOTIS » l'appelle au changement de serveur : les trajets d'une instance n'ont
   * rien à faire dans les résultats d'une autre (SPEC.md § 4.1). Les favoris et l'historique, eux,
   * ne sont pas concernés.
   */
  suspend fun clearCache(): Outcome<Unit>
}
