package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.db.EscaleDatabase
import io.github.mgdx.escale.data.db.address
import io.github.mgdx.escale.data.db.inMemoryDatabase
import io.github.mgdx.escale.data.db.stopLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryImplTest {

  private lateinit var database: EscaleDatabase
  private lateinit var repository: FavoritesRepositoryImpl

  @Before
  fun setUp() {
    database = inMemoryDatabase()
    repository = FavoritesRepositoryImpl(database) { NOW }
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `domicile et travail sont vides tant qu ils ne sont pas renseignes`() = runBlocking {
    // La règle de SPEC.md § 5.5 en dépend : sans domicile, aucune puce ne lui correspond, et
    // l'application ne le réclame jamais d'elle-même.
    assertNull(repository.home.first())
    assertNull(repository.work.first())
  }

  @Test
  fun `un domicile enregistre se relit, un domicile supprime redevient absent`() = runBlocking {
    val maison = address("12 rue des Lilas")
    assertTrue(repository.setHome(maison) is Outcome.Success)
    assertEquals(maison, repository.home.first())
    // Le travail n'a pas été touché : les deux emplacements sont indépendants.
    assertNull(repository.work.first())

    assertTrue(repository.setHome(null) is Outcome.Success)
    assertNull(repository.home.first())
  }

  @Test
  fun `un domicile au libelle vide reste un domicile renseigne`() = runBlocking {
    // « Non renseigné » se lit à l'absence de ligne, jamais à une valeur convenue : un nom vide
    // est un enregistrement légitime, et il ne doit pas être confondu avec l'absence.
    val sansNom = address(name = "")
    repository.setHome(sansNom)
    assertEquals(sansNom, repository.home.first())
  }

  @Test
  fun `le travail se remplace sans creer de doublon`() = runBlocking {
    repository.setWork(address("Bureau"))
    repository.setWork(address("Nouveau bureau"))
    assertEquals("Nouveau bureau", repository.work.first()?.name)
  }

  @Test
  fun `un arret favori conserve ses coordonnees a cote de son identifiant`() = runBlocking {
    // SPEC.md § 5.6.1 : un identifiant que le nouveau serveur ne reconnaît plus laisse un favori
    // affichable, jamais supprimé automatiquement. Sans coordonnées, il ne resterait rien.
    val arret = Stop(
      id = "de:06:1234",
      name = "Gare de Lyon",
      coordinates = LatLon(48.8443, 2.3735),
      modes = listOf(TransitMode.SUBWAY, TransitMode.REGIONAL_RAIL),
    )
    assertTrue(repository.addStop(arret) is Outcome.Success)

    val releve = repository.stops.first().single()
    assertEquals("de:06:1234", releve.id)
    assertEquals(48.8443, releve.coordinates.lat, 0.0)
    assertEquals(2.3735, releve.coordinates.lon, 0.0)
    assertEquals(listOf(TransitMode.SUBWAY, TransitMode.REGIONAL_RAIL), releve.modes)
    // Les lignes desservies ne sont pas persistées : elles se rechargent à l'appui.
    assertTrue(releve.lines.isEmpty())
  }

  @Test
  fun `un arret enregistre deux fois ne compte qu une fois`() = runBlocking {
    val arret = Stop(id = "de:06:1234", name = "Gare de Lyon", coordinates = LatLon(48.8443, 2.3735))
    repository.addStop(arret)
    repository.addStop(arret.copy(name = "Paris Gare de Lyon"))
    assertEquals(listOf("Paris Gare de Lyon"), repository.stops.first().map { it.name })
  }

  @Test
  fun `un arret favori se supprime par son identifiant`() = runBlocking {
    repository.addStop(Stop(id = "de:06:1234", name = "Gare de Lyon", coordinates = LatLon(48.8443, 2.3735)))
    assertTrue(repository.removeStop("de:06:1234") is Outcome.Success)
    assertTrue(repository.stops.first().isEmpty())
  }

  @Test
  fun `un lieu favori se relit tel qu il a ete enregistre`() = runBlocking {
    val lieu = stopLocation("de:06:9999", "Châtelet")
    assertTrue(repository.addPlace(lieu, label = "Chez Maman") is Outcome.Success)

    val releve = repository.places.first().single()
    // Le libellé de l'usager n'écrase pas le nom du serveur : le repli de SPEC.md § 5.6.1 en dépend.
    assertEquals(lieu, releve)
    assertEquals(PlaceKind.STOP, releve.kind)
  }

  @Test
  fun `un lieu favori se supprime, une adresse comme un arret`() = runBlocking {
    val adresse = address("12 rue des Lilas")
    val arret = stopLocation("de:06:9999", "Châtelet")
    repository.addPlace(adresse, label = null)
    repository.addPlace(arret, label = null)

    assertTrue(repository.removePlace(adresse) is Outcome.Success)
    assertEquals(listOf(arret), repository.places.first())

    assertTrue(repository.removePlace(arret) is Outcome.Success)
    assertTrue(repository.places.first().isEmpty())
  }

  @Test
  fun `un trajet favori rend son identifiant et se relit entierement`() = runBlocking {
    val depart = address("12 rue des Lilas")
    val arrivee = stopLocation("de:06:9999", "Châtelet")
    val outcome = repository.addJourney(depart, arrivee, JourneyCategory.BIKE, label = "Boulot")
    assertTrue(outcome is Outcome.Success)

    val favori = repository.journeys.first().single()
    assertEquals((outcome as Outcome.Success).value, favori.id)
    assertEquals("Boulot", favori.label)
    assertEquals(depart, favori.from)
    assertEquals(arrivee, favori.to)
    assertEquals(JourneyCategory.BIKE, favori.category)
    assertEquals(NOW, favori.createdAt)
  }

  @Test
  fun `un trajet favori supprime disparait de la liste`() = runBlocking {
    val id = (repository.addJourney(address("A"), address("B"), JourneyCategory.TRANSIT, null) as Outcome.Success)
      .value
    assertTrue(repository.removeJourney(id) is Outcome.Success)
    assertTrue(repository.journeys.first().isEmpty())
  }

  private companion object {
    val NOW: Instant = Instant.parse("2026-03-01T08:10:00Z")
  }
}
