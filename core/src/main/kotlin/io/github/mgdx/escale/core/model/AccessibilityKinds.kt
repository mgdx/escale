package io.github.mgdx.escale.core.model

/** Accessibilité en fauteuil d'une portion, décalque du schéma `WheelchairAccessibility`. */
enum class WheelchairAccess {
  ACCESSIBLE,
  NOT_ACCESSIBLE,
}

/** Profil piéton, décalque du schéma `PedestrianProfile` de l'OpenAPI MOTIS. */
enum class PedestrianProfile {
  FOOT,

  /** Itinéraire piéton praticable en fauteuil roulant (SPEC.md § 9). */
  WHEELCHAIR,
}

/**
 * Pénalité appliquée au dénivelé, décalque du schéma `ElevationCosts`.
 *
 * Plus la pénalité est forte, plus le serveur accepte d'allonger le trajet pour éviter une côte.
 */
enum class ElevationCosts {
  NONE,
  LOW,
  HIGH,
}
