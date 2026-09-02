package io.github.mgdx.escale.core.format

/**
 * Les couleurs de ligne renvoyées par MOTIS (`routeColor`, `routeTextColor`, `rental.color`).
 *
 * Elles arrivent en hexadécimal, avec ou sans `#`, sur trois ou six chiffres selon la source GTFS.
 * Une valeur illisible n'est pas une panne : elle rend `null`, et l'interface retombe sur la
 * palette du thème (SPEC.md § 5.2, « quand ils existent »).
 *
 * Le calcul est ici, en Kotlin pur, pour être vérifiable en JVM (docs/architecture.md § 1) : le
 * paquet `:app` se contente d'envelopper l'entier obtenu dans une `Color`.
 */
object HexColor {

  private const val SHORT_LENGTH = 3
  private const val FULL_LENGTH = 6
  private const val HEX_RADIX = 16
  private const val OPAQUE = 0xFF000000L
  private const val RED_SHIFT = 16
  private const val GREEN_SHIFT = 8
  private const val CHANNEL_MASK = 0xFFL
  private const val CHANNEL_MAX = 255.0

  /** Seuil de luminance au-delà duquel la couleur est claire et réclame un texte sombre. */
  private const val LIGHT_THRESHOLD = 0.5

  // Coefficients de luminance relative, recommandation UIT-R BT.709, reprise par WCAG 2.1.
  private const val RED_WEIGHT = 0.2126
  private const val GREEN_WEIGHT = 0.7152
  private const val BLUE_WEIGHT = 0.0722

  // Linéarisation sRGB, WCAG 2.1.
  private const val LINEAR_LIMIT = 0.03928
  private const val LINEAR_DIVISOR = 12.92
  private const val GAMMA_OFFSET = 0.055
  private const val GAMMA_DIVISOR = 1.055
  private const val GAMMA_EXPONENT = 2.4

  /**
   * La couleur ARGB opaque décrite par [value], ou `null` si la chaîne n'en décrit aucune.
   *
   * L'entier est rendu en `Long` parce qu'un ARGB opaque déborde de `Int` en Kotlin : `:app` le
   * repasse à `Color(...)`, qui attend précisément un `Long`.
   */
  fun parse(value: String?): Long? {
    val digits = value?.trim()?.removePrefix("#").orEmpty()
    val expanded = when (digits.length) {
      SHORT_LENGTH -> digits.flatMap { listOf(it, it) }.joinToString("")
      FULL_LENGTH -> digits
      else -> return null
    }
    return expanded.toLongOrNull(HEX_RADIX)?.let { OPAQUE or it }
  }

  /**
   * Vrai si un texte clair est lisible sur [argb].
   *
   * Sert de repli quand le serveur donne `routeColor` sans `routeTextColor` : SPEC.md § 9 exige un
   * contraste conforme, et poser du noir sur un fond de métro bleu nuit ne l'est pas.
   */
  fun needsLightText(argb: Long): Boolean = luminance(argb) < LIGHT_THRESHOLD

  /** Luminance relative de [argb], entre 0 (noir) et 1 (blanc). */
  fun luminance(argb: Long): Double {
    val red = channel((argb shr RED_SHIFT) and CHANNEL_MASK)
    val green = channel((argb shr GREEN_SHIFT) and CHANNEL_MASK)
    val blue = channel(argb and CHANNEL_MASK)
    return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
  }

  private fun channel(value: Long): Double {
    val normalized = value / CHANNEL_MAX
    return if (normalized <= LINEAR_LIMIT) {
      normalized / LINEAR_DIVISOR
    } else {
      Math.pow((normalized + GAMMA_OFFSET) / GAMMA_DIVISOR, GAMMA_EXPONENT)
    }
  }
}
