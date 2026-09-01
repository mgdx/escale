package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.query.PlanQueryBuilder
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.dto.PlanItineraryDto
import io.github.mgdx.escale.data.dto.PlanResponseDto

/**
 * Le point d'entrée `plan` vu du dépôt : une recherche, une page, un rafraîchissement.
 *
 * C'est la couture entre `:core.query`, qui décide **quels** paramètres partent, et [MotisClient],
 * qui sait **comment** les envoyer. Le dépôt, lui, n'a plus à connaître ni les uns ni l'autre : il
 * ne s'occupe que du cache et de l'annulation.
 */
internal class PlanApi(private val client: MotisClient) {

  /**
   * @param cursor `previousPageCursor` ou `nextPageCursor` d'une page déjà obtenue. Le reste de la
   *   requête est reconstruit à l'identique depuis [query], comme l'exige SPEC.md § 5.2.
   */
  suspend fun plan(
    baseUrl: String,
    query: SearchQuery,
    cursor: String?,
    detailedLegs: Boolean,
  ): Outcome<PlanResponseDto> = client.plan(baseUrl, PlanQueryBuilder.build(query, cursor, detailedLegs))

  suspend fun refreshItinerary(
    baseUrl: String,
    itineraryId: String,
    detailedLegs: Boolean,
  ): Outcome<PlanItineraryDto> = client.refreshItinerary(baseUrl, PlanQueryBuilder.refresh(itineraryId, detailedLegs))
}
