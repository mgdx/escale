package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Disponibilité d'une station de libre-service, décalque du schéma `RentalStation`
 * de `/api/v1/rentals` (SPEC.md § 5.3).
 *
 * Cette donnée est volatile : elle s'affiche toujours accompagnée de son heure de relevé
 * [retrievedAt] et d'un bouton de rafraîchissement, et son cache expire en 60 secondes
 * (SPEC.md § 5.7).
 */
data class RentalAvailability(
  val stationId: String,
  val name: String,
  val coordinates: LatLon,
  /** Nombre total de véhicules disponibles à la prise. */
  val numVehiclesAvailable: Int,
  /** Nombre de véhicules disponibles par type de véhicule du système (identifiant GBFS -> compte). */
  val vehicleTypesAvailable: Map<String, Int> = emptyMap(),
  /** Nombre de places libres au retour, par type de véhicule. Vide en free-floating. */
  val vehicleDocksAvailable: Map<String, Int> = emptyMap(),
  /** Faux quand la station est temporairement hors service à la prise. */
  val isRenting: Boolean = true,
  /** Faux quand la station est temporairement hors service au retour. */
  val isReturning: Boolean = true,
  /** Types de véhicules que la station accepte, à la prise comme au retour. */
  val formFactors: List<RentalFormFactor> = emptyList(),
  /** Lien profond vers l'application de l'exploitant. Ouvert en intent externe, jamais en WebView. */
  val rentalUriAndroid: String? = null,
  /** Heure à laquelle l'application a obtenu ce relevé, à afficher telle quelle. */
  val retrievedAt: Instant,
)
