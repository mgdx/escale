package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome

/**
 * **Bouchon temporaire, à supprimer à la fusion du jalon 8.**
 *
 * `RentalsRepositoryImpl` est écrit en parallèle dans `:data` par le lot « libre-service ». Ce lot-ci
 * consomme l'interface `RentalsRepository` de `:core` et ne l'implémente pas : ce bouchon n'existe
 * que pour que la branche compile et que ses tests s'exécutent avant la fusion.
 *
 * À la fusion, il y a exactement deux gestes à faire :
 *
 * 1. remplacer `AppContainer.rentalsRepository` par la vraie implémentation de `:data` ;
 * 2. supprimer ce fichier.
 *
 * Il rend un **échec** et non une liste vide, et c'est délibéré : une liste vide voudrait dire « pas
 * de libre-service ici », ce qui est faux et se verrait comme une carte silencieusement incomplète.
 * Un échec laisse les marqueurs précédents en place (SPEC.md § 8) et ne ment sur rien.
 */
class PendingRentalsRepository : RentalsRepository {

  override suspend fun stationsIn(area: BoundingBox): Outcome<List<RentalAvailability>> = NOT_WIRED_YET

  override suspend fun availabilityNear(point: LatLon, radiusMeters: Int): Outcome<List<RentalAvailability>> =
    NOT_WIRED_YET

  private companion object {
    val NOT_WIRED_YET = Outcome.Failure(EscaleError.ServerUnreachable(statusCode = null))
  }
}
