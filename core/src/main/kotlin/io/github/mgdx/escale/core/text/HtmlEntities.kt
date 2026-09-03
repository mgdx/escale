package io.github.mgdx.escale.core.text

/**
 * Le décodage des entités HTML rencontrées dans les messages de perturbation.
 *
 * **Ce n'est pas une table complète, et ce n'en sera jamais une.** HTML5 en nomme plus de deux
 * mille ; les réseaux qui publient leurs alertes en GTFS-RT en emploient une trentaine. Ce qui est
 * couvert : les cinq entités XML, les espaces et la ponctuation typographique, et la **totalité**
 * du bloc Latin-1 — c'est-à-dire tout ce qu'un texte français, allemand ou espagnol peut avoir
 * besoin d'écrire, `&eacute;` en tête.
 *
 * Une entité inconnue ou mal formée n'est **pas** une erreur : elle est rendue telle quelle, à la
 * lettre. Le texte vient du réseau, et l'abandonner ou lever pour une esperluette isolée priverait
 * l'usager d'un message qu'il pouvait lire.
 */
internal object HtmlEntities {

  /**
   * Le caractère désigné par le corps d'une entité — ce qu'il y a entre `&` et `;` —, ou `null`
   * quand elle n'est pas reconnue.
   *
   * La comparaison des noms est **sensible à la casse**, et elle doit l'être : `&Eacute;` et
   * `&eacute;` ne désignent pas la même lettre.
   */
  fun decode(body: String): String? = when {
    body.isEmpty() -> null
    body[0] == NUMERIC_PREFIX -> numeric(body)
    else -> NAMED[body]
  }

  /** `&#233;` et `&#xE9;`. Un nombre absurde ou hors du domaine Unicode n'est pas décodé. */
  private fun numeric(body: String): String? {
    val hexadecimal = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
    val digits = body.substring(if (hexadecimal) 2 else 1)
    val code = digits.toIntOrNull(if (hexadecimal) HEX_RADIX else DECIMAL_RADIX) ?: return null
    return if (isScalarValue(code)) String(Character.toChars(code)) else null
  }

  /**
   * Vrai si [code] désigne un caractère réellement représentable.
   *
   * Sont refusés les nombres négatifs, ceux au-delà du plan Unicode, les demi-codets — qui ne
   * forment pas un caractère à eux seuls et feraient lever `Character.toChars` — et les caractères
   * de commande, qui n'ont rien à faire dans un message affiché.
   */
  private fun isScalarValue(code: Int): Boolean = when {
    code > Character.MAX_CODE_POINT -> false
    code in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code -> false
    code == '\n'.code || code == '\t'.code -> true
    else -> code >= FIRST_PRINTABLE
  }

  /**
   * Les noms du bloc Latin-1, **dans l'ordre des codets `0xC0` à `0xFF`**.
   *
   * Cet ordre est celui du tableau normalisé, il n'est pas arbitraire : c'est lui qui permet de
   * décrire soixante-quatre entités sans écrire soixante-quatre lignes. Y insérer ou y retirer un
   * nom décalerait tout le reste — la liste se corrige en place, jamais par ajout au milieu.
   */
  private const val LATIN1_NAMES =
    "Agrave,Aacute,Acirc,Atilde,Auml,Aring,AElig,Ccedil,Egrave,Eacute,Ecirc,Euml," +
      "Igrave,Iacute,Icirc,Iuml,ETH,Ntilde,Ograve,Oacute,Ocirc,Otilde,Ouml,times," +
      "Oslash,Ugrave,Uacute,Ucirc,Uuml,Yacute,THORN,szlig," +
      "agrave,aacute,acirc,atilde,auml,aring,aelig,ccedil,egrave,eacute,ecirc,euml," +
      "igrave,iacute,icirc,iuml,eth,ntilde,ograve,oacute,ocirc,otilde,ouml,divide," +
      "oslash,ugrave,uacute,ucirc,uuml,yacute,thorn,yuml"

  /** Codet de la première entité de [LATIN1_NAMES], `&Agrave;`. */
  private const val LATIN1_FIRST_CODE = 0xC0

  /**
   * Les entités hors Latin-1 que les messages de perturbation emploient vraiment : la ponctuation
   * typographique, les espaces, et les cinq entités que XML impose.
   *
   * Les espaces exotiques sont décodés en espace ordinaire plutôt qu'en leur codet : le texte est
   * ensuite remis en forme par Compose, et une espace insécable n'y sert plus qu'à fabriquer un mot
   * trop long pour la largeur de l'écran. Le trait d'union conditionnel, lui, disparaît : il n'a de
   * sens que pour un moteur de césure.
   */
  private val SYMBOLS: Map<String, String> = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "ensp" to " ",
    "emsp" to " ",
    "thinsp" to " ",
    "shy" to "",
    "laquo" to "«",
    "raquo" to "»",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "ndash" to "–",
    "mdash" to "—",
    "hellip" to "…",
    "bull" to "•",
    "middot" to "·",
    "deg" to "°",
    "plusmn" to "±",
    "sup2" to "²",
    "sup3" to "³",
    "frac12" to "½",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "euro" to "€",
    "pound" to "£",
  )

  private val NAMED: Map<String, String> = buildMap {
    putAll(SYMBOLS)
    LATIN1_NAMES.split(',').forEachIndexed { offset, name ->
      put(name, (LATIN1_FIRST_CODE + offset).toChar().toString())
    }
  }

  private const val NUMERIC_PREFIX = '#'
  private const val DECIMAL_RADIX = 10
  private const val HEX_RADIX = 16

  /** En deçà, ce sont des caractères de commande : seuls la tabulation et le saut de ligne passent. */
  private const val FIRST_PRINTABLE = 0x20
}
