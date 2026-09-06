package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Un résultat de `/api/v1/geocode` ou de `/api/v1/reverse-geocode`, décalque du schéma `Match`.
 *
 * [type] et [modes] sont des chaînes et non des énumérations : l'API MOTIS ajoute des valeurs sans
 * préavis, et une valeur inconnue ne doit pas faire échouer toute la réponse. La traduction en
 * types de domaine, avec son cas par défaut, est le travail du mapper.
 *
 * Les champs `tokens`, `importance`, `category`, `tz`, `level`, `street`, `houseNumber`,
 * `zip`, `country` et `matched` existent côté API mais ne sont pas repris : le nom rendu par le serveur porte
 * déjà la rue et le numéro, l'interface n'affiche rien du reste, et `ignoreUnknownKeys` les laisse
 * passer sans bruit.
 */
@Serializable
internal data class GeocodeMatchDto(
  val type: String,
  val name: String,
  val id: String,
  val lat: Double,
  val lon: Double,
  val areas: List<GeocodeAreaDto> = emptyList(),
  val modes: List<String> = emptyList(),
  /**
   * Pertinence calculée par le serveur, absente des réponses qui ne la portent pas.
   *
   * Elle n'est reprise que pour documenter l'ordre de la liste, jamais pour écarter un résultat
   * (voir [io.github.mgdx.escale.core.model.Location.score]).
   */
  val score: Double = 0.0,
)

/** Une division administrative attachée à un résultat, décalque du schéma `Area`. */
@Serializable
internal data class GeocodeAreaDto(
  val name: String,
  val adminLevel: Double = 0.0,
  /** Première division qui lève l'ambiguïté entre deux résultats de même nom. */
  val unique: Boolean = false,
  /** Division à afficher par défaut : celle dont le niveau est le plus proche de 7, la commune. */
  val default: Boolean = false,
)
