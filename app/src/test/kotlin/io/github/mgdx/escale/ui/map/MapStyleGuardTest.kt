package io.github.mgdx.escale.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La garde qui empêche `ApplyMapSources` de toucher une feuille de style périmée.
 *
 * Changer de thème ou de serveur relance `setStyle` : MapLibre détache alors la feuille précédente,
 * et tout appel de `getSourceAs` ou de `getLayer` sur cet objet lève une `IllegalStateException`
 * — « Calling getSourceAs when a newer style is loading/has loaded », qui tuait le processus. Les
 * onze effets de `ApplyMapSources` capturent la feuille au moment de leur lancement : chacun doit
 * donc demander à MapLibre si elle est encore la bonne, et pas seulement celui de la trace.
 *
 * La règle ne s'éprouve pas en exécutant l'application — il faudrait un appareil, une carte, et le
 * bon instant à la milliseconde près. Elle s'éprouve sur le code, comme `NoBackgroundWorkTest` et
 * `MapPoiLayersTest` : ce cas d'essai lit `MapCanvas.kt` et échoue si un seul de ces effets se sert
 * de la feuille sans passer par `takeIfCurrent()`.
 */
class MapStyleGuardTest {

  /** Le fichier, commentaires retirés : la documentation explique la règle, elle ne la prouve pas. */
  private val code: String = File(MAP_CANVAS).readText()
    .replace(BLOCK_COMMENT, "")
    .replace(LINE_COMMENT, "")

  /** Le corps de `ApplyMapSources`, seul endroit où la feuille capturée est relue. */
  private val body: String = bodyOf(code, HEADER)

  @Test
  fun `la garde interroge MapLibre sur l'etat de la feuille`() {
    assertTrue("$CALL doit s'appuyer sur $GUARD, seul juge de la feuille en cours.", code.contains(GUARD))
  }

  @Test
  fun `les onze effets sont tous la`() {
    assertEquals(
      "Un effet ajouté ou retiré : le compte de ce cas d'essai doit suivre.",
      EFFECTS,
      Regex("""LaunchedEffect\(""").findAll(body).count(),
    )
  }

  @Test
  fun `aucun effet ne se sert de la feuille sans la garde`() {
    val offenders = Regex("""\bstyle\b""").findAll(body)
      .map { match -> body.substring(match.range.first, minOf(match.range.first + EXCERPT, body.length)) }
      .filterNot { it.startsWith("style$CALL") || it.startsWith("style,") }
      .map { it.substringBefore('\n').trim() }
      .toList()

    assertEquals("La feuille ne se lit qu'à travers $CALL", emptyList<String>(), offenders)
  }

  @Test
  fun `chaque effet passe par la garde`() {
    assertEquals(
      "Chacun des effets doit poser la question, une fois et une seule.",
      EFFECTS,
      Regex(Regex.escape("style$CALL")).findAll(body).count(),
    )
  }

  /**
   * Le corps de la fonction qui suit [header], accolades appariées.
   *
   * Une simple recherche de la prochaine `}` s'arrêterait à la première lambda venue, et la moitié
   * des effets échapperait à l'inspection sans que rien ne le signale.
   */
  private fun bodyOf(source: String, header: String): String {
    val start = source.indexOf(header)
    assertTrue("$header introuvable dans $MAP_CANVAS", start >= 0)
    val open = source.indexOf('{', start)
    var depth = 0
    for (index in open until source.length) {
      when (source[index]) {
        '{' -> depth++
        '}' -> if (--depth == 0) return source.substring(open + 1, index)
        else -> Unit
      }
    }
    error("Accolades non appariées après $header")
  }

  private companion object {
    const val MAP_CANVAS = "src/main/kotlin/io/github/mgdx/escale/ui/map/MapCanvas.kt"

    const val HEADER = "private fun ApplyMapSources("

    /** Le seul chemin autorisé jusqu'à la feuille, à l'intérieur de `ApplyMapSources`. */
    const val CALL = ".takeIfCurrent()"

    /** Ce sur quoi la garde se prononce : l'avis de MapLibre, et non une supposition. */
    const val GUARD = "isFullyLoaded"

    /** Dix sources GeoJSON, plus les douze couches de points d'intérêt (SPEC.md § 5.7). */
    const val EFFECTS = 11

    /** De quoi lire ce qui suit une occurrence de `style` : le nom de ce qui est appelé dessus. */
    const val EXCERPT = 24

    val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    val LINE_COMMENT = Regex("//.*")
  }
}
