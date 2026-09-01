package io.github.mgdx.escale.data.prefs

import kotlinx.serialization.Serializable

/**
 * Forme sérialisée d'un serveur connu.
 *
 * DataStore Preferences ne sait stocker qu'un ensemble de chaînes, non ordonné : la liste des
 * serveurs déjà utilisés est donc conservée en JSON dans une seule clé, ce qui préserve l'ordre de
 * récence attendu par SPEC.md § 5.6.1.
 */
@Serializable
internal data class ServerPrefsDto(
  val baseUrl: String,
  val label: String,
  val hasTiles: Boolean = false,
  /** Horodatage en millisecondes depuis l'époque, nul si le serveur n'a jamais été testé. */
  val lastCheckedAtMillis: Long? = null,
)
