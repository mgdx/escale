package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.LatLon
import kotlin.math.cos

/**
 * Les arrêts de la carte, rendus atteignables autrement que par le doigt (SPEC.md § 9).
 *
 * MapLibre dessine ses marqueurs dans une **vue unique** : un lecteur d'écran n'y voit qu'un grand
 * rectangle muet, et aucun `contentDescription` ne peut y changer quoi que ce soit sans renoncer
 * aux couches GeoJSON qu'impose la règle 6 du § 5.7. La réponse retenue est une liste ordinaire,
 * ouverte depuis un bouton, qui reprend exactement ce que la carte montre — et cette fonction est
 * ce « exactement ».
 *
 * Trois règles, toutes vérifiables en JVM :
 *
 * 1. **Le palier fait foi.** Un arrêt n'est listé que si sa couche est allumée à ce zoom, comme le
 *    décide le `minzoom` de la couche MapLibre correspondante. Lister au zoom 11 des arrêts de bus
 *    que l'écran ne montre pas ferait mentir la liste.
 * 2. **L'emprise visible fait foi**, et non l'emprise demandée : les requêtes portent sur l'écran
 *    élargi de 30 % (§ 5.7, règle 3), et ce tiers hors champ n'est pas « à l'écran ».
 * 3. **Le plus proche du centre d'abord.** Qui ne voit pas la carte ne peut pas viser : l'ordre
 *    utile est celui de la proximité au point que la caméra regarde, pas celui de la réponse du
 *    serveur, qui n'est garanti par rien.
 *
 * La liste est plafonnée à [limit]. Deux cents arrêts énoncés un à un ne sont pas un accès, et le
 * message qui accompagne la liste invite alors à se rapprocher.
 */
fun browsableStops(
  markers: List<StopMarker>,
  visibleArea: BoundingBox,
  zoom: Double,
  limit: Int = MAX_BROWSABLE_STOPS,
): List<StopMarker> {
  val center = visibleArea.center
  return markers
    .asSequence()
    .filter { it.tier.minZoom <= zoom }
    .filter { visibleArea.contains(it.point) }
    .sortedBy { it.point.rankingDistanceTo(center) }
    .take(limit)
    .toList()
}

/**
 * Au-delà, une liste lue à voix haute cesse d'être une aide.
 *
 * Le seuil est plus bas que celui du regroupement des marqueurs (200 points, § 5.7 règle 6), et
 * délibérément : regrouper des points sur une carte reste lisible d'un coup d'œil, énoncer deux
 * cents noms à la synthèse vocale ne l'est pas.
 */
const val MAX_BROWSABLE_STOPS = 40

/**
 * Une distance **de classement**, pas une distance géodésique.
 *
 * Ordonner des arrêts d'un même écran ne demande pas de haversine : la projection
 * équirectangulaire conserve l'ordre à cette échelle, et le résultat n'est jamais affiché. Le carré
 * évite une racine inutile.
 */
private fun LatLon.rankingDistanceTo(other: LatLon): Double {
  val deltaLat = lat - other.lat
  val deltaLon = (lon - other.lon) * cos(Math.toRadians((lat + other.lat) / 2))
  return deltaLat * deltaLat + deltaLon * deltaLon
}
