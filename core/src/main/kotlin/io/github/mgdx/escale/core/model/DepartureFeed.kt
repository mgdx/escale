package io.github.mgdx.escale.core.model

/**
 * La liste des prochains départs telle qu'elle s'accumule au fil des pages (SPEC.md § 5.4).
 *
 * Exact pendant de [JourneyFeed] pour l'écran des départs, et pour les mêmes raisons : « plus tôt »
 * et « plus tard » **étendent** la liste affichée au lieu de la remplacer, le recollement est une
 * règle à part entière — ordre, doublons, curseurs à conserver — et il vit donc en Kotlin pur, où
 * il se vérifie en JVM (docs/architecture.md § 1).
 *
 * Le dédoublonnage n'est pas cosmétique : le paramètre `n` de `/api/v6/stoptimes` est un
 * **minimum**, et le serveur complète toujours la dernière minute atteinte. Deux pages voisines se
 * recouvrent donc régulièrement de quelques départs, qui s'afficheraient deux fois.
 */
data class DepartureFeed(
  val entries: List<StopTimeEntry> = emptyList(),
  /** Curseur de la page précédente, `null` quand le serveur n'en propose pas : bouton masqué. */
  val previousPageCursor: String? = null,
  /** Curseur de la page suivante, `null` quand le serveur n'en propose pas : bouton masqué. */
  val nextPageCursor: String? = null,
) {
  val isEmpty: Boolean
    get() = entries.isEmpty()

  /**
   * « Plus tôt » : les départs de [page] rejoignent la liste, et c'est le curseur *précédent* qui
   * avance. Le curseur suivant, lui, continue de désigner la fin de la liste affichée.
   */
  fun earlier(page: StopTimePage): DepartureFeed = DepartureFeed(
    entries = merge(page.entries, entries),
    previousPageCursor = page.previousPageCursor,
    nextPageCursor = nextPageCursor,
  )

  /** « Plus tard » : symétrique de [earlier]. */
  fun later(page: StopTimePage): DepartureFeed = DepartureFeed(
    entries = merge(entries, page.entries),
    previousPageCursor = previousPageCursor,
    nextPageCursor = page.nextPageCursor,
  )

  companion object {
    /** La liste de départ, à la première page ou après un rafraîchissement. */
    fun of(page: StopTimePage): DepartureFeed = DepartureFeed(
      entries = merge(page.entries, emptyList()),
      previousPageCursor = page.previousPageCursor,
      nextPageCursor = page.nextPageCursor,
    )

    /**
     * Réunit deux listes en écartant les doublons, puis trie par heure effective, l'horaire
     * théorique départageant les ex æquo.
     *
     * En cas de doublon, c'est l'exemplaire du premier argument qui est retenu : sur un
     * rafraîchissement, la donnée la plus récente est passée en premier, et c'est elle qui porte le
     * retard à jour.
     */
    private fun merge(first: List<StopTimeEntry>, second: List<StopTimeEntry>): List<StopTimeEntry> = (first + second)
      .distinctBy { it.stableKey() }
      .sortedWith(compareBy({ it.time }, { it.scheduledTime }))
  }
}

/**
 * De quoi reconnaître deux fois le même départ, et le désigner dans une liste.
 *
 * L'identifiant de course fait foi quand il existe, associé à l'horaire théorique : une même course
 * repasse au même arrêt le lendemain, et deux passages distincts ne doivent pas se confondre. Sans
 * identifiant — une course ajoutée en temps réel n'en a pas —, la ligne, la girouette et le quai
 * suffisent à distinguer deux départs de la même minute.
 *
 * La clé sert aussi de `key` de liste à l'interface, ce qui lui évite d'inventer la sienne.
 */
fun StopTimeEntry.stableKey(): String =
  listOf(tripId.ifBlank { "$lineName/$headsign/${track.orEmpty()}" }, scheduledTime).joinToString("|")
