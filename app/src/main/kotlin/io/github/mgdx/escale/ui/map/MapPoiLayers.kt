package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.PoiCategory

/**
 * Les douze couches de points d'intérêt des feuilles de `res/raw` (SPEC.md § 5.6 et § 5.7).
 *
 * Elles sont **déjà dans la feuille**, avec leur filtre, leur pictogramme et leur `minzoom` : le
 * réglage ne fait que les allumer ou les éteindre. Aucune couche n'est ajoutée ni retirée à chaud,
 * c'est la règle 8 du § 5.7 et elle n'a pas d'exception.
 *
 * Les identifiants sont écrits ici **une seule fois** et repris partout ailleurs — visibilité,
 * appui, tests des feuilles — pour qu'un renommage dans le JSON ne puisse pas passer inaperçu :
 * `MapPoiLayersTest` compare cette table aux deux fichiers.
 */
internal val POI_LAYERS: Map<PoiCategory, String> = mapOf(
  PoiCategory.PUBLIC_SERVICES to "poi-public-services",
  PoiCategory.EDUCATION to "poi-education",
  PoiCategory.CULTURE to "poi-culture",
  PoiCategory.HEALTHCARE to "poi-healthcare",
  PoiCategory.TOILETS to "poi-toilets",
  PoiCategory.FOOD to "poi-food",
  PoiCategory.DINING to "poi-dining",
  PoiCategory.HEALTH_SHOPS to "poi-health-shops",
  PoiCategory.MONEY to "poi-money",
  PoiCategory.LODGING to "poi-lodging",
  PoiCategory.EVERYDAY_SERVICES to "poi-services",
  PoiCategory.OTHER_SHOPS to "poi-other-shops",
)

/**
 * Toutes les couches de points d'intérêt auxquelles un appui peut répondre.
 *
 * Les douze y sont, repères de la v1 compris : un pictogramme visible sur la carte qui ne répond
 * pas au doigt est un pictogramme qui ment. Interroger une couche éteinte ne rend rien, il n'y a
 * donc rien à filtrer ici.
 */
internal val POI_TAPPABLE_LAYERS: List<String> = POI_LAYERS.values.toList()

/**
 * Les pictogrammes que les douze couches emploient, tous pris dans les sprites du serveur.
 *
 * Le serveur MOTIS sert 112 dessins à `<base>/sprites/basics/sprites.json`, et les feuilles n'y
 * puisent que ceux-ci : **aucune image n'est ajoutée à l'APK**, qui vise moins de 15 Mo (SPEC.md
 * § 2). Quatre familles n'avaient pas de dessin à elles et empruntent celui d'un de leurs membres,
 * faute de générique dans ce jeu :
 *
 * - `icon-greengrocer` pour l'alimentation, faute d'`icon-supermarket` et d'`icon-convenience` ;
 * - `icon-chalet` pour l'hébergement, faute d'`icon-hotel` et d'`icon-camp_site` ;
 * - `icon-hairdresser` pour les services du quotidien, faute de dessin générique de service ;
 * - `icon-shop` pour les autres commerces, qui est lui le générique du jeu.
 *
 * `MapPoiLayersTest` compare cette liste aux deux feuilles, dans les deux sens : un nom mal
 * orthographié laisserait un pictogramme manquant sur la carte, sans la moindre erreur.
 */
internal val POI_SPRITES: Set<String> = setOf(
  // Repères de la v1, repris un à un de l'ancienne couche `poi-landmarks`.
  "icon-town_hall",
  "icon-post",
  "icon-police",
  "icon-fire_station",
  "icon-community",
  "icon-place_of_worship",
  "icon-school",
  "icon-college",
  "icon-library",
  "icon-theatre",
  "icon-cinema",
  "icon-art_gallery",
  "icon-castle",
  "icon-monument",
  "icon-artwork",
  "icon-viewpoint",
  "icon-lighthouse",
  "icon-historic",
  "icon-hospital",
  "icon-doctor",
  "icon-pharmacy",
  // Commerces et services, un dessin par catégorie.
  "icon-toilet",
  "icon-greengrocer",
  "icon-restaurant",
  "icon-chemist",
  "icon-bank",
  "icon-chalet",
  "icon-hairdresser",
  "icon-shop",
)
