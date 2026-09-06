package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.CategoryOrder
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyFeed
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneySort
import io.github.mgdx.escale.core.model.sorted
import io.github.mgdx.escale.core.result.EscaleError
import java.time.Duration
import java.time.Instant

/** Le sens d'une pagination : « Plus tôt » ou « Plus tard » (SPEC.md § 5.2). */
enum class ResultsPage {
  EARLIER,
  LATER,
}

/**
 * Le filtre de l'onglet Vélo (SPEC.md § 5.2) : vélo personnel et vélo partagé cohabitent dans la
 * même liste, et l'usager doit pouvoir n'en voir qu'un.
 */
enum class BikeFilter {
  ALL,
  OWN,
  SHARED,
}

/**
 * Ce qu'un onglet annonce **sous son libellé** : la durée du trajet le plus rapide qu'il propose
 * (SPEC.md § 5.2).
 *
 * Les trois cas sont distincts à l'œil comme au lecteur d'écran : une catégorie dont la réponse
 * n'est pas encore arrivée ne dit pas la même chose qu'une catégorie qui n'a rien trouvé. Les
 * confondre ferait passer une attente pour un échec.
 */
sealed interface TabHeadline {

  /** La requête de cet onglet est partie, sa réponse n'est pas là. */
  data object Pending : TabHeadline

  /** L'onglet a répondu et n'a rien à proposer : liste vide, ou requête en échec. */
  data object None : TabHeadline

  /** La durée du trajet le plus rapide de l'onglet. */
  data class Fastest(val duration: Duration) : TabHeadline
}

/**
 * L'état d'un onglet. Les quatre onglets ont chacun le leur, et **un onglet jamais ouvert reste à
 * sa valeur par défaut** : ni requête, ni chargement, ni erreur (SPEC.md § 7.3).
 *
 * [feed] nul signifie « jamais chargé » ; [feed] vide signifie « le serveur n'a rien trouvé ». La
 * distinction n'est pas cosmétique : le second cas doit afficher l'état vide explicite de
 * SPEC.md § 5.2, le premier ne doit rien afficher du tout.
 */
data class TabResults(
  val loading: Boolean = false,
  /** Non nul pendant qu'une page supplémentaire arrive : la liste reste affichée. */
  val paging: ResultsPage? = null,
  /** Vrai pendant un rafraîchissement du temps réel : la liste reste affichée, elle aussi. */
  val refreshing: Boolean = false,
  val feed: JourneyFeed? = null,
  val error: EscaleError? = null,
  /**
   * Heure du dernier chargement réussi, `null` tant qu'il n'y en a pas eu.
   *
   * Elle ne sert qu'à une chose : décider, au retour au premier plan, si les horaires ont plus de
   * 60 secondes (SPEC.md § 7.4). Le seuil et la comparaison sont dans `RealtimeRefreshPolicy`.
   */
  val loadedAt: Instant? = null,
) {
  /** Vrai quand l'onglet a répondu et n'a rien trouvé. */
  val isEmpty: Boolean
    get() = feed != null && feed.isEmpty

  /**
   * Ce que l'onglet annonce sous son libellé (SPEC.md § 5.2).
   *
   * Un onglet en échec annonce la même chose qu'un onglet vide : de son point de vue, il n'a aucun
   * trajet à proposer. Le motif de l'échec, lui, s'affiche dans l'onglet ouvert, pas sur sa
   * languette.
   */
  val headline: TabHeadline
    get() {
      val fastest = feed?.fastestDuration
      return when {
        fastest != null -> TabHeadline.Fastest(fastest)
        feed != null || error != null -> TabHeadline.None
        else -> TabHeadline.Pending
      }
    }
}

/**
 * L'état de la feuille de résultats (SPEC.md § 5.2).
 *
 * [open] suit le brouillon de recherche : tant que départ et arrivée ne sont pas tous les deux
 * renseignés, la feuille n'existe pas et la carte occupe tout l'écran (SPEC.md § 5.1).
 */
data class ResultsUiState(
  val open: Boolean = false,
  val category: JourneyCategory = JourneyCategory.TRANSIT,
  /**
   * L'ordre des languettes, réglé par l'usager (SPEC.md § 5.6).
   *
   * Il ne change **que** leur disposition : les quatre catégories sont toujours là, toujours
   * chargées, et leurs requêtes sont les mêmes. Il décide en revanche de l'ordre de chargement des
   * trois onglets non consultés, qui suivent les languettes (SPEC.md § 5.2).
   */
  val categoryOrder: List<JourneyCategory> = CategoryOrder.DEFAULT,
  val tabs: Map<JourneyCategory, TabResults> = emptyMap(),
  val bikeFilter: BikeFilter = BikeFilter.ALL,
  /**
   * L'ordre de présentation de la liste, **onglet par onglet** (SPEC.md § 5.2).
   *
   * Chaque catégorie garde le sien : trier le transport en commun par correspondances ne réordonne
   * pas les trois autres listes, et y revenir retrouve l'ordre qu'on y avait choisi. Une catégorie
   * absente de la table est une catégorie qu'on n'a jamais triée : elle s'en tient à l'ordre des
   * départs.
   *
   * Le tri ne vaut que pour la liste affichée : les durées annoncées sous les languettes sont
   * celles du trajet le plus rapide de chaque catégorie, que l'ordre ne touche pas. Et il ne
   * déclenche aucune requête — la liste visible en est simplement dérivée.
   */
  val sorts: Map<JourneyCategory, JourneySort> = emptyMap(),
  /** Clé du trajet choisi, au sens de `Journey.stableKey()`. */
  val selectedKey: String? = null,
) {
  /** L'onglet consulté. Un onglet inconnu de [tabs] est un onglet dont la requête n'est pas partie. */
  val current: TabResults
    get() = tabs[category] ?: TabResults()

  /** L'ordre de la liste de l'onglet consulté, celui des départs tant qu'on n'en a pas choisi d'autre. */
  val sort: JourneySort
    get() = sorts[category] ?: JourneySort.DEPARTURE

  /** Ce qu'annonce l'onglet [category], y compris tant que sa requête n'est pas partie. */
  fun headlineOf(category: JourneyCategory): TabHeadline = (tabs[category] ?: TabResults()).headline

  /** Les trajets à afficher, dans l'ordre choisi et filtre de l'onglet Vélo compris. */
  val visibleJourneys: List<Journey>
    get() {
      val journeys = current.feed?.sorted(sort).orEmpty()
      if (category != JourneyCategory.BIKE) return journeys
      return when (bikeFilter) {
        BikeFilter.ALL -> journeys
        BikeFilter.OWN -> journeys.filterNot { it.usesRental }
        BikeFilter.SHARED -> journeys.filter { it.usesRental }
      }
    }

  /**
   * Vrai quand le sélecteur de tri a un sens : il faut au moins deux trajets pour qu'un ordre en
   * distingue un du suivant, et un sélecteur qui n'ordonne rien n'est qu'un encombrement.
   */
  val sortVisible: Boolean
    get() = visibleJourneys.size > 1

  /**
   * Vrai quand le filtre de l'onglet Vélo a un sens : il ne s'affiche que si les deux familles
   * cohabitent réellement, un filtre qui ne filtre rien n'étant qu'un encombrement.
   */
  val bikeFilterVisible: Boolean
    get() {
      if (category != JourneyCategory.BIKE) return false
      val journeys = current.feed?.journeys.orEmpty()
      return journeys.any { it.usesRental } && journeys.any { !it.usesRental }
    }
}

/** Vrai si le trajet emprunte un véhicule en libre-service. */
private val Journey.usesRental: Boolean
  get() = legs.any { it is JourneyLeg.Rental }
