package io.github.mgdx.escale.core.model

/** Nature d'un lieu, décalque du schéma `LocationType` de l'OpenAPI MOTIS. */
enum class PlaceKind {
  /** Une adresse postale, éventuellement avec numéro. */
  ADDRESS,

  /** Un lieu remarquable : commerce, équipement, monument. */
  PLACE,

  /** Un arrêt ou une gare de la base horaire. */
  STOP,
}
