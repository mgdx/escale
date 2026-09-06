package io.github.mgdx.escale.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mgdx.escale.core.model.CategoryOrder
import io.github.mgdx.escale.core.model.ClockFormat
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.ThemeChoice
import io.github.mgdx.escale.core.result.Outcome
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
import java.time.Duration

class PreferencesRepositoryImplTest {

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

  private fun repository() = PreferencesRepositoryImpl(dataStore)

  @Test
  fun `sans rien d enregistre, les preferences sont exactement celles du domaine`() = runBlocking {
    // La promesse du lot : un usager qui n'a rien réglé obtient le comportement d'avant, à
    // l'identique. Comparer aux constructeurs par défaut, et non à des valeurs recopiées, est la
    // seule façon de garantir qu'un changement de défaut ne passe pas inaperçu.
    assertEquals(SearchPreferences(), repository().searchPreferences.first())
    assertEquals(DisplayPreferences(), repository().displayPreferences.first())
  }

  @Test
  fun `les preferences de recherche se relisent apres ecriture`() = runBlocking {
    val repository = repository()
    val voulues = SearchPreferences(
      pedestrianSpeedMetersPerSecond = 0.8,
      pedestrianProfile = PedestrianProfile.WHEELCHAIR,
      cyclingSpeedMetersPerSecond = 6.1,
      elevationCosts = ElevationCosts.HIGH,
      additionalTransferTime = Duration.ofMinutes(5),
      maxTransfers = 2,
      requireBikeTransport = true,
      allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE, RentalFormFactor.SCOOTER_SEATED),
    )
    assertTrue(repository.updateSearchPreferences(voulues) is Outcome.Success)
    assertEquals(voulues, repository.searchPreferences.first())
    // Un second dépôt sur le même fichier : c'est bien le disque qui a été relu.
    assertEquals(voulues, repository().searchPreferences.first())
  }

  @Test
  fun `les preferences d affichage se relisent apres ecriture`() = runBlocking {
    val repository = repository()
    val voulues = DisplayPreferences(
      theme = ThemeChoice.DARK,
      clockFormat = ClockFormat.HOURS_12,
      categoryOrder = listOf(
        JourneyCategory.BIKE,
        JourneyCategory.WALK,
        JourneyCategory.TRANSIT,
        JourneyCategory.CAR,
      ),
      showStops = false,
      showRentals = false,
      showPointsOfInterest = false,
      historyEnabled = false,
    )
    assertTrue(repository.updateDisplayPreferences(voulues) is Outcome.Success)
    assertEquals(voulues, repository().displayPreferences.first())
  }

  @Test
  fun `aucune correspondance et sans limite sont deux reglages distincts`() = runBlocking {
    val repository = repository()
    repository.updateSearchPreferences(SearchPreferences(maxTransfers = 0))
    assertEquals(0, repository.searchPreferences.first().maxTransfers)
    repository.updateSearchPreferences(SearchPreferences(maxTransfers = null))
    assertEquals(null, repository.searchPreferences.first().maxTransfers)
  }

  @Test
  fun `un ensemble vide de types de vehicules signifie aucun filtre`() = runBlocking {
    val repository = repository()
    repository.updateSearchPreferences(SearchPreferences(allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE)))
    repository.updateSearchPreferences(SearchPreferences(allowedRentalFormFactors = emptySet()))
    assertEquals(emptySet<RentalFormFactor>(), repository.searchPreferences.first().allowedRentalFormFactors)
  }

  @Test
  fun `un type de vehicule qui n est plus propose est oublie a la relecture`() = runBlocking {
    // Réglage écrit par une version antérieure, du temps où la voiture et le vélo cargo étaient
    // proposés (SPEC.md § 5.2). Ce qui en reste est conservé ; le reste est oublié.
    dataStore.edit {
      it[stringSetPreferencesKey("search_rental_form_factors")] = setOf("BICYCLE", "CAR", "CARGO_BICYCLE")
    }
    assertEquals(
      setOf(RentalFormFactor.BICYCLE),
      repository().searchPreferences.first().allowedRentalFormFactors,
    )
    // Et un réglage qui ne nommait que des types disparus ne doit pas se relire comme « aucun
    // véhicule », ce qui viderait l'onglet Vélo de ses trajets partagés.
    dataStore.edit { it[stringSetPreferencesKey("search_rental_form_factors")] = setOf("CAR", "MOPED") }
    assertEquals(emptySet<RentalFormFactor>(), repository().searchPreferences.first().allowedRentalFormFactors)
  }

  @Test
  fun `une valeur illisible se relit comme la valeur par defaut`() = runBlocking {
    // Écrite par une version future, ou par un fichier abîmé : l'application démarre quand même.
    dataStore.edit { it[stringPreferencesKey("search_pedestrian_profile")] = "HOVERBOARD" }
    assertEquals(PedestrianProfile.FOOT, repository().searchPreferences.first().pedestrianProfile)
  }

  @Test
  fun `retablir les valeurs par defaut ne touche pas aux reglages des autres ecrans`() = runBlocking {
    val repository = repository()
    val autreEcran = stringPreferencesKey("server_current_base_url")
    dataStore.edit { it[autreEcran] = "https://motis.exemple.org" }
    repository.updateSearchPreferences(SearchPreferences(pedestrianProfile = PedestrianProfile.WHEELCHAIR))
    repository.updateDisplayPreferences(DisplayPreferences(theme = ThemeChoice.DARK))

    assertTrue(repository.resetToDefaults() is Outcome.Success)

    assertEquals(SearchPreferences(), repository.searchPreferences.first())
    assertEquals(DisplayPreferences(), repository.displayPreferences.first())
    // Le fichier DataStore est partagé : le serveur configuré doit avoir survécu.
    assertEquals("https://motis.exemple.org", dataStore.data.first()[autreEcran])
  }

  @Test
  fun `un ordre de categories illisible se relit complet`() = runBlocking {
    // Ce que laisserait une version future ou un fichier tronqué. Une catégorie manquante serait
    // un onglet inatteignable : la relecture complète toujours (SPEC.md § 5.2).
    dataStore.edit { it[stringPreferencesKey("display_category_order")] = "WALK,HYPERLOOP" }
    val relu = repository().displayPreferences.first().categoryOrder

    assertEquals(JourneyCategory.WALK, relu.first())
    assertEquals(CategoryOrder.DEFAULT.toSet(), relu.toSet())
    assertEquals(CategoryOrder.DEFAULT.size, relu.size)
  }
}
