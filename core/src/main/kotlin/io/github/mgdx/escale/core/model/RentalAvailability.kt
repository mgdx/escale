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
  /**
   * Nature de chaque identifiant de type cité par [vehicleTypesAvailable] et
   * [vehicleDocksAvailable], quand le système la publie.
   *
   * Les identifiants de types sont opaques et propres à chaque exploitant : sans cette table, la
   * ventilation « par type » de SPEC.md § 5.3 ne serait qu'une suite de codes internes. Vide quand
   * le système ne décrit pas ses types — l'affichage se rabat alors sur [numVehiclesAvailable].
   */
  val vehicleKinds: Map<String, RentalVehicleKind> = emptyMap(),
  /**
   * Station ou véhicule laissé libre, **d'après le schéma d'API dont ce point provient**.
   *
   * Voir [RentalPointKind] : cette valeur ne se devine pas, elle se transporte. La valeur par
   * défaut n'existe que pour la compatibilité de source des appelants qui construisent un
   * [RentalAvailability] à la main ; le mapping de `:data`, lui, la renseigne toujours.
   */
  val kind: RentalPointKind = RentalPointKind.STATION,
)
