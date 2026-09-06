package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.result.EscaleError
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

  /**
   * Réponse par onglet aux requêtes **détaillées**, celles qui portent la géométrie.
   *
   * Les tenir à part de [answers] est ce qui permet de distinguer la liste, demandée sans
   * géométrie, du repli qui va chercher le tracé d'un trajet direct (SPEC.md § 7, règle 6).
   */
  val detailedAnswers = mutableMapOf<JourneyCategory, Outcome<JourneyPage>>()

  /** [fresh] vaut vrai quand la requête contourne le cache pour du temps réel (SPEC.md § 7.4). */
  data class Call(
    val category: JourneyCategory,
    val cursor: String?,
    val fresh: Boolean = false,
    val detailedLegs: Boolean = false,
  )

  override suspend fun plan(
    query: SearchQuery,
    cursor: String?,
    detailedLegs: Boolean,
    fresh: Boolean,
  ): Outcome<JourneyPage> {
    calls += Call(query.category, cursor, fresh, detailedLegs)
    queries += query
    val replies = if (detailedLegs) detailedAnswers else answers
    return replies[query.category] ?: Outcome.Success(JourneyPage(journeys = emptyList()))
  }

  /**
   * Les identifiants d'itinéraire dont la feuille a demandé le tracé réel (SPEC.md § 5.1).
   *
   * Ils sont tenus à part de [calls] : ce sont deux requêtes de nature différente, et c'est
   * précisément leur nombre respectif qui prouve la règle — la liste reste sans géométrie, seul le
   * trajet mis en évidence en obtient une.
   */
  val refreshed = mutableListOf<String>()

  /** Réponse de `refresh` par identifiant d'itinéraire ; à défaut, un échec réseau. */
  val detailed = mutableMapOf<String, Outcome<Journey>>()

  override suspend fun refresh(itineraryId: String, detailedLegs: Boolean): Outcome<Journey> {
    refreshed += itineraryId
    return detailed[itineraryId] ?: Outcome.Failure(EscaleError.NoNetwork)
  }

  override suspend fun clearCache(): Outcome<Unit> = Outcome.Success(Unit)

  /** Les onglets interrogés, dans l'ordre, sans les doublons de pagination. */
  fun categories(): List<JourneyCategory> = calls.map { it.category }

  /** Le nombre de requêtes reçues pour un onglet donné. */
  fun callsTo(category: JourneyCategory): Int = calls.count { it.category == category }
}
