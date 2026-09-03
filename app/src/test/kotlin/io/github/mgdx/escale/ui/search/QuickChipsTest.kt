package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.ui.session.SearchDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC.md § 5.1 : quelles puces s'affichent, et surtout lesquelles ne s'affichent pas. */
class QuickChipsTest {

  private val home = address("Domicile")
  private val work = address("Travail")
  private val recent = RecentSearch(
    id = 1,
    from = address("Bastille"),
    to = stop("stop:1", "Gare de Lyon"),
    time = TimeChoice.Now,
  )

  @Test
  fun `champs vides, domicile et travail renseignes, les deux puces s'affichent`() {
    val chips = quickChips(SearchDraft(), home, work, emptyList())

    assertEquals(
      listOf(QuickChip.Saved(SavedPlaceKind.HOME, home), QuickChip.Saved(SavedPlaceKind.WORK, work)),
      chips,
    )
  }

  @Test
  fun `une puce absente n'est simplement pas affichee`() {
    val chips = quickChips(SearchDraft(), home = null, work = work, recentSearches = emptyList())

    // Pas de puce grisee, pas d'invite a renseigner son domicile : rien du tout (SPEC.md § 5.5).
    assertEquals(listOf(QuickChip.Saved(SavedPlaceKind.WORK, work)), chips)
  }

  @Test
  fun `aucun favori, aucun historique, aucune puce`() {
    assertTrue(quickChips(SearchDraft(), null, null, emptyList()).isEmpty())
  }

  @Test
  fun `les dernieres recherches suivent les lieux enregistres`() {
    val chips = quickChips(SearchDraft(), home, null, listOf(recent))

    assertEquals(listOf(QuickChip.Saved(SavedPlaceKind.HOME, home), QuickChip.Recent(recent)), chips)
  }

  @Test
  fun `un champ rempli fait disparaitre les puces`() {
    val chips = quickChips(SearchDraft(from = address("Bastille")), home, work, listOf(recent))

    assertTrue(chips.isEmpty())
  }

  @Test
  fun `les puces sont bornees, une rangee n'est pas un pense-bete`() {
    val many = List(12) { index ->
      RecentSearch(
        id = index.toLong(),
        from = address("Depart $index"),
        to = address("Arrivee $index"),
        time = TimeChoice.Now,
      )
    }

    assertEquals(5, quickChips(SearchDraft(), null, null, many).size)
  }

  @Test
  fun `ma position n'est proposee que si une position est deja connue`() {
    assertEquals(listOf(SearchShortcut.PICK_ON_MAP), searchShortcuts(false, null, null))
    assertEquals(
      listOf(SearchShortcut.MY_LOCATION, SearchShortcut.PICK_ON_MAP),
      searchShortcuts(true, null, null),
    )
  }

  @Test
  fun `les entrees de tete suivent l'ordre de la spec`() {
    assertEquals(
      listOf(
        SearchShortcut.MY_LOCATION,
        SearchShortcut.HOME,
        SearchShortcut.WORK,
        SearchShortcut.PICK_ON_MAP,
      ),
      searchShortcuts(myLocationKnown = true, home = home, work = work),
    )
  }
}
