package io.github.mgdx.escale.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le sous-ensemble HTML réellement rencontré dans les messages de perturbation.
 *
 * Les cas nominaux viennent des fixtures du dépôt : `stoptimes_alerts.json` enveloppe sa
 * description dans un `<p>`, `plan_with_alerts.json` y mêle des espaces insécables et des sauts de
 * ligne. Les cas suivants, eux, ne viennent d'aucune fixture et n'en viendront jamais : ce sont
 * ceux d'un serveur hostile ou cassé, et c'est exactement pour eux que ce fichier existe.
 */
class HtmlTextTest {

  // --- Le sous-ensemble réellement rencontré -----------------------------------------------

  @Test
  fun `un paragraphe francilien perd ses balises et garde sa phrase`() {
    val raw = "<p>La ligne 85 est déviée : les arrêts situés entre Barbès - Rochechouart et " +
      "Châteaudun - Lamartine ne sont plus desservis en direction de Châtelet.</p>"
    val plain = HtmlText.toPlainText(raw)
    assertFalse(plain.contains('<'))
    assertTrue(plain.startsWith("La ligne 85 est déviée"))
    assertTrue(plain.endsWith("en direction de Châtelet."))
  }

  @Test
  fun `un texte sans balise ni entite traverse sans dommage`() {
    val raw = "Wegen Gleisbauarbeiten fährt die Linie S2 über eine Umleitung."
    assertEquals(raw, HtmlText.toPlainText(raw))
  }

  @Test
  fun `les balises de bloc deviennent des sauts de ligne`() {
    assertEquals("Un\nDeux", HtmlText.toPlainText("Un<br>Deux"))
    assertEquals("Un\n\nDeux", HtmlText.toPlainText("<p>Un</p><p>Deux</p>"))
    assertEquals("Un\nDeux\nTrois", HtmlText.toPlainText("<ul><li>Un</li><li>Deux</li><li>Trois</li></ul>"))
  }

  @Test
  fun `les balises en ligne disparaissent et leur texte reste`() {
    assertEquals("Trafic interrompu ce soir", HtmlText.toPlainText("Trafic <b>interrompu</b> <em>ce soir</em>"))
  }

  @Test
  fun `les entites sont decodees, nommees comme numeriques`() {
    assertEquals("Tram C & bus 32", HtmlText.toPlainText("Tram C &amp; bus 32"))
    assertEquals("déviée", HtmlText.toPlainText("d&eacute;vi&eacute;e"))
    assertEquals("déviée", HtmlText.toPlainText("d&#233;vi&#xE9;e"))
    assertEquals("À 8 h", HtmlText.toPlainText("&Agrave;&nbsp;8&nbsp;h"))
    assertEquals("« Détour » — 5 min…", HtmlText.toPlainText("&laquo; D&eacute;tour &raquo; &mdash; 5 min&hellip;"))
  }

  @Test
  fun `la casse d une entite nommee est significative`() {
    assertEquals("Éé", HtmlText.toPlainText("&Eacute;&eacute;"))
  }

  @Test
  fun `les blancs sont remis en forme sans jamais laisser trois lignes vides`() {
    assertEquals("Un\n\nDeux", HtmlText.toPlainText("<div>Un</div><br><br><br><div>   Deux   </div>"))
    assertEquals("Un Deux", HtmlText.toPlainText("Un  \t  Deux"))
  }

  // --- Les liens ----------------------------------------------------------------------------

  @Test
  fun `un lien garde son libelle et son adresse`() {
    val plain = HtmlText.toPlainText("Voir <a href=\"https://exemple.test/info\">le détail</a>.")
    assertEquals("Voir le détail (https://exemple.test/info).", plain)
  }

  @Test
  fun `une adresse deja lisible dans le libelle n est pas repetee`() {
    val plain = HtmlText.toPlainText("<a href='https://exemple.test'>https://exemple.test</a>")
    assertEquals("https://exemple.test", plain)
  }

  @Test
  fun `une adresse qui n est ni http ni https est abandonnee`() {
    assertEquals("Cliquez", HtmlText.toPlainText("<a href=\"javascript:alert(1)\">Cliquez</a>"))
    assertEquals("Cliquez", HtmlText.toPlainText("<a href=\"data:text/html,x\">Cliquez</a>"))
    assertEquals("Cliquez", HtmlText.toPlainText("<a href=\"/relatif\">Cliquez</a>"))
  }

  @Test
  fun `un attribut entre guillemets peut contenir un chevron fermant`() {
    assertEquals("Détour", HtmlText.toPlainText("<span title=\"a > b\">Détour</span>"))
  }

  // --- Contenus qui ne sont pas du texte ------------------------------------------------------

  @Test
  fun `script et style partent avec leur contenu`() {
    assertEquals("Avant Après", HtmlText.toPlainText("Avant <script>var x = 1 < 2;</script> Après"))
    assertEquals("Avant Après", HtmlText.toPlainText("Avant <style>p { color: red }</style> Après"))
  }

  @Test
  fun `un commentaire disparait, meme sans fin`() {
    assertEquals("Avant Après", HtmlText.toPlainText("Avant <!-- note interne --> Après"))
    assertEquals("Avant", HtmlText.toPlainText("Avant <!-- note jamais refermée"))
  }

  // --- Entrées hostiles : rien ne doit lever, boucler, ni traîner ------------------------------

  @Test
  fun `une balise jamais refermee ne fait pas perdre le message`() {
    assertEquals("Texte", HtmlText.toPlainText("<p>Texte"))
    assertEquals("Texte", HtmlText.toPlainText("Texte <b"))
    assertEquals("Texte", HtmlText.toPlainText("Texte <span class=\"jamais fermé"))
  }

  @Test
  fun `un chevron isole reste du texte`() {
    assertEquals("3 < 5 > 2", HtmlText.toPlainText("3 < 5 > 2"))
    assertEquals("<<<< >>>>", HtmlText.toPlainText("<<<< >>>>"))
  }

  @Test
  fun `une imbrication absurde ne creuse aucune pile`() {
    val depth = 10_000
    val raw = "<div>".repeat(depth) + "Perturbation" + "</div>".repeat(depth)
    assertEquals("Perturbation", HtmlText.toPlainText(raw))
  }

  @Test
  fun `une entite invalide est rendue a la lettre`() {
    assertEquals("&", HtmlText.toPlainText("&"))
    assertEquals("&;", HtmlText.toPlainText("&;"))
    assertEquals("&#;", HtmlText.toPlainText("&#;"))
    assertEquals("&#xZZ;", HtmlText.toPlainText("&#xZZ;"))
    assertEquals("&pasuneentite;", HtmlText.toPlainText("&pasuneentite;"))
    assertEquals("&#99999999999999;", HtmlText.toPlainText("&#99999999999999;"))
    // Un demi-codet ne forme pas un caractère : il ne doit pas faire lever `Character.toChars`.
    assertEquals("&#xD800;", HtmlText.toPlainText("&#xD800;"))
    // Zéro et les caractères de commande ne sont pas affichables : l'entité reste à la lettre
    // plutôt que de devenir un caractère invisible au milieu du message.
    assertEquals("&#0;", HtmlText.toPlainText("&#0;"))
  }

  @Test
  fun `les caracteres de commande et les blancs invisibles sont neutralises`() {
    // Espace insécable, espace de largeur nulle, indicateur d'ordre des octets, octet nul : tous
    // ramenés à un séparateur de mots unique, aucun ne subsiste dans le texte affiché.
    assertEquals("Un Deux", HtmlText.toPlainText("Un\u00A0Deux"))
    assertEquals("Un Deux", HtmlText.toPlainText("Un\u0000\u200B\uFEFF\tDeux"))
  }

  @Test
  fun `un message entierement balise se reduit au vide`() {
    assertEquals("", HtmlText.toPlainText("<p></p><div><br/></div>"))
    assertEquals("", HtmlText.toPlainText(""))
    assertEquals("", HtmlText.toPlainText("   \n\n   "))
  }

  /**
   * Le coût reste linéaire, y compris sur les deux formes qui pourraient le rendre quadratique :
   * une esperluette à chaque caractère, et un chevron à chaque caractère.
   *
   * Le plafond est **très large** — il ne mesure pas une performance, il attrape une boucle ou une
   * recherche non bornée. Le traitement réel se compte en millisecondes.
   */
  @Test
  fun `un mega-octet de texte hostile passe en un temps raisonnable`() {
    val cases = listOf(
      "<p>Perturbation ".repeat(60_000),
      "&".repeat(1_000_000),
      "&#".repeat(500_000),
      "<".repeat(1_000_000),
      "Perturbation à signaler ".repeat(45_000),
    )
    val start = System.nanoTime()
    cases.forEach { HtmlText.toPlainText(it) }
    val elapsedMillis = (System.nanoTime() - start) / 1_000_000
    assertTrue("traitement trop long : $elapsedMillis ms", elapsedMillis < MAX_ELAPSED_MILLIS)
  }

  private companion object {
    const val MAX_ELAPSED_MILLIS = 5_000L
  }
}
