package io.github.mgdx.escale.ui.session

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.TimeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Ce que l'usager a saisi jusqu'ici dans la carte de recherche (SPEC.md § 5.1).
 *
 * C'est un brouillon, et non une [SearchQuery] : il existe dès le premier champ rempli, alors
 * qu'une requête n'a de sens qu'une fois les deux points connus.
 */
data class SearchDraft(val from: Location? = null, val to: Location? = null, val time: TimeChoice = TimeChoice.Now) {
  /** Vrai quand la recherche peut partir (SPEC.md § 5.1 : pas de bouton « Rechercher »). */
  val isComplete: Boolean get() = from != null && to != null
}

/**
 * La recherche en cours, **partagée entre la carte de recherche et la feuille de résultats**.
 *
 * SPEC.md § 5.1 ne laisse pas le choix : « dès que Départ et Arrivée sont renseignés, la recherche
 * se lance », sans bouton. La carte de recherche produit donc un état que la feuille de résultats
 * consomme, et cet état n'appartient ni à l'une ni à l'autre — il vit ici, dans `AppContainer`, et
 * les deux écrans l'observent sans se connaître (docs/architecture.md § 11.4).
 *
 * La classe est délibérément sans dépendance Android et sans effet de bord : elle ne lance aucune
 * requête, ne persiste rien, et **ne journalise rien**. Elle détient précisément ce que SPEC.md
 * § 11 interdit d'écrire dans une trace — des adresses et des coordonnées — y compris en débogage.
 */
class SearchSession {

  private val mutableDraft = MutableStateFlow(SearchDraft())

  val draft: StateFlow<SearchDraft> = mutableDraft.asStateFlow()

  fun setFrom(location: Location?) {
    mutableDraft.update { it.copy(from = location) }
  }

  fun setTo(location: Location?) {
    mutableDraft.update { it.copy(to = location) }
  }

  fun setTime(time: TimeChoice) {
    mutableDraft.update { it.copy(time = time) }
  }

  /**
   * Le bouton d'inversion de SPEC.md § 5.1. Il échange les deux points même quand l'un manque :
   * l'usager qui a saisi une arrivée et veut en faire son départ doit pouvoir le faire.
   */
  fun swap() {
    mutableDraft.update { it.copy(from = it.to, to = it.from) }
  }

  fun clear() {
    mutableDraft.value = SearchDraft()
  }

  /**
   * La requête à envoyer pour un onglet donné, ou `null` tant que le brouillon est incomplet.
   *
   * **Aucune règle n'est réimplémentée ici.** En particulier, le choix entre l'identifiant d'arrêt
   * et les coordonnées (docs/architecture.md § 11.3 : une requête par coordonnées autour d'une gare
   * rendait zéro résultat là où la même par `stopId` en rendait cinq) appartient à
   * `PlanQueryBuilder`, dans `:core`. Cette fonction se contente de lui transmettre les [Location]
   * intacts, avec leur `id` et leur `kind` — les reconstruire à partir des coordonnées affichées
   * serait exactement le contournement que ce paragraphe interdit.
   */
  fun toQuery(category: JourneyCategory, preferences: SearchPreferences): SearchQuery? {
    val current = mutableDraft.value
    val from = current.from ?: return null
    val to = current.to ?: return null
    return SearchQuery(
      from = from,
      to = to,
      time = current.time,
      category = category,
      preferences = preferences,
    )
  }
}
