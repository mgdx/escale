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
  fun `la luminance va du noir au blanc`() {
    assertEquals(0.0, HexColor.luminance(0xFF000000L), 1e-6)
    assertEquals(1.0, HexColor.luminance(0xFFFFFFFFL), 1e-6)
  }
}
