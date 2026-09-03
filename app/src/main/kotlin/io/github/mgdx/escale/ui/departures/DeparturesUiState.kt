package io.github.mgdx.escale.ui.departures

import io.github.mgdx.escale.core.model.DepartureFeed
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.core.result.EscaleError
import java.time.Instant

/** Le sens d'une pagination : « Plus tôt » ou « Plus tard » (SPEC.md § 5.4). */
enum class DeparturesPage {
  EARLIER,
  LATER,
}

/**
 * L'état de l'écran des prochains départs (SPEC.md § 5.4).
 *
 * [feed] nul signifie « jamais chargé », [feed] vide signifie « le serveur n'a rien trouvé ». La
 * distinction n'est pas cosmétique, c'est celle de SPEC.md § 8 : le second cas affiche un état vide
 * explicite, le premier ne doit rien affirmer du tout.
 */
data class DeparturesUiState(
  /** Nom de l'arrêt, d'abord celui reçu par la navigation, puis celui que le serveur confirme. */
  val stopName: String = "",
  /**
   * Les lignes desservant l'arrêt, telles que `/api/v6/stop` les rend (SPEC.md § 5.4).
   *
   * Elles sont demandées **une seule fois**, à l'ouverture, et jamais redemandées : une desserte ne
   * change pas d'une minute à l'autre, là où les horaires en dessous changent sans arrêt. Vide tant
   * que la réponse n'est pas arrivée, ou quand elle a échoué : l'écran vaut sans elles.
   */
  val lines: List<StopLine> = emptyList(),
  val loading: Boolean = false,
  /** Non nul pendant qu'une page supplémentaire arrive : la liste reste affichée. */
  val paging: DeparturesPage? = null,
  /** Vrai pendant un rafraîchissement du temps réel : la liste reste affichée, elle aussi. */
  val refreshing: Boolean = false,
  val feed: DepartureFeed? = null,
  /**
   * Les puces de filtre offertes, choisies par `DepartureFilters` d'après les modes que le serveur
   * annonce. Vide quand l'arrêt n'est desservi que par un mode : filtrer n'y a aucun sens.
   */
  val filters: List<DepartureModeFilter> = emptyList(),
  /** La puce active, `null` pour « tous les modes ». */
  val filter: DepartureModeFilter? = null,
  val error: EscaleError? = null,
  /**
   * Heure du dernier chargement réussi, `null` tant qu'il n'y en a pas eu.
   *
   * Elle sert à deux choses, et à deux choses seulement : décider au retour au premier plan si les
   * horaires ont plus de 60 secondes (SPEC.md § 7.4), et dater le bandeau de perturbations pour
   * qu'il ne change pas au fil des secondes. Le seuil et la comparaison sont dans
   * `RealtimeRefreshPolicy`.
   */
  val loadedAt: Instant? = null,
) {
  val entries: List<StopTimeEntry>
    get() = feed?.entries.orEmpty()

  /** Vrai quand le serveur a répondu et n'a rien à annoncer. */
  val isEmpty: Boolean
    get() = feed != null && feed.isEmpty

  /**
   * Les perturbations à annoncer en bandeau pour l'arrêt (SPEC.md § 5.4).
   *
   * Elles arrivent attachées à chaque départ, jamais à l'arrêt lui-même : les réunir ici est le
   * seul moyen d'en faire un bandeau d'arrêt. Le tri « en vigueur / à venir » et le dédoublonnage
   * reviennent à `Disruptions`, dans `:core`, comme partout ailleurs dans l'application.
   */
  val alerts: List<Disruption>
    get() = entries.flatMap { it.alerts }

  /** Vrai quand une page supplémentaire peut être demandée dans ce sens. */
  fun canPage(page: DeparturesPage): Boolean = cursor(page) != null

  /** Le curseur d'une page supplémentaire, `null` quand le serveur n'en propose pas. */
  fun cursor(page: DeparturesPage): String? = when (page) {
    DeparturesPage.EARLIER -> feed?.previousPageCursor
    DeparturesPage.LATER -> feed?.nextPageCursor
  }
}
