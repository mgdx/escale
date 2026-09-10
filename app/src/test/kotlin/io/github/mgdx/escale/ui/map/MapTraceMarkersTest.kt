package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.geo.TraceMarkerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La politique de recouvrement et l'échelle des marqueurs de trajet (SPEC.md § 5.3 et § 5.7).
 *
 * Ce que ces cas d'essai tiennent, et qui manquait : les correspondances entrent dans la détection
 * de recouvrement de MapLibre et passent sous les deux extrémités, au lieu d'être toutes dessinées
 * sans condition — ce qui les empilait les unes sur les autres et noircissait l'anneau de départ.
 * Rien ici ne demande MapLibre : ce sont trois tables de décision, et c'est exactement ce qu'il
 * faut vérifier hors appareil.
 */
class MapTraceMarkersTest {

  @Test
  fun `les deux extremites sont dessinees quoi qu'il arrive`() {
    assertTrue(TraceMarkerKind.ORIGIN.alwaysDrawn())
    assertTrue(TraceMarkerKind.DESTINATION.alwaysDrawn())
  }

  @Test
  fun `une correspondance cede la place`() {
    assertFalse(TraceMarkerKind.TRANSFER.alwaysDrawn())
  }

  @Test
  fun `les correspondances sont posees sous les deux extremites`() {
    assertEquals(
      listOf(TraceMarkerKind.TRANSFER, TraceMarkerKind.ORIGIN, TraceMarkerKind.DESTINATION),
      TRACE_MARKER_LAYER_ORDER,
    )
  }

  @Test
  fun `toutes les natures de marqueur ont leur couche`() {
    assertEquals(TraceMarkerKind.entries.toSet(), TRACE_MARKER_LAYER_ORDER.toSet())
    assertEquals(TRACE_MARKER_LAYER_ORDER.size, TRACE_MARKER_LAYER_ORDER.toSet().size)
  }

  @Test
  fun `le dessin plein de la correspondance est compose plus petit que les contours`() {
    val transfer = TraceMarkerKind.TRANSFER.iconSize()
    assertTrue(
      "la correspondance doit rester en deçà du départ",
      transfer < TraceMarkerKind.ORIGIN.iconSize(),
    )
    assertTrue(
      "la correspondance doit rester en deçà de l'arrivée",
      transfer < TraceMarkerKind.DESTINATION.iconSize(),
    )
    assertEquals(TraceMarkerKind.ORIGIN.iconSize(), TraceMarkerKind.DESTINATION.iconSize())
  }
}
