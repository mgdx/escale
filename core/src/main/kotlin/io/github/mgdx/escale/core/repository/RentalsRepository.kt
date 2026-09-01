package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.result.Outcome

/** Stations et véhicules en libre-service (`/api/v1/rentals`). */
interface RentalsRepository {
  /**
   * Stations et véhicules libres contenus dans [area], pour la carte.
   * Cache d'une minute seulement : la donnée est volatile (SPEC.md § 5.7).
   */
  suspend fun stationsIn(area: BoundingBox): Outcome<List<RentalAvailability>>

  /**
   * Disponibilité autour d'un point, pour la portion en libre-service d'un trajet (SPEC.md § 5.3).
   *
   * @param radiusMeters rayon de recherche, volontairement faible : on vise une station précise.
   */
  suspend fun availabilityNear(
    point: LatLon,
    radiusMeters: Int = DEFAULT_RADIUS_METERS,
  ): Outcome<List<RentalAvailability>>

  companion object {
    const val DEFAULT_RADIUS_METERS = 50
  }
}
