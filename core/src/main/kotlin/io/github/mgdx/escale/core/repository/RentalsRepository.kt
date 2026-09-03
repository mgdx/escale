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
   * @param fresh `true` pour ignorer le cache mémoire et redemander la disponibilité au serveur.
   *   C'est le seul moyen d'honorer le bouton de rafraîchissement du § 5.3 : sans lui, la réponse
   *   mise en cache serait rendue telle quelle et le geste de l'usager n'aurait aucun effet
   *   pendant une minute, sans que rien ne le lui dise. Le cache d'une minute existe pour empêcher
   *   les requêtes **automatiques**, pas pour bloquer une action délibérée (SPEC.md § 7.4).
   *   Réservé au rafraîchissement demandé par l'usager, jamais à un chargement ordinaire.
   */
  suspend fun availabilityNear(
    point: LatLon,
    radiusMeters: Int = DEFAULT_RADIUS_METERS,
    fresh: Boolean = false,
  ): Outcome<List<RentalAvailability>>

  companion object {
    const val DEFAULT_RADIUS_METERS = 50
  }
}
