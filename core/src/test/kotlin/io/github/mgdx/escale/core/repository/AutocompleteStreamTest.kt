package io.github.mgdx.escale.core.repository

import app.cash.turbine.test
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les trois règles de sobriété de SPEC.md § 7.1 sont vérifiées ici, en temps virtuel : un test qui
 * attendrait vraiment 350 ms serait lent et instable.
 */
class AutocompleteStreamTest {

  private val paris = LatLon(48.8566, 2.3522)

  private fun location(name: String) = Location(
    id = null,
    name = name,
    description = null,
    coordinates = paris,
    kind = PlaceKind.ADDRESS,
  )

  /** Dépôt en trompe-l'œil : il note ce qu'on lui demande et ce qu'on lui annule. */
  private class FakeGeocodeRepository(
    private val answerDelayMillis: Long = 0L,
    private val outcome: (String) -> Outcome<List<Location>>,
  ) : GeocodeRepository {
    val requestedTexts = mutableListOf<String>()
    var cancelledRequests = 0
      private set

    override suspend fun autocomplete(
      text: String,
      bias: LatLon?,
      language: String?,
      limit: Int,
    ): Outcome<List<Location>> {
      requestedTexts += text
      try {
        delay(answerDelayMillis)
      } catch (cancellation: CancellationException) {
        cancelledRequests++
        throw cancellation
      }
      return outcome(text)
    }

    override suspend fun reverseGeocodeAddress(point: LatLon, language: String?): Outcome<Location?> =
      Outcome.Success(null)

    override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> = Outcome.Success(null)

    override suspend fun clearGeocodeCache(): Outcome<Unit> = Outcome.Success(Unit)
  }

  private fun typing(vararg keystrokes: Pair<Long, String>): Flow<AutocompleteQuery> = flow {
    keystrokes.forEach { (pause, text) ->
      delay(pause)
      emit(AutocompleteQuery(text, bias = paris, language = "fr"))
    }
  }

  @Test
  fun `en deca de trois caracteres, aucune requete n est emise`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Success(emptyList()) }
    repository.autocompleteStream(flowOf(AutocompleteQuery("Ga"))).test {
      assertEquals(AutocompleteState.Idle, awaitItem())
      awaitComplete()
    }
    assertEquals(emptyList<String>(), repository.requestedTexts)
  }

  @Test
  fun `une rafale de frappes ne produit qu une requete, celle de la derniere saisie`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Success(listOf(location(it))) }
    val keystrokes = typing(0L to "G", 50L to "Ga", 50L to "Gar", 50L to "Gare")
    repository.autocompleteStream(keystrokes).test {
      assertEquals(AutocompleteState.Idle, awaitItem())
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare"))), awaitItem())
      awaitComplete()
    }
    assertEquals(listOf("Gare"), repository.requestedTexts)
  }

  @Test
  fun `deux saisies espacees de plus de 350 ms produisent deux requetes`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Success(listOf(location(it))) }
    val keystrokes = typing(0L to "Gare", 400L to "Gare de Lyon")
    repository.autocompleteStream(keystrokes).test {
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare"))), awaitItem())
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare de Lyon"))), awaitItem())
      awaitComplete()
    }
    assertEquals(listOf("Gare", "Gare de Lyon"), repository.requestedTexts)
  }

  @Test
  fun `une frappe pendant une requete en vol annule cette requete`() = runTest {
    val repository = FakeGeocodeRepository(answerDelayMillis = 1_000L) {
      Outcome.Success(listOf(location(it)))
    }
    // La première requête part après l'anti-rebond, la seconde frappe arrive alors qu'elle attend.
    val keystrokes = typing(0L to "Gare", 500L to "Gare de Lyon")
    repository.autocompleteStream(keystrokes).test {
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare de Lyon"))), awaitItem())
      awaitComplete()
    }
    assertEquals(listOf("Gare", "Gare de Lyon"), repository.requestedTexts)
    assertEquals(1, repository.cancelledRequests)
  }

  @Test
  fun `vider le champ ramene a l etat vide sans attendre l anti-rebond`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Success(listOf(location(it))) }
    val keystrokes = typing(0L to "Gare", 400L to "")
    repository.autocompleteStream(keystrokes).test {
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare"))), awaitItem())
      assertEquals(AutocompleteState.Idle, awaitItem())
      awaitComplete()
    }
    assertEquals(listOf("Gare"), repository.requestedTexts)
  }

  @Test
  fun `un echec du serveur devient un etat d erreur, jamais une exception`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Failure(EscaleError.NoNetwork) }
    repository.autocompleteStream(flowOf(AutocompleteQuery("Gare"))).test {
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Failed(EscaleError.NoNetwork), awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `les espaces autour de la saisie ne declenchent pas deux requetes`() = runTest {
    val repository = FakeGeocodeRepository { Outcome.Success(listOf(location(it))) }
    val keystrokes = typing(0L to "Gare", 400L to "Gare ")
    repository.autocompleteStream(keystrokes).test {
      assertEquals(AutocompleteState.Loading, awaitItem())
      assertEquals(AutocompleteState.Suggestions(listOf(location("Gare"))), awaitItem())
      awaitComplete()
    }
    assertEquals(listOf("Gare"), repository.requestedTexts)
  }
}
