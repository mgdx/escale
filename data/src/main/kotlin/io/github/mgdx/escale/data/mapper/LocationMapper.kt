package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.data.dto.GeocodeAreaDto
import io.github.mgdx.escale.data.dto.GeocodeMatchDto

/**
 * Séparateur des divisions administratives dans le complément de localisation.
 *
 * Ce n'est pas une chaîne d'interface : aucune traduction ne l'accompagne, c'est la ponctuation
 * qu'emploient les deux langues du projet.
 */
private const val AREA_SEPARATOR = ", "

/** `Match` -> [Location]. */
internal fun GeocodeMatchDto.toDomain(): Location = Location(
  // Seul un arrêt porte un identifiant exploitable par les autres points d'entrée : pour une
  // adresse ou un lieu, la requête `plan` part des coordonnées (docs/architecture.md § 4).
  id = id.takeIf { kind() == PlaceKind.STOP && it.isNotBlank() },
  name = name,
  description = describeAreas(areas),
  coordinates = LatLon(lat = lat, lon = lon),
  kind = kind(),
  // SPEC.md § 5.1 : l'interface annonce les modes desservis par un arrêt. Un doublon renvoyé par
  // le serveur, ou deux valeurs inconnues repliées sur OTHER, ne doivent pas s'afficher deux fois.
  servedModes = modes.map(::toTransitMode).distinct(),
  // Transporté tel quel : c'est la composition de SPEC.md § 5.1 qui s'appuie sur l'ordre qu'il
  // documente, et personne ne le compare à un seuil.
  score = score,
)

/** `Match[]` -> liste de [Location], dans l'ordre de pertinence rendu par le serveur. */
internal fun List<GeocodeMatchDto>.toDomain(): List<Location> = map { it.toDomain() }

/**
 * `LocationType` -> [PlaceKind].
 *
 * Une valeur inconnue devient [PlaceKind.PLACE] : c'est la catégorie la plus neutre, et elle vaut
 * mieux qu'un résultat perdu (SPEC.md § 5.1).
 */
private fun GeocodeMatchDto.kind(): PlaceKind = when (type) {
  "ADDRESS" -> PlaceKind.ADDRESS
  "STOP" -> PlaceKind.STOP
  else -> PlaceKind.PLACE
}

/**
 * `Mode` -> [TransitMode].
 *
 * Les valeurs dépréciées de l'API sont ramenées sur leur remplaçante, comme l'annonce la
 * documentation de [TransitMode] ; tout le reste, y compris une valeur que l'API ajouterait demain,
 * devient [TransitMode.OTHER].
 */
private fun toTransitMode(raw: String): TransitMode = when (raw) {
  "REGIONAL_FAST_RAIL" -> TransitMode.REGIONAL_RAIL
  "METRO" -> TransitMode.SUBWAY
  "AREAL_LIFT", "CABLE_CAR" -> TransitMode.AERIAL_LIFT
  else -> TRANSIT_MODES_BY_NAME[raw] ?: TransitMode.OTHER
}

private val TRANSIT_MODES_BY_NAME: Map<String, TransitMode> = TransitMode.entries.associateBy { it.name }

/**
 * Complément de localisation, du plus précis au plus large.
 *
 * L'API marque `default` la division à afficher (la commune, niveau administratif le plus proche
 * de 7) et `unique` celle qui distingue deux résultats homonymes — c'est ce qui sépare la « Rue de
 * Rivoli » du 4e arrondissement de celle du 1er. Ces deux-là suffisent : reprendre la liste
 * entière donnerait « France, France métropolitaine, Île-de-France, Paris, Paris… ».
 */
private fun describeAreas(areas: List<GeocodeAreaDto>): String? {
  val displayed = areas.filter { it.default || it.unique }
    .sortedByDescending { it.adminLevel }
    .map { it.name }
    .distinct()
  return displayed.ifEmpty {
    // Aucun drapeau : le serveur n'a rien distingué, la division la plus fine reste le meilleur
    // repère possible.
    listOfNotNull(areas.maxByOrNull { it.adminLevel }?.name)
  }.joinToString(AREA_SEPARATOR).ifBlank { null }
}
