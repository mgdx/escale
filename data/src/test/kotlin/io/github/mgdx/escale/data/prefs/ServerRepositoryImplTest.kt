package io.github.mgdx.escale.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.MotisClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRepositoryImplTest {

  @get:Rule
  val folder = TemporaryFolder()

  private lateinit var scope: CoroutineScope
  private lateinit var dataStore: DataStore<Preferences>

  @Before
  fun setUp() {
    // DataStore écrit réellement sur disque : il lui faut un vrai répartiteur, pas du temps virtuel.
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    dataStore = PreferenceDataStoreFactory.create(scope = scope) {
      folder.newFile("escale.preferences_pb")
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  private fun repository(engine: MockEngine = MockEngine { respondError(HttpStatusCode.NotFound) }) =
    ServerRepositoryImpl(dataStore, MotisClient(versionName = "1.0.0", engine = engine))

  @Test
  fun `sans rien d enregistre le serveur courant est l instance publique`() = runBlocking {
    val current = repository().current.first()
    assertEquals(ServerUrl.DEFAULT_BASE_URL, current.baseUrl)
  }

  @Test
  fun `un serveur enregistre devient le serveur courant`() = runBlocking {
    val repository = repository()
    val custom = ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis.exemple.org")
    assertTrue(repository.save(custom) is Outcome.Success)
    assertEquals(custom, repository.current.first())
  }

  @Test
  fun `les serveurs deja utilises sont conserves, le plus recent en tete`() = runBlocking {
    val repository = repository()
    val premier = ServerConfig(baseUrl = "https://un.exemple.org", label = "un")
    val second = ServerConfig(baseUrl = "https://deux.exemple.org", label = "deux")
    repository.save(premier)
    repository.save(second)
    assertEquals(listOf(second, premier), repository.knownServers.first())
  }

  @Test
  fun `enregistrer deux fois le meme serveur ne le duplique pas`() = runBlocking {
    val repository = repository()
    val serveur = ServerConfig(baseUrl = "https://un.exemple.org", label = "un")
    repository.save(serveur)
    repository.save(serveur)
    assertEquals(1, repository.knownServers.first().size)
  }

  @Test
  fun `oublier un serveur le retire de la liste`() = runBlocking {
    val repository = repository()
    repository.save(ServerConfig(baseUrl = "https://un.exemple.org", label = "un"))
    repository.save(ServerConfig(baseUrl = "https://deux.exemple.org", label = "deux"))
    repository.forget("https://un.exemple.org")
    assertEquals(
      listOf("https://deux.exemple.org"),
      repository.knownServers.first().map { it.baseUrl },
    )
  }

  @Test
  fun `retablir le serveur par defaut remet l instance publique`() = runBlocking {
    val repository = repository()
    repository.save(ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis"))
    repository.resetToDefault()
    assertEquals(ServerUrl.DEFAULT_BASE_URL, repository.current.first().baseUrl)
  }

  @Test
  fun `le test de connexion distingue les trois etapes`() = runBlocking {
    val engine = MockEngine { request ->
      when {
        request.url.encodedPath.endsWith("/api/v1/health") ->
          respond(
            "{\"rt\":true,\"gbfs\":false}",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
          )

        // Serveur trop ancien : le point d'entrée v6 n'existe pas.
        request.url.encodedPath.endsWith("/api/v6/map/stops") -> respondError(HttpStatusCode.NotFound)

        else -> respondError(HttpStatusCode.NotFound)
      }
    }
    val check = repository(engine).test("motis.exemple.org")
    val value = (check as Outcome.Success).value
    assertTrue(value.reachable)
    assertEquals(false, value.apiCompatible)
    assertEquals(false, value.tilesAvailable)
    assertEquals(true, value.health?.realtime)
  }

  @Test
  fun `une URL inexploitable ne declenche aucune requete`() = runBlocking {
    var calls = 0
    val engine = MockEngine {
      calls++
      respondError(HttpStatusCode.NotFound)
    }
    assertTrue(repository(engine).test("ftp://exemple.org") is Outcome.Failure)
    assertEquals(0, calls)
  }
}
