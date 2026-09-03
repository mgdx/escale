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
import io.github.mgdx.escale.data.db.rowCount
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
  fun `un lieu favori garde le nom que l usager lui a donne`() = runBlocking {
    val lieu = stopLocation("de:06:9999", "Châtelet")
    val outcome = repository.addPlace(lieu, label = "Chez Maman")
    assertTrue(outcome is Outcome.Success)

    val releve = repository.places.first().single()
    assertEquals((outcome as Outcome.Success).value, releve.id)
    // SPEC.md § 5.5 parle de « lieux nommés » : le nom de l'usager se relit…
    assertEquals("Chez Maman", releve.label)
    assertEquals("Chez Maman", releve.displayName)
    // …sans écraser le nom du serveur, dont dépend le repli de SPEC.md § 5.6.1.
    assertEquals(lieu, releve.location)
    assertEquals(PlaceKind.STOP, releve.location.kind)
    assertEquals(NOW, releve.createdAt)
  }

  @Test
  fun `un lieu sans libelle s affiche sous le nom du serveur`() = runBlocking {
    repository.addPlace(address("12 rue des Lilas"), label = null)
    assertEquals("12 rue des Lilas", repository.places.first().single().displayName)
  }

  @Test
  fun `un lieu favori se supprime par son identifiant, sans emporter son homonyme`() = runBlocking {
    val lieu = stopLocation("de:06:9999", "Châtelet")
    val premier = repository.addPlace(lieu, label = "Le café") as Outcome.Success
    repository.addPlace(lieu, label = "L'appartement au-dessus")

    // Même nom, mêmes coordonnées, même identifiant d'arrêt : seul l'identifiant de la ligne les
    // distingue, et la première version les effaçait tous les deux d'un coup.
    assertTrue(repository.removePlace(premier.value) is Outcome.Success)

    assertEquals(listOf("L'appartement au-dessus"), repository.places.first().map { it.label })
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

  @Test
  fun `le meme trajet mis deux fois en favori ne fait qu une ligne`() = runBlocking {
    val depart = address("12 rue des Lilas")
    val arrivee = stopLocation("de:06:9999", "Châtelet")

    val premier = repository.addJourney(depart, arrivee, JourneyCategory.TRANSIT, label = "Boulot")
    val second = repository.addJourney(depart, arrivee, JourneyCategory.TRANSIT, label = null)

    // Le second appel rend l'identifiant du premier : l'appel est idempotent, pas en échec.
    assertTrue(second is Outcome.Success)
    assertEquals((premier as Outcome.Success).value, (second as Outcome.Success).value)
    assertEquals(1, database.rowCount("favorite_journeys"))
    // Le libellé du premier n'est pas écrasé par le second, qui n'apportait rien.
    assertEquals("Boulot", repository.journeys.first().single().label)
  }

  @Test
  fun `deux categories du meme couple restent deux favoris distincts`() = runBlocking {
    val depart = address("12 rue des Lilas")
    val arrivee = stopLocation("de:06:9999", "Châtelet")

    repository.addJourney(depart, arrivee, JourneyCategory.TRANSIT, label = null)
    repository.addJourney(depart, arrivee, JourneyCategory.BIKE, label = null)

    // La catégorie fait partie de la clé : « à vélo » et « en transports » ne sont pas le même trajet.
    assertEquals(2, database.rowCount("favorite_journeys"))
  }

  @Test
  fun `deux adresses homonymes a des points differents ne se confondent pas`() = runBlocking {
    val depart = address("Mairie", lat = 48.85, lon = 2.35)
    val autreDepart = address("Mairie", lat = 45.75, lon = 4.85)
    val arrivee = address("Gare")

    repository.addJourney(depart, arrivee, JourneyCategory.TRANSIT, label = null)
    repository.addJourney(autreDepart, arrivee, JourneyCategory.TRANSIT, label = null)

    // Aucun `stopId` de part et d'autre : c'est le nom **et** les coordonnées qui départagent, là
    // où un index incluant le `stopId` nul aurait laissé passer les deux.
    assertEquals(2, database.rowCount("favorite_journeys"))
  }
}
