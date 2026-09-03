package io.github.mgdx.escale.data.repository

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

  /** Une recherche : un départ, une arrivée, une heure — ce que `record` demande, et rien de plus. */
  private suspend fun HistoryRepositoryImpl.record(to: String, time: TimeChoice = TimeChoice.Now) =
    record(from = address("12 rue des Lilas"), to = stopLocation("de:06:$to", to), time = time)

  @Test
  fun `une recherche enregistree se relit, la plus recente d abord`() = runBlocking {
    val repository = repository()
    assertTrue(repository.record("Nation") is Outcome.Success)
    now = now.plusSeconds(60)
    repository.record("Bastille")

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
      repository.record("Arrêt $rang")
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
    assertTrue(repository.record("Nation") is Outcome.Success)

    assertEquals(0, database.rowCount("search_history"))
    assertTrue(repository.recentSearches.first().isEmpty())
  }

  @Test
  fun `la bascule ne vaut que pour l avenir, les entrees deja la restent`() = runBlocking {
    val repository = repository()
    repository.record("Nation")
    preferences.setHistoryEnabled(false)
    repository.record("Bastille")

    // Désactiver l'historique n'efface pas : c'est le bouton « Tout effacer » qui le fait.
    assertEquals(listOf("Nation"), repository.recentSearches.first().map { it.to.name })
  }

  @Test
  fun `une entree s efface toute seule`() = runBlocking {
    val repository = repository()
    repository.record("Nation")
    now = now.plusSeconds(60)
    repository.record("Bastille")

    val aEffacer = repository.recentSearches.first().first { it.to.name == "Nation" }
    assertTrue(repository.delete(aEffacer.id) is Outcome.Success)

    assertEquals(listOf("Bastille"), repository.recentSearches.first().map { it.to.name })
  }

  @Test
  fun `l heure demandee est enregistree avec la recherche, et relue telle quelle`() = runBlocking {
    val repository = repository()
    val avant = Instant.parse("2026-03-02T09:00:00Z")
    repository.record("Nation", TimeChoice.ArriveBy(avant))
    now = now.plusSeconds(60)
    repository.record("Bastille", TimeChoice.DepartAt(avant))
    now = now.plusSeconds(60)
    repository.record("Opéra")

    val relues = repository.recentSearches.first()
    // SPEC.md § 5.1 : la puce relance la recherche. Sans l'heure, elle en lancerait une autre.
    assertEquals(TimeChoice.Now, relues[0].time)
    assertEquals(TimeChoice.DepartAt(avant), relues[1].time)
    assertEquals(TimeChoice.ArriveBy(avant), relues[2].time)
  }

  @Test
  fun `tout effacer ne laisse rien dans la table`() = runBlocking {
    val repository = repository()
    repeat(5) { rang ->
      now = START.plusSeconds(rang.toLong())
      repository.record("Arrêt $rang")
    }

    assertTrue(repository.clear() is Outcome.Success)

    assertEquals(0, database.rowCount("search_history"))
    assertTrue(repository.recentSearches.first().isEmpty())
  }

  private companion object {
    val START: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }

  @Test
  fun `la meme paire cherchee deux fois n occupe qu une ligne, la plus recente`() = runBlocking {
    val repository = repository()
    val matin = TimeChoice.DepartAt(Instant.parse("2026-03-02T07:00:00Z"))
    repository.record("Nation", matin)
    now = now.plusSeconds(3600)
    val soir = TimeChoice.ArriveBy(Instant.parse("2026-03-02T18:00:00Z"))
    repository.record("Nation", soir)

    // Une ligne, pas deux : les cinquante entrées sont cinquante trajets, pas cinquante lignes.
    assertEquals(1, database.rowCount("search_history"))
    val relue = repository.recentSearches.first().single()
    // C'est la dernière qui fait foi, avec son horodatage et son heure demandée.
    assertEquals(now, relue.searchedAt)
    assertEquals(soir, relue.time)
  }

  @Test
  fun `une paire rejouee remonte en tete des dernieres recherches`() = runBlocking {
    val repository = repository()
    repository.record("Nation")
    now = now.plusSeconds(60)
    repository.record("Bastille")
    now = now.plusSeconds(60)
    repository.record("Nation")

    assertEquals(listOf("Nation", "Bastille"), repository.recentSearches.first().map { it.to.name })
  }

  @Test
  fun `le sens compte, un retour n est pas la meme recherche qu un aller`() = runBlocking {
    val repository = repository()
    val lilas = address("12 rue des Lilas")
    val nation = stopLocation("de:06:Nation", "Nation")
    repository.record(from = lilas, to = nation, time = TimeChoice.Now)
    repository.record(from = nation, to = lilas, time = TimeChoice.Now)

    assertEquals(2, database.rowCount("search_history"))
  }

  @Test
  fun `deux homonymes a des points differents restent deux recherches`() = runBlocking {
    val repository = repository()
    val arrivee = address("Gare", lat = 50.63, lon = 3.07)
    repository.record(from = address("Mairie", lat = 48.85, lon = 2.35), to = arrivee, time = TimeChoice.Now)
    repository.record(from = address("Mairie", lat = 45.75, lon = 4.85), to = arrivee, time = TimeChoice.Now)

    // Aucun `stopId` de part et d'autre : c'est le nom **et** les coordonnées qui départagent.
    assertEquals(2, database.rowCount("search_history"))
  }
}
