package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Un serveur MOTIS configuré (SPEC.md § 5.6.1).
 *
 * [baseUrl] est toujours la **racine** du serveur, normalisée : schéma présent, ni barre oblique
 * finale ni suffixe `/api`. C'est le client HTTP qui ajoute `/api/v6/...`, `/api/v1/...` et
 * `/tiles/...`.
 */
data class ServerConfig(
  val baseUrl: String,
  /** Nom lisible donné par l'usager, ou l'hôte à défaut. */
  val label: String,
  /** Le serveur sert-il un fond de carte ? Un serveur sans tuiles reste utilisable (SPEC.md § 5.7). */
  val hasTiles: Boolean = false,
  /** Date du dernier test de connexion réussi. Nulle si le serveur n'a jamais été testé. */
  val lastCheckedAt: Instant? = null,
)
