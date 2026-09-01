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
)
