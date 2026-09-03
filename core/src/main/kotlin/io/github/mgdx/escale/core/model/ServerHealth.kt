package io.github.mgdx.escale.core.model

/**
 * Réponse de `GET /api/v1/health`, décalque du schéma `HealthResponse`.
 *
 * Le point d'entrée répond **400 avec le même corps** tant qu'il n'a pas parcouru un cycle complet
 * de tous ses flux. Ce n'est donc pas une erreur de requête : le serveur est joignable, il n'a
 * simplement pas fini de démarrer. C'est ce que porte [fullyStarted].
 */
data class ServerHealth(
  /** Le serveur consomme au moins un flux temps réel (GTFS-RT, SIRI Lite, VDV). */
  val realtime: Boolean,
  /** Le serveur consomme au moins un flux GBFS, donc du libre-service. */
  val gbfs: Boolean,
  /** Faux tant que le serveur n'a pas terminé son premier cycle de mise à jour. */
  val fullyStarted: Boolean,
)
