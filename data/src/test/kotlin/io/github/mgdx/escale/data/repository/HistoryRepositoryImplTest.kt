package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.HistoryRepository
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.address
import io.github.mgdx.escale.data.db.inMemoryDatabase
import io.github.mgdx.escale.data.db.rowCount
import io.github.mgdx.escale.data.db.stopLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryImplTest {

  private lateinit var database: EscaleDatabase
  private lateinit var preferences: FakePreferencesRepository
  private var now: Instant = START

  @Before
  fun setUp() {
    database = inMemoryDatabase()
    preferences = FakePreferencesRepository()
    now = START
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun repository() = HistoryRepositoryImpl(database, preferences) { now }

  private fun query(to: String) = SearchQuery(
    from = address("12 rue des Lilas"),
    to = stopLocation("de:06:$to", to),
    time = TimeChoice.Now,
    category = JourneyCategory.TRANSIT,
  )

  @Test
  fun `une recherche enregistree se relit, la plus recente d abord`() = runBlocking {
    val repository = repository()
    assertTrue(repository.record(query("Nation")) is Outcome.Success)
    now = now.plusSeconds(60)
    repository.record(query("Bastille"))

    assertEquals(listOf("Bastille", "Nation"), repository.recentSearches.first().map { it.to.name })
    val premiere = repository.recentSearches.first().first()
    assertEquals(now, premiere.searchedAt)
    assertEquals("12 rue des Lilas", premiere.from.name)
  }

  @Test
  fun `le plafond de cinquante est tenu par le stockage, pas par l affichage`() = runBlocking {
    val repository = repository()
    repeat(60) { rang ->
      now = START.plusSeconds(rang.toLong())
      repository.record(query("Arrêt $rang"))
    }

    // La table elle-même ne contient que cinquante lignes : la purge a lieu à l'insertion, et non
    // dans la requête de lecture. C'est ce qui garantit qu'aucun lieu ne survit dans le fichier à
    // la vue qu'en a l'usager (SPEC.md § 5.5 et § 11).
    assertEquals(HistoryRepository.MAX_ENTRIES, database.rowCount("search_history"))

    val relues = repository.recentSearches.first()
    assertEquals(HistoryRepository.MAX_ENTRIES, relues.size)
    // Ce sont les cinquante dernières qui restent, la 59e en tête et la 10e en queue.
    assertEquals("Arrêt 59", relues.first().to.name)
    assertEquals("Arrêt 10", relues.last().to.name)
  }

  @Test
  fun `historique desactive, rien n est ecrit`() = runBlocking {
    preferences.setHistoryEnabled(false)
    val repository = repository()

    // Un succès, pas un échec : l'usager a demandé qu'on ne garde rien, ce n'est pas une anomalie.
    assertTrue(repository.record(query("Nation")) is Outcome.Success)

    assertEquals(0, database.rowCount("search_history"))
    assertTrue(repository.recentSearches.first().isEmpty())
  }

  @Test
  fun `la bascule ne vaut que pour l avenir, les entrees deja la restent`() = runBlocking {
    val repository = repository()
    repository.record(query("Nation"))
    preferences.setHistoryEnabled(false)
    repository.record(query("Bastille"))

    // Désactiver l'historique n'efface pas : c'est le bouton « Tout effacer » qui le fait.
    assertEquals(listOf("Nation"), repository.recentSearches.first().map { it.to.name })
  }

  @Test
  fun `une entree s efface toute seule`() = runBlocking {
    val repository = repository()
    repository.record(query("Nation"))
    now = now.plusSeconds(60)
    repository.record(query("Bastille"))

    val aEffacer = repository.recentSearches.first().first { it.to.name == "Nation" }
    assertTrue(repository.delete(aEffacer.id) is Outcome.Success)

    assertEquals(listOf("Bastille"), repository.recentSearches.first().map { it.to.name })
  }

  @Test
  fun `tout effacer ne laisse rien dans la table`() = runBlocking {
    val repository = repository()
    repeat(5) { rang ->
      now = START.plusSeconds(rang.toLong())
      repository.record(query("Arrêt $rang"))
    }

    assertTrue(repository.clear() is Outcome.Success)

    assertEquals(0, database.rowCount("search_history"))
    assertTrue(repository.recentSearches.first().isEmpty())
  }

  private companion object {
    val START: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}
