package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.geo.isWithinMeters
import io.github.mgdx.escale.core.model.Location

/**
 * Écarte les suggestions que le serveur rend en double.
 *
 * `/api/v1/geocode` répète des résultats strictement identiques : « Louvre » (Paris, lieu) sort
 * trois fois sur `louvre`, « Gare de Lyon-Diderot » deux fois sur `gare de lyon`. Trois lignes
 * identiques dans une liste de dix, ce sont trois places perdues pour des résultats utiles.
 *
 * **Le premier exemplaire l'emporte** : le serveur classe par pertinence, celui qu'il a mis en tête
 * est le bon, et l'ordre des autres n'est pas modifié.
 *
 * Deux entrées sont tenues pour la même quand **tout** ceci est vrai :
 *
 * - même [Location.kind] — une adresse et un arrêt de même nom sont deux endroits ;
 * - même [Location.name] et même [Location.description] après normalisation (casse, espaces
 *   multiples, tirets et apostrophes typographiques) : c'est la description qui sépare deux
 *   homonymes de communes différentes, et elle doit donc être comparée, jamais ignorée ;
 * - coordonnées distantes de moins de [DUPLICATE_RADIUS_METERS].
 *
 * Une exception l'emporte sur tout le reste : **deux identifiants non nuls et distincts ne sont
 * jamais fusionnés**. Deux arrêts homonymes séparés de vingt mètres — les deux sens d'une même rue,
 * les deux quais d'une station — sont deux points d'embarquement différents, et confondre leurs
 * `stopId` ferait partir la recherche du mauvais quai.
 *
 * Fonction pure, et volontairement écrite champ par champ : comparer des [Location] entières la
 * casserait le jour où un champ s'y ajoute.
 */
fun List<Location>.withoutDuplicates(): List<Location> {
  if (size < 2) return this
  val kept = ArrayList<Location>(size)
  for (candidate in this) {
    if (kept.none { it.isSameSuggestionAs(candidate) }) kept += candidate
  }
  return kept
}

/**
 * Au-delà, deux résultats de même nom sont deux endroits distincts.
 *
 * Cent mètres couvrent l'écart entre deux relevés d'un même lieu — le serveur place tantôt le
 * centroïde du bâtiment, tantôt son entrée — sans atteindre la rue voisine.
 */
const val DUPLICATE_RADIUS_METERS = 100.0

private fun Location.isSameSuggestionAs(other: Location): Boolean {
  // Deux arrêts que la base horaire distingue restent distincts, quoi que disent leurs libellés.
  if (id != null && other.id != null && id != other.id) return false
  return kind == other.kind &&
    name.normalizedForComparison() == other.name.normalizedForComparison() &&
    description?.normalizedForComparison() == other.description?.normalizedForComparison() &&
    coordinates.isWithinMeters(other.coordinates, DUPLICATE_RADIUS_METERS)
}

/**
 * Le libellé ramené à sa forme comparable : minuscules, espaces réduits à un seul, tirets et
 * apostrophes typographiques ramenés au caractère simple.
 *
 * Le serveur puise dans plusieurs jeux de données, qui ne composent pas la ponctuation de la même
 * façon : « Gare de Lyon–Diderot » et « Gare de Lyon-Diderot » sont le même arrêt.
 */
private fun String.normalizedForComparison(): String = buildString(length) {
  var previousWasSpace = false
  for (character in this@normalizedForComparison) {
    val simplified = when (character) {
      in TYPOGRAPHIC_DASHES -> '-'
      in TYPOGRAPHIC_APOSTROPHES -> '\''
      else -> character
    }
    if (simplified.isWhitespace()) {
      // Un seul espace, et aucun en tête : le reste ne distingue pas deux lieux.
      if (isNotEmpty()) previousWasSpace = true
    } else {
      if (previousWasSpace) append(' ')
      previousWasSpace = false
      append(simplified.lowercaseChar())
    }
  }
}

/** Cadratin, demi-cadratin, trait d'union insécable et signe moins, tous rendus par « - ». */
private const val TYPOGRAPHIC_DASHES = "‐‑‒–—―−"

/** Apostrophe courbe, accents employés comme apostrophe, tous rendus par « ' ». */
private const val TYPOGRAPHIC_APOSTROPHES = "‘’ʼ´`"
