package io.github.mgdx.escale.ui.session

import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.search.address
import io.github.mgdx.escale.ui.search.stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Ce que `SavedStateHandle` conserve quand le systeme tue le processus en arriere-plan. */
class SavedSearchTest {

  @Test
  fun `un aller-retour conserve l'identifiant d'arret et le type de lieu`() {
    val draft = SearchDraft(
      from = stop("de:0800:1234", "Gare de Lyon"),
      to = address("12 rue de la Paix"),
      time = TimeChoice.ArriveBy(Instant.ofEpochMilli(1_757_000_000_000)),
    )

    val restored = decodeSearchDraft(encodeSearchDraft(draft))

    // Perdre le `stopId` a la rotation relancerait une requete par coordonnees, que
    // docs/architecture.md § 11.3 proscrit autour d'une gare.
    assertEquals(draft, restored)
    assertEquals("de:0800:1234", restored?.from?.id)
    assertEquals(PlaceKind.STOP, restored?.from?.kind)
    assertEquals(listOf(TransitMode.RAIL, TransitMode.SUBWAY), restored?.from?.servedModes)
  }

  @Test
  fun `un brouillon vide se relit tel quel`() {
    assertEquals(SearchDraft(), decodeSearchDraft(encodeSearchDraft(SearchDraft())))
  }

  @Test
  fun `partir maintenant ne se fige pas en une heure`() {
    val restored = decodeSearchDraft(encodeSearchDraft(SearchDraft(time = TimeChoice.Now)))

    assertEquals(TimeChoice.Now, restored?.time)
  }

  @Test
  fun `une heure de depart survit a la mort du processus`() {
    val time = TimeChoice.DepartAt(Instant.ofEpochMilli(1_757_012_345_000))

    assertEquals(time, decodeSearchDraft(encodeSearchDraft(SearchDraft(time = time)))?.time)
  }

  @Test
  fun `un etat sauvegarde illisible ne fait pas planter l'ecran`() {
    assertNull(decodeSearchDraft("ceci n'est pas du JSON"))
    assertNull(decodeSearchDraft(""))
  }
}
