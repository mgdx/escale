package io.github.mgdx.escale.core.model

import java.time.Duration

/**
 * Préférences de recherche persistées, exposées par l'écran de réglages (SPEC.md § 5.6).
 *
 * Chaque champ se traduit directement en paramètre de `/api/v6/plan` ; les valeurs par défaut sont
 * celles du serveur, de sorte qu'une préférence laissée intacte n'alourdit pas la requête.
 */
data class SearchPreferences(
  /** `pedestrianSpeed`, en mètres par seconde. Défaut MOTIS : 1,2 m/s, soit environ 4,3 km/h. */
  val pedestrianSpeedMetersPerSecond: Double = DEFAULT_PEDESTRIAN_SPEED,
  /** `pedestrianProfile`. [PedestrianProfile.WHEELCHAIR] change l'itinéraire, pas seulement l'affichage. */
  val pedestrianProfile: PedestrianProfile = PedestrianProfile.FOOT,
  /** `cyclingSpeed`, en mètres par seconde. Défaut MOTIS : 5 m/s, soit 18 km/h. */
  val cyclingSpeedMetersPerSecond: Double = DEFAULT_CYCLING_SPEED,
  /** `elevationCosts`. */
  val elevationCosts: ElevationCosts = ElevationCosts.NONE,
  /** `additionalTransferTime` : marge ajoutée à chaque correspondance. */
  val additionalTransferTime: Duration = Duration.ZERO,
  /** `maxTransfers`. Nul : le serveur applique sa propre limite. */
  val maxTransfers: Int? = null,
  /** `requireBikeTransport` : n'accepter que les courses acceptant les vélos. */
  val requireBikeTransport: Boolean = false,
  /**
   * Types de véhicules partagés acceptés. Alimente à la fois `directRentalFormFactors`,
   * `preTransitRentalFormFactors` et `postTransitRentalFormFactors` : l'usager qui refuse les
   * trottinettes les refuse partout, depuis un seul réglage (SPEC.md § 5.2).
   * Vide signifie « aucun filtre », et non « aucun véhicule ».
   */
  val allowedRentalFormFactors: Set<RentalFormFactor> = emptySet(),
) {
  companion object {
    const val DEFAULT_PEDESTRIAN_SPEED = 1.2
    const val DEFAULT_CYCLING_SPEED = 5.0
  }
}
