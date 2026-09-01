package io.github.mgdx.escale.core.model

/** Une recherche d'itinéraire : ce que l'usager a saisi, plus l'onglet consulté. */
data class SearchQuery(
  val from: Location,
  val to: Location,
  val time: TimeChoice,
  /** Onglet consulté : une requête `plan` par catégorie, jamais quatre d'un coup (SPEC.md § 5.2). */
  val category: JourneyCategory,
  val preferences: SearchPreferences = SearchPreferences(),
)
