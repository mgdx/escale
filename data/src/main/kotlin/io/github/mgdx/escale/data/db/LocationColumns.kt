package io.github.mgdx.escale.data.db

/**
 * Un lieu du domaine, aplati en colonnes réutilisables par plusieurs tables.
 *
 * **Les coordonnées sont stockées à côté de l'identifiant, jamais à sa place.** SPEC.md § 5.6.1
 * l'impose : un `stopId` enregistré peut ne plus être reconnu après un changement de serveur, et
 * le favori doit alors « rester affiché avec ses coordonnées et un signalement discret ». Un
 * favori qui ne porterait que son identifiant deviendrait un point sans position, donc inaffichable
 * — et la seule issue serait la suppression automatique, que la spec interdit.
 *
 * C'est un type de `:data` : il ne remonte jamais dans l'interface (docs/architecture.md § 1).
 */
internal data class LocationColumns(
  /** `stopId` quand le lieu est un arrêt, nul pour une adresse ou un lieu remarquable. */
  val stopId: String?,
  val name: String,
  val description: String?,
  val lat: Double,
  val lon: Double,
  /** Nom de la valeur de `PlaceKind`. */
  val kind: String,
  /** Noms des valeurs de `TransitMode`, séparés par des virgules. Vide quand il n'y en a aucune. */
  val servedModes: String,
)
