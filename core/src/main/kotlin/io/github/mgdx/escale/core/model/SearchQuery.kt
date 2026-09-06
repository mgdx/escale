package io.github.mgdx.escale.core.model

/** Une recherche d'itinéraire : ce que l'usager a saisi, plus l'onglet consulté. */
data class SearchQuery(
  val from: Location,
  val to: Location,
  val time: TimeChoice,
  /** Onglet consulté : une requête `plan` par catégorie, jamais quatre d'un coup (SPEC.md § 5.2). */
  val category: JourneyCategory,
  val preferences: SearchPreferences = SearchPreferences(),
  /**
   * La langue de l'interface, dans laquelle le serveur libelle arrêts et destinations.
   *
   * Elle est ici, et non dans [SearchPreferences] : ce n'est pas un réglage de recherche mais une
   * propriété de la requête elle-même. Le cache de `PlanRepository` étant indexé par la
   * [SearchQuery] entière, un changement de langue de l'application donne bien une clé différente
   * et ne ressert pas des libellés dans l'ancienne langue.
   */
  val language: String? = null,
)
