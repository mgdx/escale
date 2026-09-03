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

/**
 * Ce qu'un point de libre-service **est** : une station, ou un véhicule laissé libre.
 *
 * `/api/v1/rentals` rend deux schémas distincts et non interchangeables, `RentalStation` et
 * `RentalVehicle`. Le domaine les réunit dans un seul [RentalAvailability] parce qu'ils répondent à
 * la même question — « qu'est-ce qui est disponible ici, maintenant » — mais leur nature, elle, ne
 * se dilue pas : elle décide du palier de zoom auquel le point apparaît, de la forme de son
 * marqueur et de son libellé.
 *
 * **Cette valeur est fixée par le schéma d'API d'origine, et par lui seul.** Personne ne doit la
 * redériver du nom, des bornes, du nombre de véhicules ou de quoi que ce soit d'autre : ces
 * signaux mentent. Une station peut n'avoir aucun nom et aucune borne publiée — la fixture
 * `rentals_brussels_closed_station.json` en contient une, hors service ce jour-là — et une
 * heuristique la prendrait pour un véhicule en libre accès. L'information existe dans la réponse ;
 * il suffit de ne pas la perdre en la lisant.
 */
enum class RentalPointKind {
  /** Décrit par le schéma `RentalStation` : un emplacement fixe, nommé par l'exploitant. */
  STATION,

  /** Décrit par le schéma `RentalVehicle` : un véhicule posé quelque part, sans station. */
  FREE_FLOATING,
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

/**
 * La nature d'un type de véhicule d'un système, décalque des seuls champs utiles du schéma
 * `RentalVehicleType` de `/api/v1/rentals`.
 *
 * Elle existe parce que `RentalAvailability.vehicleTypesAvailable` est indexé par **identifiant de
 * type**, et que ces identifiants sont opaques : `196`, `dott_scooter`, `NES:VehicleType:...`, voire
 * la chaîne vide. Affichés tels quels, ils ne disent rien ; traduits en type de véhicule et en
 * motorisation, ils donnent la ventilation que SPEC.md § 5.3 réclame — « 9 vélos, 1 vélo à
 * assistance électrique » plutôt que « 431 : 9, 446 : 1 ».
 */
data class RentalVehicleKind(val formFactor: RentalFormFactor?, val propulsionType: RentalPropulsionType?)

/**
 * Ce qu'une station propose pour un type de véhicule donné : le résultat de la ventilation que
 * `RentalStations.kt` calcule pour l'affichage de SPEC.md § 5.3.
 *
 * [kind] est nul quand le système ne décrit pas ses types : le compte reste juste, seule
 * l'étiquette manque.
 */
data class RentalTypeCount(
  val kind: RentalVehicleKind?,
  /** Véhicules disponibles à la prise. */
  val vehicles: Int,
  /** Places libres au retour. */
  val docks: Int,
)
