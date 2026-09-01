package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC.md § 4.3 et docs/architecture.md § 10 : le décodage est testé aux **deux** précisions,
 * 6 pour `/api/v6` et 7 pour `/api/v1`. Se tromper de précision décale les points d'un facteur dix,
 * ce qui reste plausible à l'œil sur une carte : seul un test l'attrape.
 */
class PolylineDecoderTest {

  private val tolerance = 1e-9

  private fun assertPointsEqual(expected: List<LatLon>, actual: List<LatLon>) {
    assertEquals("nombre de points", expected.size, actual.size)
    expected.zip(actual).forEachIndexed { index, (want, got) ->
      assertEquals("latitude du point $index", want.lat, got.lat, tolerance)
      assertEquals("longitude du point $index", want.lon, got.lon, tolerance)
    }
  }

  @Test
  fun `decode a precision 6 pour les points d entree v6`() {
    val encoded = "ka~d|A{dqnCsrEkdOntLgrO"
    val expected = listOf(
      LatLon(48.856614, 2.352222),
      LatLon(48.860000, 2.360500),
      LatLon(48.853000, 2.369000),
    )
    assertPointsEqual(expected, PolylineDecoder.decode(encoded, PolylineDecoder.PRECISION_V6))
  }

  @Test
  fun `decode a precision 7 pour les points d entree v1`() {
    val encoded = "wvuzb\\wytzk@gcaAwt`D~ugCo_eD"
    val expected = listOf(
      LatLon(48.8566140, 2.3522220),
      LatLon(48.8600000, 2.3605000),
      LatLon(48.8530000, 2.3690000),
    )
    assertPointsEqual(expected, PolylineDecoder.decode(encoded, PolylineDecoder.PRECISION_V1))
  }

  @Test
  fun `la meme chaine decodee aux deux precisions differe d un facteur dix`() {
    val encoded = "ka~d|A{dqnCsrEkdOntLgrO"
    val atSix = PolylineDecoder.decode(encoded, PolylineDecoder.PRECISION_V6)
    val atSeven = PolylineDecoder.decode(encoded, PolylineDecoder.PRECISION_V1)
    assertEquals(atSix.size, atSeven.size)
    atSix.zip(atSeven).forEach { (six, seven) ->
      assertEquals(six.lat, seven.lat * 10.0, tolerance)
      assertEquals(six.lon, seven.lon * 10.0, tolerance)
    }
  }

  @Test
  fun `decode des coordonnees negatives`() {
    val decoded = PolylineDecoder.decode("|lbr_A{c}k_H", PolylineDecoder.PRECISION_V6)
    assertPointsEqual(listOf(LatLon(-33.867487, 151.206990)), decoded)
  }

  @Test
  fun `decode la polyligne d exemple de Google a precision 5`() {
    val decoded = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
    val expected = listOf(
      LatLon(38.5, -120.2),
      LatLon(40.7, -120.95),
      LatLon(43.252, -126.453),
    )
    assertPointsEqual(expected, decoded)
  }

  @Test
  fun `une polyligne vide rend une liste vide`() {
    // Cas normal : avec detailedLegs=false, le serveur renvoie une polyligne vide.
    assertTrue(PolylineDecoder.decode("", PolylineDecoder.PRECISION_V6).isEmpty())
  }
}
