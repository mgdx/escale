package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode

/**
 * Ce qu'il faut savoir d'un arrêt pour le dessiner, et rien d'autre (SPEC.md § 5.7).
 *
 * Le calcul est ici, en Kotlin pur, pour être vérifiable en JVM : `:app` n'a plus qu'à le
 * sérialiser en GeoJSON et à laisser MapLibre lire les propriétés.
 *
 * @param tier palier à partir duquel le marqueur doit se voir. C'est lui qui porte la règle 5 :
 *   franchir un seuil vers le bas ne déclenche aucune requête, la couche du palier supérieur est
 *   simplement masquée par son `minzoom`.
 * @param mode mode qui donne son **dessin** au marqueur. Une forme, jamais une teinte seule
 *   (SPEC.md § 9) : le nom de l'arrêt reste écrit à côté.
 */
data class StopMarker(val id: String, val name: String, val tier: ZoomTier, val mode: TransitMode)

/** Les marqueurs à poser pour une réponse du serveur, dans l'ordre où elle est arrivée. */
fun stopMarkers(stops: List<Stop>): List<StopMarker> = stops.map { stop ->
  StopMarker(
    id = stop.id,
    name = stop.name,
    tier = stopTier(stop.modes),
    mode = principalMode(stop.modes),
  )
}

/**
 * Le palier à partir duquel un arrêt s'affiche (tableau de SPEC.md § 5.7).
 *
 * Gares et stations de métro dès le zoom 11, tout le reste à partir du zoom 13. La liste des modes
 * ferrés lourds est celle de la spec, mot pour mot, et vit dans `TransitMode.HEAVY_RAIL_MODES`.
 *
 * Un arrêt que le serveur rend à une requête de palier 11 sans porter l'un de ces modes — cela
 * arrive sur un arrêt regroupé dont le serveur ne publie que le mode d'un enfant — n'apparaît qu'au
 * zoom 13. C'est le tableau de la spec qui fait foi, pas la générosité du serveur.
 */
fun stopTier(modes: List<TransitMode>): ZoomTier =
  if (modes.any { it in TransitMode.HEAVY_RAIL_MODES }) ZoomTier.MAJOR_STATIONS else ZoomTier.ALL_STOPS

/**
 * Le mode qui donne son dessin au marqueur, quand l'arrêt en dessert plusieurs.
 *
 * Le plus « lourd » gagne : une gare desservie aussi par des bus reste une gare. L'ordre est celui
 * de la hiérarchie des réseaux, pas celui que le serveur a renvoyé, qui n'est pas garanti.
 */
fun principalMode(modes: List<TransitMode>): TransitMode =
  MODE_PRIORITY.firstOrNull { it in modes } ?: modes.firstOrNull() ?: TransitMode.OTHER

/**
 * SPEC.md § 5.7, règle 6 : « regroupement (`cluster`) au-delà de 200 points visibles ».
 *
 * En deçà, chaque arrêt garde son dessin et son nom : regrouper trois arrêts n'aide personne.
 */
fun shouldClusterStops(count: Int): Boolean = count > MapLoadRules.CLUSTER_THRESHOLD

/** Du réseau le plus structurant au plus fin, pour choisir le dessin d'un arrêt multimodal. */
private val MODE_PRIORITY = listOf(
  TransitMode.HIGHSPEED_RAIL,
  TransitMode.LONG_DISTANCE,
  TransitMode.NIGHT_RAIL,
  TransitMode.RAIL,
  TransitMode.REGIONAL_RAIL,
  TransitMode.SUBURBAN,
  TransitMode.SUBWAY,
  TransitMode.TRAM,
  TransitMode.FERRY,
  TransitMode.AERIAL_LIFT,
  TransitMode.FUNICULAR,
  TransitMode.AIRPLANE,
  TransitMode.COACH,
  TransitMode.BUS,
)
