package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalPointKind
import io.github.mgdx.escale.core.model.RentalVehicleKind
import io.github.mgdx.escale.data.dto.RentalProviderDto
import io.github.mgdx.escale.data.dto.RentalStationDto
import io.github.mgdx.escale.data.dto.RentalVehicleDto
import io.github.mgdx.escale.data.dto.RentalsResponseDto
import java.time.Instant

/*
 * `/api/v1/rentals` → domaine (SPEC.md § 5.3 et § 5.7).
 *
 * Deux choses valent d'être sues avant de relire ce fichier, l'une et l'autre constatées sur
 * `api.transitous.org` :
 *
 * - **la nature du point vient du schéma qu'on est en train de lire, et de nulle part ailleurs.**
 *   Ce fichier est le seul du projet à savoir s'il traduit un `RentalStation` ou un
 *   `RentalVehicle` ; s'il ne le consigne pas dans [RentalPointKind], l'information est perdue pour
 *   tout le monde et la carte n'a plus que des devinettes à sa disposition ;
 * - **les identifiants de types de véhicules sont opaques, et parfois vides.** `196`, `dott_scooter`,
 *   `NES:VehicleType:mg5reichweite350km`, ou `""` chez Vélib'. Seule la table `vehicleTypes` de
 *   l'exploitant leur donne un sens ; c'est elle que ce mapping résout, faute de quoi la ventilation
 *   « par type » de SPEC.md § 5.3 ne serait qu'une suite de codes internes ;
 * - **`vehicleDocksAvailable` est vide sur tous les flux observés**, y compris Vélib' et Villo, qui
 *   ont pourtant des bornes. Le champ est mappé fidèlement, et c'est l'affichage qui tait la mention
 *   plutôt que d'annoncer « 0 place libre » là où l'exploitant n'a rien publié.
 *
 * Rien n'est journalisé : chaque objet d'ici porte une position précise (SPEC.md § 8 et § 11).
 */

/**
 * Les stations **et** les véhicules isolés de la réponse, dans cet ordre.
 *
 * Les deux deviennent des [RentalAvailability] parce que l'interface `RentalsRepository` n'a qu'un
 * type de sortie, et parce qu'ils répondent à la même question : « qu'est-ce qui est disponible
 * ici, maintenant ». Un véhicule isolé est simplement une station d'un véhicule, sans nom et sans
 * borne — voir [toAvailability] pour ce que cela implique.
 *
 * @param retrievedAt heure du relevé, celle que l'écran affiche à côté du compte (SPEC.md § 5.3).
 */
internal fun RentalsResponseDto.toAvailabilities(retrievedAt: Instant): List<RentalAvailability> {
  val kinds = providers.associate { it.id to it.vehicleKinds() }
  return stations.map { it.toAvailability(kinds[it.providerId].orEmpty(), retrievedAt) } +
    vehicles.map { it.toAvailability(retrievedAt) }
}

/**
 * La table « identifiant de type → nature », pour un exploitant.
 *
 * Un même identifiant peut apparaître plusieurs fois dans `vehicleTypes` : MOTIS y décline chaque
 * type par contrainte de retour, si bien que nextbike Berlin publie treize entrées pour six types.
 * La **première** l'emporte, et les suivantes sont ignorées : elles ne diffèrent que par une
 * contrainte de retour dont la portion de trajet, elle, porte déjà la valeur qui la concerne.
 */
private fun RentalProviderDto.vehicleKinds(): Map<String, RentalVehicleKind> {
  val kinds = LinkedHashMap<String, RentalVehicleKind>()
  for (type in vehicleTypes) {
    kinds.getOrPut(type.id) {
      RentalVehicleKind(
        formFactor = rentalFormFactorOf(type.formFactor),
        propulsionType = rentalPropulsionTypeOf(type.propulsionType),
      )
    }
  }
  return kinds
}

/**
 * Une station.
 *
 * [kinds] est restreint aux identifiants que la station cite réellement : le domaine n'a que faire
 * des treize types de l'exploitant quand la station n'en propose que trois.
 */
private fun RentalStationDto.toAvailability(
  kinds: Map<String, RentalVehicleKind>,
  retrievedAt: Instant,
): RentalAvailability {
  val cited = vehicleTypesAvailable.keys + vehicleDocksAvailable.keys
  return RentalAvailability(
    stationId = id,
    name = name,
    coordinates = LatLon(lat = lat, lon = lon),
    numVehiclesAvailable = numVehiclesAvailable,
    vehicleTypesAvailable = vehicleTypesAvailable,
    vehicleDocksAvailable = vehicleDocksAvailable,
    isRenting = isRenting,
    isReturning = isReturning,
    formFactors = formFactors.mapNotNull(::rentalFormFactorOf),
    rentalUriAndroid = rentalUriAndroid.orNullIfBlank(),
    retrievedAt = retrievedAt,
    vehicleKinds = kinds.filterKeys { it in cited },
    kind = RentalPointKind.STATION,
  )
}

/**
 * Un véhicule isolé, vu comme la disponibilité d'un point.
 *
 * Trois choix, tous discutables et tous documentés ici plutôt qu'ailleurs :
 *
 * - **un véhicule vaut un**, sauf s'il est réservé ou hors service : dans ce cas il reste sur la
 *   carte, mais il n'est pas disponible, et l'annoncer disponible enverrait quelqu'un pour rien ;
 * - **il n'a pas de nom.** Le flux n'en donne aucun, et fabriquer « Vélo n° 7c3fee74 » afficherait
 *   un identifiant technique à l'usager ;
 * - **`isReturning` garde sa valeur neutre.** Un véhicule libre n'est pas un point de retour ; le
 *   mettre à faux se lirait « n'accepte plus les retours », ce qui n'a pas de sens ici.
 */
private fun RentalVehicleDto.toAvailability(retrievedAt: Instant): RentalAvailability {
  val available = !isReserved && !isDisabled
  val vehicleKind = RentalVehicleKind(
    formFactor = rentalFormFactorOf(formFactor),
    propulsionType = rentalPropulsionTypeOf(propulsionType),
  )
  return RentalAvailability(
    stationId = id,
    name = "",
    coordinates = LatLon(lat = lat, lon = lon),
    numVehiclesAvailable = if (available) 1 else 0,
    vehicleTypesAvailable = mapOf(typeId to if (available) 1 else 0),
    isRenting = available,
    formFactors = listOfNotNull(vehicleKind.formFactor),
    rentalUriAndroid = rentalUriAndroid.orNullIfBlank(),
    retrievedAt = retrievedAt,
    vehicleKinds = mapOf(typeId to vehicleKind),
    kind = RentalPointKind.FREE_FLOATING,
  )
}

/**
 * Les flux GBFS écrivent une chaîne vide là où ils n'ont rien à dire : sur les vingt-quatre stations
 * relevées place de la Bastille, `rentalUriAndroid` valait `""` partout. Sans cette conversion,
 * l'écran afficherait un bouton « Ouvrir l'application » qui n'ouvre rien.
 */
private fun String?.orNullIfBlank(): String? = this?.takeIf(String::isNotBlank)
