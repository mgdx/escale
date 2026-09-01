package io.github.mgdx.escale.core.model

/**
 * Normalisation de l'URL de serveur saisie par l'usager (SPEC.md § 5.6.1).
 *
 * L'application enregistre toujours la **racine** : c'est le client HTTP qui ajoute `/api/v6/...`,
 * `/api/v1/...` et `/tiles/...`. Cette règle est du ressort de `:core` parce qu'elle est
 * entièrement testable en JVM, et parce que l'écran de réglages et le dépôt doivent en appliquer
 * exactement la même.
 */
object ServerUrl {

  /** Serveur par défaut : l'instance publique communautaire (SPEC.md § 4.1). */
  const val DEFAULT_BASE_URL = "https://api.transitous.org"

  private val HOST_PATTERN = Regex("^[A-Za-z0-9.-]+(:\\d{1,5})?$")
  private const val API_SUFFIX = "/api"
  private const val SCHEME_SEPARATOR = "://"

  /**
   * Rend l'URL racine normalisée, ou `null` si la saisie ne peut pas en former une.
   *
   * Traitements appliqués, dans cet ordre :
   * - suppression des espaces de bord ;
   * - ajout de `https://` quand le schéma manque ;
   * - suppression des barres obliques finales ;
   * - suppression d'un suffixe `/api` collé par erreur depuis la documentation.
   */
  fun normalize(raw: String): String? {
    val withScheme = addSchemeIfMissing(raw.trim()) ?: return null
    val schemeEnd = withScheme.indexOf(SCHEME_SEPARATOR) + SCHEME_SEPARATOR.length
    val scheme = withScheme.substring(0, schemeEnd).lowercase()
    var rest = withScheme.substring(schemeEnd).trimEnd('/')
    if (rest.endsWith(API_SUFFIX, ignoreCase = true)) {
      rest = rest.dropLast(API_SUFFIX.length).trimEnd('/')
    }
    val host = rest.substringBefore('/')
    return if (host.isNotEmpty() && HOST_PATTERN.matches(host)) scheme + rest else null
  }

  /** Ajoute `https://` quand le schéma manque, et refuse tout schéma autre que HTTP. */
  private fun addSchemeIfMissing(trimmed: String): String? = when {
    trimmed.isEmpty() -> null
    trimmed.startsWith("http://", ignoreCase = true) -> trimmed
    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
    trimmed.contains(SCHEME_SEPARATOR) -> null
    else -> "https://$trimmed"
  }

  /** Vrai si l'URL passe en clair, ce qui impose un avertissement explicite (SPEC.md § 5.6.1). */
  fun isCleartext(baseUrl: String): Boolean = baseUrl.startsWith("http://", ignoreCase = true)

  /** L'hôte seul, tel qu'il doit être inscrit dans `network_security_config.xml`. Nul si l'URL est invalide. */
  fun hostOf(baseUrl: String): String? {
    val schemeEnd = baseUrl.indexOf(SCHEME_SEPARATOR)
    if (schemeEnd < 0) return null
    val authority = baseUrl.substring(schemeEnd + SCHEME_SEPARATOR.length).substringBefore('/')
    val host = authority.substringBefore(':')
    return host.ifEmpty { null }
  }
}
