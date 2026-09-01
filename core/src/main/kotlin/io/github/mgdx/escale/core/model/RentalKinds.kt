package io.github.mgdx.escale.core.model

/** Type de véhicule en libre-service, décalque du schéma `RentalFormFactor` de l'OpenAPI MOTIS. */
enum class RentalFormFactor {
  BICYCLE,
  CARGO_BICYCLE,
  CAR,
  MOPED,
  SCOOTER_STANDING,
  SCOOTER_SEATED,
  OTHER,
}

/** Motorisation, décalque du schéma `RentalPropulsionType` de l'OpenAPI MOTIS. */
enum class RentalPropulsionType {
  HUMAN,
  ELECTRIC_ASSIST,
  ELECTRIC,
  COMBUSTION,
  COMBUSTION_DIESEL,
  HYBRID,
  PLUG_IN_HYBRID,
  HYDROGEN_FUEL_CELL,
}

/** Contrainte de retour du véhicule, décalque du schéma `RentalReturnConstraint`. */
enum class RentalReturnConstraint {
  /** Aucune contrainte : le véhicule peut être laissé en dehors d'une station. */
  NONE,

  /** Le retour doit se faire dans une station du système, quelle qu'elle soit. */
  ANY_STATION,

  /** Le retour doit se faire dans la station de départ. */
  ROUNDTRIP_STATION,
}
