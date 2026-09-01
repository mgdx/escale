package io.github.mgdx.escale.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattedDistanceTest {

  @Test
  fun `sous cent metres la distance est au metre pres`() {
    val formatted = FormattedDistance.of(87.4)
    assertEquals(87.0, formatted.value, 1e-9)
    assertEquals(DistanceUnit.METERS, formatted.unit)
    assertEquals(0, formatted.decimals)
  }

  @Test
  fun `entre cent metres et un kilometre la distance est arrondie a la dizaine`() {
    val formatted = FormattedDistance.of(457.0)
    assertEquals(460.0, formatted.value, 1e-9)
    assertEquals(DistanceUnit.METERS, formatted.unit)
  }

  @Test
  fun `au dela d un kilometre la distance passe en kilometres avec une decimale`() {
    val formatted = FormattedDistance.of(2345.0)
    assertEquals(2.3, formatted.value, 1e-9)
    assertEquals(DistanceUnit.KILOMETERS, formatted.unit)
    assertEquals(1, formatted.decimals)
  }

  @Test
  fun `au dela de dix kilometres la distance est arrondie au kilometre`() {
    val formatted = FormattedDistance.of(23456.0)
    assertEquals(23.0, formatted.value, 1e-9)
    assertEquals(DistanceUnit.KILOMETERS, formatted.unit)
    assertEquals(0, formatted.decimals)
  }

  @Test
  fun `une distance absente ou aberrante vaut zero metre`() {
    assertEquals(0.0, FormattedDistance.of(-12.0).value, 1e-9)
    assertEquals(0.0, FormattedDistance.of(Double.NaN).value, 1e-9)
    assertEquals(DistanceUnit.METERS, FormattedDistance.of(Double.NaN).unit)
  }
}
