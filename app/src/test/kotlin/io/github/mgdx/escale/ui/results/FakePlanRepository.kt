package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.result.Outcome

/**
 * Un dépôt d'itinéraires qui compte ce qu'on lui demande.
 *
 * C'est le seul moyen de prouver la règle de SPEC.md § 7.3 : un onglet que personne n'ouvre ne
 * déclenche **aucune** requête. Un test qui se contenterait de regarder l'état affiché ne verrait
 * pas la différence entre « pas de requête » et « requête dont on ignore le résultat ».
 */
class FakePlanRepository : PlanRepository {

  /** Chaque appel reçu : l'onglet, et le curseur de pagination éventuel. */
  val calls = mutableListOf<Call>()

  /** Les requêtes reçues, entières : c'est là que se lisent les réglages de SPEC.md § 5.6. */
  val queries = mutableListOf<SearchQuery>()

  /** Réponse par onglet ; à défaut, une page vide. */
  val answers = mutableMapOf<JourneyCategory, Outcome<JourneyPage>>()

  /** [fresh] vaut vrai quand la requête contourne le cache pour du temps réel (SPEC.md § 7.4). */
  data class Call(val category: JourneyCategory, val cursor: String?, val fresh: Boolean = false)

  override suspend fun plan(
    query: SearchQuery,
    cursor: String?,
    detailedLegs: Boolean,
    fresh: Boolean,
  ): Outcome<JourneyPage> {
    calls += Call(query.category, cursor, fresh)
    queries += query
    return answers[query.category] ?: Outcome.Success(JourneyPage(journeys = emptyList()))
  }

  override suspend fun refresh(itineraryId: String, detailedLegs: Boolean): Outcome<Journey> =
    error("Le rafraîchissement ne relève pas de la feuille de résultats.")

  override suspend fun clearCache(): Outcome<Unit> = Outcome.Success(Unit)

  /** Les onglets interrogés, dans l'ordre, sans les doublons de pagination. */
  fun categories(): List<JourneyCategory> = calls.map { it.category }
}
