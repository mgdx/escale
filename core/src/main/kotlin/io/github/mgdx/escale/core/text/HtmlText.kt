package io.github.mgdx.escale.core.text

/**
 * Réduction en texte simple d'un message venu du réseau qui contient du balisage HTML.
 *
 * **Pourquoi ce fichier existe.** Les `descriptionText` des alertes du réseau francilien arrivent
 * enveloppées dans du HTML — `<p>…</p>` au minimum. Affichés tels quels, l'usager lit les balises.
 * SPEC.md § 2 interdit la `WebView`, le catalogue de bibliothèques est figé, et la règle est
 * purement textuelle : elle vit donc dans `:core`, où elle se vérifie en JVM
 * (docs/architecture.md § 1).
 *
 * **Ce n'est pas un moteur HTML, et il ne faut pas le faire grandir en ce sens.** Il traite le
 * sous-ensemble réellement rencontré :
 * - les balises de **bloc** deviennent un saut de ligne — `<p>`, `<br>`, `<div>`, `<li>`, un titre… ;
 * - les balises **en ligne** disparaissent, leur texte reste — `<b>`, `<em>`, `<span>`… ;
 * - un **lien** garde son libellé, et son adresse est ajoutée entre parenthèses quand elle n'y
 *   figure pas déjà : la perdre effacerait une information que le transporteur a jugée utile. Seules
 *   les adresses `http://` et `https://` sont conservées — un `javascript:` ou un `data:` n'a rien à
 *   faire dans un texte affiché ;
 * - `<script>` et `<style>` sont retirés **avec leur contenu**, qui n'est pas du texte ;
 * - les entités sont décodées par [HtmlEntities] ;
 * - les blancs sont remis en forme : une suite d'espaces vaut un espace, une ligne vide sépare deux
 *   paragraphes, et jamais plus d'une.
 *
 * **Le texte est traité comme hostile**, parce qu'il l'est : il vient du réseau, personne ne le
 * valide, et il s'affiche sur un écran. Aucune entrée ne doit pouvoir faire lever, boucler, ni
 * coûter un temps déraisonnable. D'où trois propriétés que les tests vérifient une à une :
 * - **un seul passage, sans récursion** : le coût est linéaire en la longueur de l'entrée, une
 *   imbrication absurde ne creuse aucune pile, et une chaîne d'un mégaoctet passe en quelques
 *   millisecondes ;
 * - **rien n'est jamais abandonné** : une balise non fermée, un commentaire sans fin, un `<` isolé,
 *   une entité invalide sont rendus tels quels ou ignorés, jamais propagés en exception ;
 * - **la recherche de fin est bornée** là où elle pourrait devenir quadratique : le corps d'une
 *   entité n'est cherché que sur quelques caractères, pas jusqu'au bout de la chaîne.
 */
object HtmlText {

  /**
   * [raw] débarrassé de son balisage.
   *
   * Un texte qui n'en contient pas est simplement remis en forme : c'est le cas le plus fréquent,
   * la plupart des réseaux publiant du texte brut.
   */
  fun toPlainText(raw: String): String {
    if (raw.isEmpty()) return raw
    val stripped = if (raw.none { it == TAG_OPEN || it == AMPERSAND }) raw else Scanner(raw).strip()
    return normalizeWhitespace(stripped)
  }
}

/**
 * Le passage unique sur la chaîne.
 *
 * Chaque étape rend l'indice où reprendre, et cet indice **progresse toujours** : c'est ce qui
 * garantit l'arrêt quelle que soit l'entrée.
 */
private class Scanner(private val raw: String) {

  private val out = StringBuilder(raw.length)

  /** Adresse du lien en cours, et position de son libellé dans [out]. */
  private var href: String? = null
  private var labelStart = NO_LABEL

  fun strip(): String {
    var index = 0
    while (index < raw.length) {
      index = when (raw[index]) {
        TAG_OPEN -> tag(index)
        AMPERSAND -> entity(index)
        else -> text(index)
      }
    }
    return out.toString()
  }

  private fun text(start: Int): Int {
    out.append(raw[start])
    return start + 1
  }

  private fun tag(start: Int): Int =
    if (raw.startsWith(COMMENT_OPEN, start)) skipPast(start, COMMENT_CLOSE) else element(start)

  /**
   * Une balise, ou un `<` qui n'en ouvre aucune.
   *
   * Un `<` que ne suit pas un nom est du texte — « 3 < 5 » doit rester lisible —, et une balise que
   * rien ne referme consomme le reste de la chaîne plutôt que de faire rejeter le message.
   */
  private fun element(start: Int): Int {
    var cursor = start + 1
    val closing = cursor < raw.length && raw[cursor] == SLASH
    if (closing) cursor++
    val nameEnd = nameEnd(cursor)
    if (nameEnd == cursor) return text(start)
    val name = raw.substring(cursor, nameEnd).lowercase()
    val tagEnd = tagEnd(nameEnd)
    val after = if (tagEnd < raw.length) tagEnd + 1 else raw.length
    if (name in OPAQUE_TAGS && !closing) return skipPast(skipPast(after, CLOSE_TAG_OPEN + name), TAG_CLOSE)
    emit(name = name, closing = closing, attributes = raw.substring(nameEnd, tagEnd))
    return after
  }

  private fun nameEnd(from: Int): Int {
    var cursor = from
    while (cursor < raw.length && raw[cursor].isLetterOrDigit()) cursor++
    return cursor
  }

  /**
   * L'indice du `>` qui ferme la balise, ou la fin de la chaîne s'il n'y en a pas.
   *
   * Le `>` d'une valeur d'attribut entre guillemets ne compte pas : `<a title="a > b">` est une
   * seule balise.
   */
  private fun tagEnd(from: Int): Int {
    var cursor = from
    var quote = NO_QUOTE
    while (cursor < raw.length) {
      val character = raw[cursor]
      when {
        quote != NO_QUOTE -> if (character == quote) quote = NO_QUOTE
        character == '"' || character == '\'' -> quote = character
        character == TAG_CLOSE_CHAR -> return cursor
      }
      cursor++
    }
    return raw.length
  }

  /**
   * Ce qu'une balise reconnue produit : un saut de ligne, un lien, ou rien du tout.
   *
   * Une balise de bloc ouvre toujours une ligne ; seule une balise de **paragraphe** en ouvre une
   * aussi en se refermant. Sans cette distinction, une liste à puces gagnerait une ligne vide entre
   * chaque item — `</li><li>` produirait deux sauts, exactement comme `</p><p>`.
   */
  private fun emit(name: String, closing: Boolean, attributes: String) {
    when {
      name == ANCHOR_TAG -> anchor(closing, attributes)
      name in BLOCK_TAGS -> if (!closing || name in PARAGRAPH_TAGS) out.append('\n')
      else -> Unit
    }
  }

  private fun anchor(closing: Boolean, attributes: String) {
    if (!closing) {
      href = hrefOf(attributes)
      labelStart = out.length
      return
    }
    val url = href ?: return
    val label = if (labelStart in 0..out.length) out.substring(labelStart) else ""
    href = null
    labelStart = NO_LABEL
    if (!label.contains(url)) out.append(" ($url)")
  }

  /**
   * Une entité, décodée ou rendue à la lettre.
   *
   * La recherche du `;` est **bornée** : sans cela, une chaîne pleine d'esperluettes sans entité
   * ferait relire la fin du texte à chaque fois, et le coût deviendrait quadratique.
   */
  private fun entity(start: Int): Int {
    val limit = minOf(raw.length, start + MAX_ENTITY_LENGTH)
    val end = (start + 1 until limit).firstOrNull { raw[it] == SEMICOLON } ?: return text(start)
    val decoded = HtmlEntities.decode(raw.substring(start + 1, end)) ?: return text(start)
    out.append(decoded)
    return end + 1
  }

  /** Saute jusqu'après [marker], ou jusqu'au bout de la chaîne quand il manque. */
  private fun skipPast(start: Int, marker: String): Int {
    if (start >= raw.length) return raw.length
    val at = raw.indexOf(marker, start, ignoreCase = true)
    return if (at < 0) raw.length else at + marker.length
  }
}

/** L'adresse d'un lien, ou `null` quand il n'y en a pas d'exploitable. */
private fun hrefOf(attributes: String): String? {
  val match = HREF.find(attributes) ?: return null
  val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty().trim()
  return value.takeIf { it.startsWith(HTTP, ignoreCase = true) || it.startsWith(HTTPS, ignoreCase = true) }
}

/**
 * Les blancs, remis en forme.
 *
 * Le saut de ligne porte la structure, tout le reste est un séparateur de mots : espaces
 * insécables, tabulations, espaces de largeur nulle et caractères de commande sont ramenés à un
 * espace unique. Deux paragraphes restent séparés par une ligne vide, jamais par trois.
 */
private fun normalizeWhitespace(text: String): String = joinLines(text.split('\n').map(::collapseSpaces))

private fun collapseSpaces(line: String): String {
  val out = StringBuilder(line.length)
  var pendingSpace = false
  line.forEach { character ->
    if (character.isHorizontalSpace()) {
      pendingSpace = true
    } else {
      if (pendingSpace && out.isNotEmpty()) out.append(' ')
      pendingSpace = false
      out.append(character)
    }
  }
  return out.toString()
}

private fun joinLines(lines: List<String>): String {
  val out = StringBuilder()
  var blankSeen = false
  lines.forEach { line ->
    if (line.isEmpty()) {
      blankSeen = true
    } else {
      if (out.isNotEmpty()) out.append(if (blankSeen) "\n\n" else "\n")
      blankSeen = false
      out.append(line)
    }
  }
  return out.toString()
}

private fun Char.isHorizontalSpace(): Boolean =
  this != '\n' && (isWhitespace() || Character.isSpaceChar(this) || isISOControl() || this in INVISIBLE)

/**
 * Les balises dont le contenu **n'est pas du texte** : il est retiré avec elles.
 *
 * Aucun message de perturbation n'en contient, et c'est justement la raison : le jour où il en
 * arrive un, ce n'est pas une phrase à montrer à l'usager.
 */
private val OPAQUE_TAGS = setOf("script", "style")

/** Les balises qui séparent deux blocs de texte, et qui ouvrent donc une ligne. */
private val BLOCK_TAGS = setOf(
  "br", "p", "div", "hr", "pre", "blockquote", "section", "article", "header", "footer", "aside",
  "ul", "ol", "li", "dl", "dt", "dd", "table", "thead", "tbody", "tr", "td", "th", "caption",
  "h1", "h2", "h3", "h4", "h5", "h6", "figure", "figcaption", "form", "fieldset", "address",
)

/** Celles de [BLOCK_TAGS] qui ouvrent aussi une ligne **en se refermant** : les vrais paragraphes. */
private val PARAGRAPH_TAGS = setOf(
  "p", "div", "pre", "blockquote", "section", "article", "header", "footer", "aside",
  "ul", "ol", "dl", "table", "caption", "h1", "h2", "h3", "h4", "h5", "h6",
  "figure", "figcaption", "form", "fieldset", "address",
)

/** Les blancs que `isWhitespace` ne reconnaît pas mais qui n'en sont pas moins invisibles. */
private val INVISIBLE = setOf('\u200B', '\u200C', '\u200D', '\uFEFF')

private val HREF = Regex("""href\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)

private const val ANCHOR_TAG = "a"
private const val COMMENT_OPEN = "<!--"
private const val COMMENT_CLOSE = "-->"
private const val CLOSE_TAG_OPEN = "</"
private const val TAG_CLOSE = ">"
private const val TAG_OPEN = '<'
private const val TAG_CLOSE_CHAR = '>'
private const val SLASH = '/'
private const val AMPERSAND = '&'
private const val SEMICOLON = ';'
private const val NO_QUOTE = ' '
private const val NO_LABEL = -1
private const val HTTP = "http://"
private const val HTTPS = "https://"

/**
 * Longueur maximale d'une entité, `&` et `;` compris.
 *
 * `&#x10FFFF;` en fait dix ; au-delà, ce n'est plus une entité mais une esperluette suivie de texte.
 * Cette borne n'est pas un confort : c'est elle qui garde le traitement linéaire.
 */
private const val MAX_ENTITY_LENGTH = 12
