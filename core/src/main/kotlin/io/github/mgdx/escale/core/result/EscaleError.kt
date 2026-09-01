package io.github.mgdx.escale.core.result

/**
 * Les cas d'échec que l'interface doit distinguer à l'écran (SPEC.md § 8).
 *
 * Aucune de ces valeurs ne porte de coordonnée, d'adresse, de paramètre de requête ni d'URL :
 * SPEC.md § 8 et § 11 interdisent qu'une donnée de localisation se retrouve dans une trace,
 * y compris en compilation de débogage. C'est la raison pour laquelle [ApiVersionTooOld] ne
 * transporte que le chemin du point d'entrée, jamais l'URL complète appelée.
 */
sealed interface EscaleError {
  /** Aucune connectivité : bandeau explicite et bouton « Réessayer », jamais un écran vide. */
  data object NoNetwork : EscaleError

  /** Le serveur n'a pas répondu dans les 30 secondes (SPEC.md § 7.8). */
  data object Timeout : EscaleError

  /**
   * Le serveur a répondu par une erreur, ou pas du tout.
   * [statusCode] est nul quand la connexion elle-même a échoué.
   */
  data class ServerUnreachable(val statusCode: Int?) : EscaleError

  /**
   * 404 sur un point d'entrée `v6` : le serveur utilise une version de MOTIS antérieure à la 2.9
   * (SPEC.md § 4.3). [endpoint] est le chemin relatif appelé, par exemple `/api/v6/plan`,
   * sans hôte ni paramètre.
   */
  data class ApiVersionTooOld(val endpoint: String) : EscaleError

  /**
   * 400 ou 422 : requête refusée. [serverMessage] reprend le champ `error` de la réponse, qui est
   * un message technique du serveur et ne contient pas la requête de l'usager.
   */
  data class BadRequest(val serverMessage: String?) : EscaleError

  /** Tout le reste. [cause] est le nom de la défaillance, jamais son contexte de données. */
  data class Unknown(val cause: String?) : EscaleError
}
