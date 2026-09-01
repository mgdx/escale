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

/**
 * Résultat du test de connexion en trois étapes de l'écran « Serveur MOTIS » (SPEC.md § 5.6.1).
 *
 * Les trois étapes sont indépendantes : un serveur joignable dont l'API est trop ancienne reste
 * signalé comme joignable, et un serveur sans tuiles reste utilisable.
 */
data class ServerCheck(
  /** Étape 1 : `GET /api/v1/health` a répondu. */
  val reachable: Boolean,
  /** Étape 2 : un point d'entrée `v6` a répondu autre chose qu'un 404. */
  val apiCompatible: Boolean,
  /** Étape 3 : le serveur sert une tuile vectorielle sur `/tiles/{z}/{x}/{y}.mvt`. */
  val tilesAvailable: Boolean,
  /** Détail renvoyé par l'étape 1, nul si elle a échoué. */
  val health: ServerHealth? = null,
)
