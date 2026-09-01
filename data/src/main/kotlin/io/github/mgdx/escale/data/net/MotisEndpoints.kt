package io.github.mgdx.escale.data.net

/**
 * Le seul endroit où se construisent les URL du serveur MOTIS.
 *
 * L'URL enregistrée par l'usager est la **racine** du serveur (SPEC.md § 5.6.1) : c'est ici, et
 * nulle part ailleurs, que `/api/v6/...`, `/api/v1/...` et `/tiles/...` lui sont ajoutés. Quand
 * MOTIS changera de version de point d'entrée, il n'y aura qu'un fichier à corriger.
 */
internal object MotisEndpoints {

  // --- Points d'entrée v1 -------------------------------------------------------------------
  const val HEALTH = "/api/v1/health"
  const val GEOCODE = "/api/v1/geocode"
  const val REVERSE_GEOCODE = "/api/v1/reverse-geocode"
  const val RENTALS = "/api/v1/rentals"
  const val INITIAL_MAP = "/api/v1/map/initial"

  // --- Points d'entrée v6, qui supposent MOTIS 2.9 ou plus (SPEC.md § 4.3) ------------------
  const val PLAN = "/api/v6/plan"
  const val REFRESH_ITINERARY = "/api/v6/refresh-itinerary"
  const val TRIP = "/api/v6/trip"
  const val STOPTIMES = "/api/v6/stoptimes"
  const val STOP = "/api/v6/stop"
  const val MAP_STOPS = "/api/v6/map/stops"

  /** Préfixe des points d'entrée dont un 404 signifie « serveur trop ancien ». */
  const val V6_PREFIX = "/api/v6/"

  /** Concatène la racine du serveur et un chemin de point d'entrée, sans jamais doubler la barre. */
  fun url(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path

  /** Tuile vectorielle du fond de carte servi par le serveur lui-même (SPEC.md § 5.7). */
  fun tileUrl(baseUrl: String, zoom: Int, x: Int, y: Int): String = "${baseUrl.trimEnd('/')}/tiles/$zoom/$x/$y.mvt"
}
