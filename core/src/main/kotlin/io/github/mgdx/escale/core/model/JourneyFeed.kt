package io.github.mgdx.escale.core.model

/**
 * La liste de résultats d'un onglet, telle qu'elle s'accumule au fil des pages (SPEC.md § 5.2).
 *
 * Les boutons « Plus tôt » et « Plus tard » n'ouvrent pas une nouvelle liste : ils étendent celle
 * qui est déjà affichée. Le recollement des pages est une règle à part entière — ordre, doublons,
 * curseurs à conserver — et il est ici, en Kotlin pur, pour être vérifiable en JVM
 * (docs/architecture.md § 1).
 *
 * Les trajets sans horaire (champ `direct` de la réponse) sont mêlés aux autres : ils ne dépendent
 * pas de l'heure demandée, le serveur les renvoie donc **identiques à chaque page**, et sans
 * dédoublonnage une liste paginée trois fois afficherait trois fois le même trajet à pied.
 */
data class JourneyFeed(
  val journeys: List<Journey> = emptyList(),
  /** Curseur de la page précédente, `null` quand le serveur n'en propose pas : bouton masqué. */
  val previousPageCursor: String? = null,
  /** Curseur de la page suivante, `null` quand le serveur n'en propose pas : bouton masqué. */
  val nextPageCursor: String? = null,
) {
  val isEmpty: Boolean
    get() = journeys.isEmpty()

  /**
   * « Plus tôt » : les trajets de [page] rejoignent la liste, et c'est le curseur *précédent* qui
   * avance. Le curseur suivant, lui, continue de désigner la fin de la liste affichée.
   */
  fun earlier(page: JourneyPage): JourneyFeed = JourneyFeed(
    journeys = merge(page.all(), journeys),
    previousPageCursor = page.previousPageCursor,
    nextPageCursor = nextPageCursor,
  )

  /** « Plus tard » : symétrique de [earlier]. */
  fun later(page: JourneyPage): JourneyFeed = JourneyFeed(
    journeys = merge(journeys, page.all()),
    previousPageCursor = previousPageCursor,
    nextPageCursor = page.nextPageCursor,
  )

  companion object {
    /** La liste de départ, à la première page d'une recherche. */
    fun of(page: JourneyPage): JourneyFeed = JourneyFeed(
      journeys = merge(page.all(), emptyList()),
      previousPageCursor = page.previousPageCursor,
      nextPageCursor = page.nextPageCursor,
    )

    /**
     * Réunit deux listes en écartant les doublons, puis trie par heure de départ, l'arrivée la plus
     * proche d'abord à départ égal. En cas de doublon, c'est l'exemplaire du premier argument qui
     * est retenu : sur un rafraîchissement, la donnée la plus récente est passée en premier.
     */
    private fun merge(first: List<Journey>, second: List<Journey>): List<Journey> = (first + second)
      .distinctBy { it.identity() }
      .sortedWith(compareBy({ it.startTime }, { it.endTime }))

    /**
     * De quoi reconnaître deux fois le même trajet. L'identifiant du serveur fait foi quand il
     * existe ; sinon les horaires et la forme du trajet suffisent, un trajet direct étant toujours
     * rendu à l'identique d'une page à l'autre.
     */
    private fun Journey.identity(): Any =
      id ?: listOf(startTime, endTime, transfers, legs.size, legs.firstOrNull()?.from?.name)
  }
}

/** Les trajets d'une page, horaires et directs réunis dans l'ordre du serveur. */
private fun JourneyPage.all(): List<Journey> = journeys + direct
