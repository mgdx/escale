package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox

/** L'état de la caméra à un instant donné : ce que l'écran montre, et à quelle échelle. */
data class MapViewport(val visibleArea: BoundingBox, val zoom: Double)

/** Ce qu'il faut demander au serveur pour peupler la carte : une emprise et un palier de zoom. */
data class MapDataRequest(val area: BoundingBox, val tier: ZoomTier)

/** Les constantes chiffrées des règles de fluidité de SPEC.md § 5.7, à un seul endroit. */
object MapLoadRules {

  /** Règle 1 : le chargement est déclenché à l'arrêt de la caméra, après 300 ms d'anti-rebond. */
  const val CAMERA_IDLE_DEBOUNCE_MILLIS = 300L

  /** Règle 3 : l'emprise demandée est celle de l'écran élargie de 30 %. */
  const val AREA_EXPANSION_RATIO = 0.3

  /** Règle 6 : au-delà de 200 points visibles, la source GeoJSON regroupe les marqueurs. */
  const val CLUSTER_THRESHOLD = 200

  /** Règle 9 : aucune animation de caméra ne dépasse 500 ms. */
  const val MAX_CAMERA_ANIMATION_MILLIS = 500
}

/**
 * Décide s'il faut redemander des données au serveur après un arrêt de caméra (SPEC.md § 5.7).
 *
 * Trois des neuf règles de fluidité tiennent dans cette fonction, et c'est pour cela qu'elle vit
 * dans `:core` où elle se teste en JVM :
 *
 * - **règle 3** : l'emprise demandée est celle de l'écran élargie de 30 %, si bien qu'un petit
 *   déplacement retombe dans une zone déjà couverte et n'émet rien ;
 * - **règle 5** : franchir un seuil de zoom **vers le bas** ne déclenche aucune requête tant que
 *   l'emprise déjà chargée couvre l'écran — les modes d'un palier bas sont un sous-ensemble de
 *   ceux du palier haut, il suffit de masquer des couches ;
 * - **SPEC.md § 7.9** : sous le zoom 11, aucune requête n'est émise, jamais.
 *
 * @param loaded la dernière requête effectivement chargée, ou `null` si rien ne l'a encore été.
 * @param visibleArea l'emprise de l'écran, telle que la rend la projection de la carte.
 * @param zoom le niveau de zoom courant.
 * @return la requête à émettre, ou `null` s'il n'y a rien à demander.
 */
fun planMapLoad(loaded: MapDataRequest?, visibleArea: BoundingBox, zoom: Double): MapDataRequest? {
  val tier = ZoomTier.forZoom(zoom)
  if (!tier.requestsStops) return null
  // Le palier déjà chargé est au moins aussi riche, et son emprise couvre l'écran : rien à faire.
  if (loaded != null && loaded.tier >= tier && loaded.area.covers(visibleArea)) return null
  return MapDataRequest(area = visibleArea.expandBy(MapLoadRules.AREA_EXPANSION_RATIO), tier = tier)
}

/**
 * Vrai si cette emprise contient entièrement [other].
 *
 * L'antiméridien n'est pas traité, au même titre que dans [boundingBoxOf] : aucun jeu de données
 * MOTIS ne s'y prête en v1.
 */
private fun BoundingBox.covers(other: BoundingBox): Boolean = contains(other.min) && contains(other.max)
