package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `GET /api/v1/rentals` (SPEC.md § 5.3 et § 5.7).
 *
 * Ces DTO ne sortent jamais de `:data` (docs/architecture.md § 1 et § 2). Comme partout dans le
 * projet, les types énumérés sont lus en `String` : `/api/v1/rentals` est marqué expérimental côté
 * MOTIS, et une valeur ajoutée à `RentalFormFactor` ne doit pas faire échouer la lecture de la
 * réponse entière.
 *
 * Le tableau `zones` du schéma n'est volontairement pas déclaré : Escale ne dessine aucune zone de
 * géorepérage en v1, la requête les exclut déjà (`withZones=false`), et `ignoreUnknownKeys` laisse
 * passer celles qu'un serveur renverrait quand même.
 */
@Serializable
internal data class RentalsResponseDto(
  val providers: List<RentalProviderDto> = emptyList(),
  val stations: List<RentalStationDto> = emptyList(),
  val vehicles: List<RentalVehicleDto> = emptyList(),
)

/**
 * Décalque du schéma `RentalProvider`, réduit à ce qui s'affiche.
 *
 * Seul [vehicleTypes] est réellement indispensable : c'est la seule table qui traduise les
 * identifiants opaques de `vehicleTypesAvailable` en type de véhicule et en motorisation.
 */
@Serializable
internal data class RentalProviderDto(
  val id: String = "",
  val name: String = "",
  val vehicleTypes: List<RentalVehicleTypeDto> = emptyList(),
)

/** Décalque du schéma `RentalVehicleType` : un type de véhicule d'un exploitant. */
@Serializable
internal data class RentalVehicleTypeDto(
  /** Unique **au sein de l'exploitant**, et parfois vide : plusieurs flux GBFS n'en publient pas. */
  val id: String = "",
  val formFactor: String? = null,
  val propulsionType: String? = null,
)

/** Décalque du schéma `RentalStation` : une station et son état à l'instant de la réponse. */
@Serializable
internal data class RentalStationDto(
  val id: String = "",
  val providerId: String = "",
  val name: String = "",
  val lat: Double = 0.0,
  val lon: Double = 0.0,
  val isRenting: Boolean = true,
  val isReturning: Boolean = true,
  val numVehiclesAvailable: Int = 0,
  val formFactors: List<String> = emptyList(),
  /** Identifiant de type de véhicule → nombre disponible à la prise. */
  val vehicleTypesAvailable: Map<String, Int> = emptyMap(),
  /** Identifiant de type de véhicule → places libres au retour. Souvent vide, voir `RentalMapper`. */
  val vehicleDocksAvailable: Map<String, Int> = emptyMap(),
  /** Lien profond vers l'application de l'exploitant. Souvent la chaîne vide en pratique. */
  val rentalUriAndroid: String? = null,
)

/** Décalque du schéma `RentalVehicle` : un véhicule isolé, hors station (free-floating). */
@Serializable
internal data class RentalVehicleDto(
  val id: String = "",
  val providerId: String = "",
  /** Type du véhicule, à rapprocher de `RentalProvider.vehicleTypes`. */
  val typeId: String = "",
  val lat: Double = 0.0,
  val lon: Double = 0.0,
  val formFactor: String? = null,
  val propulsionType: String? = null,
  /** Vrai quand un client l'a déjà réservé : il est visible mais indisponible. */
  val isReserved: Boolean = false,
  /** Vrai quand le véhicule est hors service. */
  val isDisabled: Boolean = false,
  val rentalUriAndroid: String? = null,
)
