package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexColorTest {

  @Test
  fun `une couleur sur six chiffres est lue avec ou sans diese`() {
    assertEquals(0xFF1D9E75L, HexColor.parse("#1D9E75"))
    assertEquals(0xFF1D9E75L, HexColor.parse("1d9e75"))
  }

  @Test
  fun `une couleur sur trois chiffres est developpee`() {
    assertEquals(0xFFAABBCCL, HexColor.parse("#abc"))
  }

  @Test
  fun `une valeur absente ou illisible ne rend aucune couleur`() {
    // Le serveur laisse souvent `routeColor` vide : ce n'est pas une panne, c'est un repli.
    assertNull(HexColor.parse(null))
    assertNull(HexColor.parse(""))
    assertNull(HexColor.parse("#12345"))
    assertNull(HexColor.parse("#GGGGGG"))
  }

  @Test
  fun `un fond sombre reclame un texte clair`() {
    assertTrue(HexColor.needsLightText(HexColor.parse("#00205B")!!))
    assertFalse(HexColor.needsLightText(HexColor.parse("#FFCD00")!!))
  }

  @Test
  fun `un fond de teinte moyenne reclame un texte sombre, pas un texte clair`() {
    // Le piège du seuil à 0,5 : ces deux teintes passent pour « sombres » — leur luminance vaut
    // 0,38 et 0,33 — et un seuil à 0,5 y poserait du blanc. Le blanc n'y atteint pourtant que
    // 2,4:1 et 2,8:1, sous le 4,5:1 qu'exige SPEC.md § 9, là où le noir dépasse 7:1.
    val black = HexColor.parse("#000000")!!
    val white = HexColor.parse("#FFFFFF")!!
    for (value in listOf("#4DBD38", "#EE7C0E")) {
      val background = HexColor.parse(value)!!
      assertFalse(HexColor.needsLightText(background))
      assertTrue(HexColor.contrastRatio(black, background) >= HexColor.AA_TEXT)
      assertFalse(HexColor.contrastRatio(white, background) >= HexColor.AA_TEXT)
    }
  }

  @Test
  fun `le rapport de contraste va de un a vingt-et-un`() {
    val black = HexColor.parse("#000000")!!
    val white = HexColor.parse("#FFFFFF")!!
    assertEquals(21.0, HexColor.contrastRatio(black, white), 1e-6)
    assertEquals(21.0, HexColor.contrastRatio(white, black), 1e-6)
    assertEquals(1.0, HexColor.contrastRatio(white, white), 1e-6)
  }

  @Test
  fun `une couleur de texte publiee par le reseau n'est retenue que si elle se lit`() {
    // `route_text_color` est facultatif en GTFS et souvent laissé au noir : sur un fond de métro
    // bleu nuit, le prendre au mot rend la pastille illisible (SPEC.md § 9).
    assertEquals("#FFFFFF", HexColor.textOn(background = "#00205B", preferred = "#000000"))
    // Publiée et lisible, elle est conservée telle quelle, sous sa forme canonique.
    assertEquals("#FFCD00", HexColor.textOn(background = "#00205B", preferred = "ffcd00"))
  }

  @Test
  fun `sans couleur de texte publiee, le noir ou le blanc est calcule`() {
    assertEquals("#FFFFFF", HexColor.textOn(background = "#00205B"))
    assertEquals("#000000", HexColor.textOn(background = "#FFCD00"))
  }

  @Test
  fun `sans fond du reseau, aucune couleur de texte n'est imposee`() {
    // C'est alors la palette du thème qui s'applique : l'appelant sait quel `on…` va avec, pas nous.
    assertNull(HexColor.textOn(background = null, preferred = "#FFFFFF"))
    assertNull(HexColor.textOn(background = "bleu", preferred = "#FFFFFF"))
  }

  @Test
  fun `le seuil du grand texte est plus permissif que celui du texte courant`() {
    // Un gris moyen sur blanc : 3,5:1. Refusé pour du texte courant, accepté pour un titre.
    val grey = "#949494"
    assertEquals("#000000", HexColor.textOn(background = "#FFFFFF", preferred = grey))
    assertEquals(grey, HexColor.textOn(background = "#FFFFFF", preferred = grey, minimumRatio = HexColor.AA_LARGE))
  }

  @Test
  fun `la luminance va du noir au blanc`() {
    assertEquals(0.0, HexColor.luminance(0xFF000000L), 1e-6)
    assertEquals(1.0, HexColor.luminance(0xFFFFFFFFL), 1e-6)
  }

  @Test
  fun `une couleur est ramenee a la forme que MapLibre attend`() {
    // Le serveur envoie aussi bien `4dbd38` que `#4DBD38` ; une couche MapLibre n'accepte que la
    // seconde forme (SPEC.md § 5.3).
    assertEquals("#4DBD38", HexColor.normalize("4dbd38"))
    assertEquals("#4DBD38", HexColor.normalize("#4dbd38"))
    assertEquals("#FFCC00", HexColor.normalize("fc0"))
    assertNull(HexColor.normalize(null))
    assertNull(HexColor.normalize("bleu"))
  }

  @Test
  fun `le texte lisible sur une couleur de ligne est noir ou blanc`() {
    assertEquals("#FFFFFF", HexColor.readableTextOn("#00205B"))
    assertEquals("#000000", HexColor.readableTextOn("#FFCD00"))
    assertNull(HexColor.readableTextOn(null))
  }
}
