package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerTestProgress
import io.github.mgdx.escale.core.model.ServerTestStep
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.net.GeocodeApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le dépôt de géocodage vu depuis l'application : serveur courant, verrous de sobriété, purge. */
class GeocodeRepositoryImplTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  private fun fixture(name: String): String =
    checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
      "fixture manquante : $name"
    }.readBytes().decodeToString()

  /** Serveur courant en dur ; le reste du contrat ne sert pas au géocodage. */
  private class FakeServerRepository(baseUrl: String) : ServerRepository {
    private val config = ServerConfig(baseUrl = baseUrl, label = "Test")
    override val current: Flow<ServerConfig> = flowOf(config)
    override val knownServers: Flow<List<ServerConfig>> = flowOf(listOf(config))
    override suspend fun save(config: ServerConfig): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun forget(baseUrl: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun resetToDefault(): Outcome<Unit> = Outcome.Success(Unit)
    override fun test(baseUrl: String): Flow<ServerTestProgress> = flowOf(
      ServerTestProgress.Finished(ServerTestStep.REACHABLE, passed = true),
      ServerTestProgress.Finished(ServerTestStep.API_VERSION, passed = true),
      ServerTestProgress.Finished(ServerTestStep.TILES, passed = true),
    )
  }

  private fun repository(
    fixtureName: String,
    baseUrl: String = "https://exemple.org",
    requests: MutableList<HttpRequestData> = mutableListOf(),
  ): GeocodeRepositoryImpl {
    val engine = MockEngine { request ->
      requests += request
      respond(fixture(fixtureName), HttpStatusCode.OK, jsonHeaders)
    }
    return GeocodeRepositoryImpl(
      api = GeocodeApi(versionName = "1.0.0", engine = engine),
      serverRepository = FakeServerRepository(baseUrl),
      ioDispatcher = Dispatchers.Unconfined,
    )
  }

  @Test
  fun `en deca de trois caracteres, aucune requete ne part`() = runTest {
    // Deuxième verrou de SPEC.md § 7.1 : même un appel direct au dépôt ne réveille pas le serveur.
    val requests = mutableListOf<HttpRequestData>()
    val outcome = repository("geocode_rue_de_rivoli.json", requests = requests).autocomplete("Ru")
    assertEquals(emptyList<Any>(), (outcome as Outcome.Success).value)
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `les espaces autour de la saisie ne comptent pas et ne sont pas envoyes`() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val repository = repository("geocode_rue_de_rivoli.json", requests = requests)
    assertEquals(emptyList<Any>(), (repository.autocomplete("  Ru  ") as Outcome.Success).value)
    repository.autocomplete("  Rue de Rivoli  ")
    assertEquals("Rue de Rivoli", requests.single().url.parameters["text"])
  }

  @Test
  fun `la requete part vers le serveur courant`() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val repository = repository(
      "geocode_rue_de_rivoli.json",
      baseUrl = "https://motis.exemple.org",
      requests = requests,
    )
    repository.autocomplete("Rue de Rivoli")
    assertEquals("motis.exemple.org", requests.single().url.host)
  }

  @Test
  fun `le geocodage inverse ne rend que le resultat le plus pertinent`() = runTest {
    val outcome = repository("reverse_geocode_bastille.json").reverseGeocode(LatLon(48.8532, 2.3692))
    assertEquals("Colonne de Juillet", (outcome as Outcome.Success).value?.name)
  }

  @Test
  fun `un endroit que le serveur ne sait pas nommer rend null, pas une erreur`() = runTest {
    val outcome = repository("geocode_no_result.json").reverseGeocode(LatLon(0.0, 0.0))
    assertNull((outcome as Outcome.Success).value)
  }

  @Test
  fun `vider le cache de geocodage reussit meme sans cache disque`() = runTest {
    // SPEC.md § 5.6.1 : le changement de serveur appelle cette purge, elle ne doit jamais échouer
    // bruyamment.
    val outcome = repository("geocode_no_result.json").clearGeocodeCache()
    assertTrue(outcome is Outcome.Success)
  }

  // Hote introuvable et absence de reseau sont deux pannes distinctes : les confondre enverrait
  // l'usager chercher une coupure inexistante apres une faute de frappe dans l'URL (SPEC.md § 8).
  @Test
  fun `un hote introuvable remonte en erreur typee`() = runTest {
    assertEquals(
      EscaleError.HostNotFound,
      erreurDe { throw java.net.UnknownHostException("exemple.org") },
    )
  }

  @Test
  fun `une panne de reseau remonte en erreur typee`() = runTest {
    assertEquals(
      EscaleError.NoNetwork,
      erreurDe { throw java.net.NoRouteToHostException("exemple.org") },
    )
  }

  private suspend fun erreurDe(panne: () -> Nothing): EscaleError {
    val repository = GeocodeRepositoryImpl(
      api = GeocodeApi(versionName = "1.0.0", engine = MockEngine { panne() }),
      serverRepository = FakeServerRepository("https://exemple.org"),
      ioDispatcher = Dispatchers.Unconfined,
    )
    return (repository.autocomplete("Rue de Rivoli") as Outcome.Failure).error
  }
}
