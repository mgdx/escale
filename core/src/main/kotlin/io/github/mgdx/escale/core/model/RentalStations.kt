package io.github.mgdx.escale.core.model

import kotlin.math.cos

/*
 * Ce que l'écran de détail a besoin de calculer sur une station de libre-service (SPEC.md § 5.3).
 *
 * Tout est ici, en Kotlin pur, parce que tout est faux de la même manière que discrètement : un
 * comptage qui additionne les trottinettes aux vélos ne lève aucune exception, il affiche
 * simplement un chiffre auquel l'usager va se fier pour prendre sa décision. Ces règles se
 * vérifient en JVM (docs/architecture.md § 1).
 *
 * Rien n'est journalisé : une station de libre-service est une position précise (SPEC.md § 8, § 11).
 */

/**
 * Le nombre de véhicules à annoncer pour une portion empruntant un [formFactor] donné.
 *
 * La ventilation par type n'est employée que si **tous** les identifiants cités par la station sont
 * connus : une table partielle donnerait un total tronqué, c'est-à-dire un chiffre plus petit que la
 * réalité annoncé avec la même assurance. Dans le doute, c'est [RentalAvailability.numVehiclesAvailable]
 * qui parle, puisque c'est le seul champ que l'API garantit.
 */
fun RentalAvailability.vehiclesFor(formFactor: RentalFormFactor?): Int {
  if (formFactor == null || vehicleTypesAvailable.isEmpty()) return numVehiclesAvailable
  if (!vehicleTypesAvailable.keys.all { vehicleKinds.containsKey(it) }) return numVehiclesAvailable
  return vehicleTypesAvailable.entries.sumOf { (id, count) ->
    if (vehicleKinds[id]?.formFactor == formFactor) count else 0
  }
}

/**
 * Le nombre de places libres au retour, tous types confondus.
 *
 * `vehicleDocksAvailable` est facultatif côté API et vide sur la plupart des flux : un total de
 * zéro et une absence d'information ne se ressemblent que pour qui ne regarde pas le champ. C'est
 * [hasDockCounts] qui les distingue, et l'affichage tait la mention plutôt que d'annoncer « 0 place
 * libre » à une station qui n'en compte simplement pas.
 */
fun RentalAvailability.docksAvailable(): Int = vehicleDocksAvailable.values.sum()

/** Vrai quand le système publie un compte de places libres, fût-il nul. */
fun RentalAvailability.hasDockCounts(): Boolean = vehicleDocksAvailable.isNotEmpty()

/**
 * La ventilation par type de SPEC.md § 5.3, regroupée par **nature** et non par identifiant.
 *
 * Deux identifiants de même nature sont additionnés — un exploitant qui publie trois références de
 * vélo mécanique n'a pas trois lignes à faire lire. Les natures sans véhicule ni place libre sont
 * omises : une ligne « 0 » n'apprend rien de plus que le total déjà affiché.
 *
 * L'ordre est celui des énumérations du domaine, donc stable d'un appel à l'autre : une liste qui
 * change d'ordre à chaque rafraîchissement se lit comme une liste qui a changé de contenu.
 */
fun RentalAvailability.countsByKind(): List<RentalTypeCount> {
  val ids = vehicleTypesAvailable.keys + vehicleDocksAvailable.keys
  val grouped = LinkedHashMap<RentalVehicleKind?, RentalTypeCount>()
  for (id in ids) {
    val kind = vehicleKinds[id]
    val previous = grouped[kind]
    grouped[kind] = RentalTypeCount(
      kind = kind,
      vehicles = (previous?.vehicles ?: 0) + (vehicleTypesAvailable[id] ?: 0),
      docks = (previous?.docks ?: 0) + (vehicleDocksAvailable[id] ?: 0),
    )
  }
  return grouped.values
    .filter { it.vehicles > 0 || it.docks > 0 }
    .sortedWith(compareBy({ it.kind?.formFactor?.ordinal ?: UNKNOWN_LAST }, { it.kind?.propulsionType?.ordinal ?: 0 }))
}

/**
 * La station que la portion de trajet désigne, parmi celles que le serveur a rendues autour du point.
 *
 * Un rayon même faible rapporte plusieurs stations : trois exploitants différents partagent le
 * parvis de la gare de Berlin. Choisir la plus proche ne suffit donc pas — c'est le **nom porté par
 * la portion** qui identifie l'exploitant, et la distance ne départage que les candidats restants.
 *
 * @param name `fromStationName` ou `toStationName` de la portion. Nul ou vide en free-floating,
 *   auquel cas seule la proximité peut trancher.
 */
fun List<RentalAvailability>.stationFor(point: LatLon, name: String?): RentalAvailability? {
  val wanted = name?.trim()?.takeIf(String::isNotEmpty)
  val named = if (wanted == null) emptyList() else filter { it.name.trim().equals(wanted, ignoreCase = true) }
  return (named.ifEmpty { this }).minByOrNull { it.coordinates.rankingDistanceTo(point) }
}

/** Les natures inconnues passent en fin de liste, après toutes celles que l'on sait nommer. */
private const val UNKNOWN_LAST = Int.MAX_VALUE

/**
 * Une distance **de classement**, pas une distance géodésique.
 *
 * Départager deux stations distantes de quelques dizaines de mètres ne demande pas une formule de
 * haversine : la projection équirectangulaire conserve l'ordre à cette échelle, et son résultat
 * n'est jamais affiché. Le carré évite une racine inutile.
 */
private fun LatLon.rankingDistanceTo(other: LatLon): Double {
  val deltaLat = lat - other.lat
  val deltaLon = (lon - other.lon) * cos(Math.toRadians((lat + other.lat) / 2))
  return deltaLat * deltaLat + deltaLon * deltaLon
}
