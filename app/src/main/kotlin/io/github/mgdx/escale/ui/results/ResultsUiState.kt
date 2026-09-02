package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyFeed
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.result.EscaleError

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
  val feed: JourneyFeed? = null,
  val error: EscaleError? = null,
) {
  /** Vrai quand l'onglet a répondu et n'a rien trouvé. */
  val isEmpty: Boolean
    get() = feed != null && feed.isEmpty
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
  val tabs: Map<JourneyCategory, TabResults> = emptyMap(),
  val bikeFilter: BikeFilter = BikeFilter.ALL,
  /** Clé du trajet choisi, au sens de `Journey.stableKey()`. */
  val selectedKey: String? = null,
) {
  /** L'onglet consulté. Un onglet inconnu de [tabs] est un onglet que personne n'a encore ouvert. */
  val current: TabResults
    get() = tabs[category] ?: TabResults()

  /** Les trajets à afficher, filtre de l'onglet Vélo compris. */
  val visibleJourneys: List<Journey>
    get() {
      val journeys = current.feed?.journeys.orEmpty()
      if (category != JourneyCategory.BIKE) return journeys
      return when (bikeFilter) {
        BikeFilter.ALL -> journeys
        BikeFilter.OWN -> journeys.filterNot { it.usesRental }
        BikeFilter.SHARED -> journeys.filter { it.usesRental }
      }
    }

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
