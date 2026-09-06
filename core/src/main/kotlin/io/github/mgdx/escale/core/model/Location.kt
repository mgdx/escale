package io.github.mgdx.escale.core.model

/**
 * Point de départ ou d'arrivée saisi par l'usager, tel que le renvoie `/api/v1/geocode`
 * (schéma `Match`).
 */
data class Location(
  /**
   * Identifiant d'arrêt (`stopId`) quand [kind] vaut [PlaceKind.STOP]. Nul pour une adresse ou un
   * lieu : la requête `plan` porte alors les coordonnées.
   */
  val id: String?,
  /** Libellé affiché à l'usager. */
  val name: String,
  /** Complément de localisation : ville, quartier, pays. Nul si le serveur n'en fournit pas. */
  val description: String?,
  val coordinates: LatLon,
  val kind: PlaceKind,
  /**
   * Modes desservis quand il s'agit d'un arrêt. Sert à différencier visuellement les résultats
   * d'autocomplétion (SPEC.md § 5.1). Vide pour une adresse ou un lieu.
   */
  val servedModes: List<TransitMode> = emptyList(),
  /**
   * Ordre de pertinence du serveur, dont l'algorithme peut changer.
   *
   * Il **sert à ordonner, jamais à seuiller** : la valeur n'a de sens que relativement aux autres
   * résultats de la même réponse, et deux versions de MOTIS ne la calculent pas de la même façon.
   * Aucun code ne la compare donc à un seuil, et l'interface ne l'affiche pas ; elle documente
   * l'ordre dans lequel le serveur a rendu la liste, celui que la composition de SPEC.md § 5.1
   * conserve. Zéro par défaut : un lieu qui ne vient pas du géocodage n'a pas de pertinence.
   */
  val score: Double = 0.0,
)
