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
  private const val RGB_MASK = 0xFFFFFFL

  /** Les deux seules couleurs de texte qui se posent sur n'importe quelle couleur de ligne. */
  private const val WHITE = "#FFFFFF"
  private const val BLACK = "#000000"

  /** Le rapport de contraste minimal du texte courant au niveau AA (WCAG 2.1, § 1.4.3). */
  const val AA_TEXT = 4.5

  /**
   * Le rapport minimal du grand texte et des éléments graphiques porteurs d'information
   * (WCAG 2.1, § 1.4.3 et § 1.4.11).
   */
  const val AA_LARGE = 3.0

  /** La constante d'ambiance de la formule de contraste de WCAG 2.1. */
  private const val CONTRAST_OFFSET = 0.05

  /**
   * Seuil de luminance au-delà duquel la couleur est claire et réclame un texte sombre.
   *
   * Ce n'est **pas** 0,5. Le point de bascule est celui où le noir et le blanc contrastent autant
   * avec le fond, et la formule de contraste de WCAG 2.1 le place bien plus bas :
   * `(L + 0,05) / 0,05 = 1,05 / (L + 0,05)`, donc `L = √(1,05 × 0,05) − 0,05 ≈ 0,179`.
   *
   * Prendre 0,5 revient à poser du blanc sur toute la plage 0,179 → 0,5, où le noir se lit
   * pourtant mieux : sur un bleu de métro moyen, le blanc tombe à 3:1, sous le 4,5:1 qu'exige
   * SPEC.md § 9, alors que le noir y atteint 7:1.
   */
  private const val LIGHT_THRESHOLD = 0.1791

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
   * La couleur [value] ramenée à la forme canonique `#RRGGBB`, ou `null` si elle n'en décrit aucune.
   *
   * C'est cette forme-là que les couches MapLibre attendent : une feuille de style refuse `4dbd38`
   * tout court, et le serveur, lui, l'envoie aussi bien avec que sans `#` (SPEC.md § 5.2).
   */
  fun normalize(value: String?): String? = parse(value)?.let { "#%06X".format(it and RGB_MASK) }

  /**
   * Le noir ou le blanc, celui des deux qui se lit sur [color].
   *
   * Sert quand le réseau publie `routeColor` sans `routeTextColor`, ce qui est fréquent : SPEC.md
   * § 9 exige un contraste conforme, et il vaut mieux le calculer que de parier sur le blanc.
   * Rend `null` si [color] n'est pas une couleur.
   */
  fun readableTextOn(color: String?): String? = parse(color)?.let { if (needsLightText(it)) WHITE else BLACK }

  /**
   * La couleur de texte à poser sur [background] : [preferred] quand le réseau la publie **et
   * qu'elle atteint le niveau AA**, sinon le noir ou le blanc, celui des deux qui se lit.
   *
   * Le détour par le contraste n'est pas de la méfiance gratuite. `route_text_color` est un champ
   * GTFS facultatif que beaucoup de producteurs laissent à sa valeur par défaut, le noir, y compris
   * sur des lignes au fond très sombre : le prendre au mot rend la pastille illisible. SPEC.md § 9
   * exige un contraste conforme, pas un contraste annoncé.
   *
   * Rend `null` quand [background] n'est pas une couleur : sans fond du réseau, c'est la palette du
   * thème qui s'applique, et c'est à l'appelant de choisir le `on…` qui va avec.
   */
  fun textOn(background: String?, preferred: String? = null, minimumRatio: Double = AA_TEXT): String? {
    val backgroundArgb = parse(background) ?: return null
    val preferredArgb = parse(preferred)
    if (preferredArgb != null && contrastRatio(preferredArgb, backgroundArgb) >= minimumRatio) {
      return normalize(preferred)
    }
    return if (needsLightText(backgroundArgb)) WHITE else BLACK
  }

  /**
   * Le rapport de contraste entre [first] et [second], de 1 (identiques) à 21 (noir sur blanc).
   *
   * Formule de WCAG 2.1, § 1.4.3. Les deux couleurs sont supposées opaques, ce que garantit [parse].
   */
  fun contrastRatio(first: Long, second: Long): Double {
    val one = luminance(first)
    val other = luminance(second)
    val lighter = maxOf(one, other)
    val darker = minOf(one, other)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
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
