package io.github.mgdx.escale.ui.session

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.query.PlanQueryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

/**
 * L'état partagé par la carte de recherche et la feuille de résultats (docs/architecture.md § 11.4).
 *
 * Les deux lots qui viennent codent contre cette classe : ses règles sont donc éprouvées ici, en
 * JVM, avant qu'ils commencent.
 */
class SearchSessionTest {

  private val station = Location(
    id = "de-DE:11:900003201",
    name = "Berlin Hbf",
    description = "Berlin",
    coordinates = LatLon(52.525589, 13.369545),
    kind = PlaceKind.STOP,
  )

  private val address = Location(
    id = null,
    name = "Unter den Linden 1",
    description = "Berlin",
    coordinates = LatLon(52.517036, 13.388860),
    kind = PlaceKind.ADDRESS,
  )

  @Test
  fun `un brouillon neuf est vide, incomplet, et part maintenant`() {
    val session = SearchSession()
    val draft = session.draft.value
    assertNull(draft.from)
    assertNull(draft.to)
    assertEquals(TimeChoice.Now, draft.time)
    assertFalse(draft.isComplete)
  }

  @Test
  fun `le brouillon n est complet qu une fois les deux points renseignes`() {
    val session = SearchSession()
    session.setFrom(station)
    assertFalse("un départ seul ne suffit pas", session.draft.value.isComplete)
    session.setTo(address)
    assertTrue(session.draft.value.isComplete)
  }

  @Test
  fun `effacer un point rend le brouillon incomplet a nouveau`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)
    session.setTo(null)
    assertFalse(session.draft.value.isComplete)
  }

  @Test
  fun `l inversion echange le depart et l arrivee sans toucher a l heure`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)
    val departure = TimeChoice.DepartAt(Instant.parse("2026-09-02T08:30:00Z"))
    session.setTime(departure)

    session.swap()

    assertEquals(address, session.draft.value.from)
    assertEquals(station, session.draft.value.to)
    assertEquals(departure, session.draft.value.time)
  }

  @Test
  fun `l inversion fonctionne aussi quand un seul point est renseigne`() {
    val session = SearchSession()
    session.setTo(station)

    session.swap()

    assertEquals(station, session.draft.value.from)
    assertNull(session.draft.value.to)
  }

  @Test
  fun `effacer ramene le brouillon a son etat initial`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)
    session.setTime(TimeChoice.ArriveBy(Instant.parse("2026-09-02T09:00:00Z")))

    session.clear()

    assertEquals(SearchDraft(), session.draft.value)
  }

  @Test
  fun `toQuery rend null tant que le brouillon est incomplet`() {
    val session = SearchSession()
    assertNull(session.toQuery(JourneyCategory.TRANSIT, SearchPreferences()))

    session.setFrom(station)
    assertNull("un départ seul ne fait pas une requête", session.toQuery(JourneyCategory.TRANSIT, SearchPreferences()))
  }

  @Test
  fun `toQuery reporte fidelement le brouillon et l onglet consulte`() {
    val session = SearchSession()
    val arrival = TimeChoice.ArriveBy(Instant.parse("2026-09-02T09:00:00Z"))
    session.setFrom(station)
    session.setTo(address)
    session.setTime(arrival)

    val query = requireNotNull(session.toQuery(JourneyCategory.BIKE, SearchPreferences()))

    assertEquals(station, query.from)
    assertEquals(address, query.to)
    assertEquals(arrival, query.time)
    assertEquals(JourneyCategory.BIKE, query.category)
  }

  @Test
  fun `toQuery demande les libelles dans la langue de l interface`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)

    val query = requireNotNull(session.toQuery(JourneyCategory.TRANSIT, SearchPreferences()))

    assertEquals(Locale.getDefault().language, query.language)
    assertEquals(Locale.getDefault().language, PlanQueryBuilder.build(query)["language"])
  }

  /**
   * docs/architecture.md § 11.3 : une requête `plan` exprimée par coordonnées autour d'une gare
   * rendait zéro résultat, là où la même exprimée par `stopId` en rendait cinq. La règle vit dans
   * `PlanQueryBuilder` ; ce test vérifie que [SearchSession] ne la contourne pas en amont, en
   * reconstruisant un point à partir des coordonnées affichées.
   */
  @Test
  fun `un arret part par son identifiant, une adresse par ses coordonnees`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)

    val query = requireNotNull(session.toQuery(JourneyCategory.TRANSIT, SearchPreferences()))
    val parameters = PlanQueryBuilder.build(query)

    assertEquals("de-DE:11:900003201", parameters["fromPlace"])
    assertEquals("52.517036,13.38886", parameters["toPlace"])
  }

  @Test
  fun `l inversion inverse aussi les points de la requete`() {
    val session = SearchSession()
    session.setFrom(station)
    session.setTo(address)
    session.swap()

    val parameters = PlanQueryBuilder.build(
      requireNotNull(session.toQuery(JourneyCategory.TRANSIT, SearchPreferences())),
    )

    assertEquals("52.517036,13.38886", parameters["fromPlace"])
    assertEquals("de-DE:11:900003201", parameters["toPlace"])
  }
}
