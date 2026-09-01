package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.JourneyPage
import io.github.mgdx.escale.core.model.SearchQuery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache **mémoire** des réponses `plan`, pour la durée de la recherche (SPEC.md § 7.5).
 *
 * Il n'y a délibérément aucune persistance sur disque : une page de résultats contient l'origine et
 * la destination de l'usager, et SPEC.md § 11 ne veut pas la voir survivre à la session.
 *
 * L'entrée est indexée par le serveur interrogé, la recherche, le curseur et le niveau de détail.
 * Le serveur en fait partie pour que changer d'instance dans les réglages n'expose jamais le
 * résultat d'une autre (SPEC.md § 4.1) ; [clear] reste offert à l'écran « Serveur MOTIS » pour
 * vider explicitement l'ensemble.
 */
class PlanCache {

  private val mutex = Mutex()

  /**
   * Insertion ordonnée, plus ancienne d'abord : au-delà de [MAX_ENTRIES], la plus ancienne cède
   * sa place. Une recherche produit au plus quatre onglets fois quelques pages ; le plafond n'est
   * là que pour borner une session longue.
   */
  private val entries = LinkedHashMap<Key, JourneyPage>()

  internal data class Key(val baseUrl: String, val query: SearchQuery, val cursor: String?, val detailedLegs: Boolean)

  internal suspend fun get(key: Key): JourneyPage? = mutex.withLock { entries[key] }

  internal suspend fun put(key: Key, page: JourneyPage) = mutex.withLock {
    entries.remove(key)
    entries[key] = page
    while (entries.size > MAX_ENTRIES) {
      entries.remove(entries.keys.first())
    }
  }

  /** Vide le cache. À déclencher au changement de serveur MOTIS (SPEC.md § 4.1). */
  suspend fun clear() = mutex.withLock { entries.clear() }

  private companion object {
    const val MAX_ENTRIES = 32
  }
}
