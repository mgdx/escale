package io.github.mgdx.escale.data.db

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.WatchSchedule
import io.github.mgdx.escale.core.model.WatchedJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Le mapping entité → domaine, dans les deux sens, comme pour les DTO du réseau. */
class EntityMappingTest {

  @Test
  fun `une adresse fait l aller-retour sans rien perdre`() {
    val adresse = address("12 rue des Lilas")
    assertEquals(adresse, adresse.toColumns().toLocation())
  }

  @Test
  fun `un arret fait l aller-retour avec son identifiant, ses modes et ses coordonnees`() {
    val arret = stopLocation("de:06:1234", "Gare de Lyon")
    val colonnes = arret.toColumns()

    // L'identifiant et les coordonnées coexistent : SPEC.md § 5.6.1 interdit de choisir.
    assertEquals("de:06:1234", colonnes.stopId)
    assertEquals(48.8443, colonnes.lat, 0.0)
    assertEquals(arret, colonnes.toLocation())
  }

  @Test
  fun `un mode inconnu est ignore, le favori reste lisible`() {
    // Un serveur plus récent, ou une base écrite par une version future : perdre l'accès à ses
    // favoris parce qu'un nom d'énumération a bougé serait la pire réponse possible.
    val colonnes = stopLocation("de:06:1234", "Gare de Lyon")
      .toColumns()
      .copy(servedModes = "SUBWAY,HYPERLOOP,REGIONAL_RAIL")

    assertEquals(listOf(TransitMode.SUBWAY, TransitMode.REGIONAL_RAIL), colonnes.toLocation().servedModes)
  }

  @Test
  fun `un genre de lieu illisible se relit d apres l identifiant`() {
    // docs/architecture.md § 11.3 : un arrêt relu comme une adresse partirait en coordonnées et ne
    // rendrait aucun résultat. L'identifiant tranche mieux qu'une valeur par défaut arbitraire.
    val arret = stopLocation("de:06:1234", "Gare de Lyon").toColumns().copy(kind = "TELEPORTATION")
    assertEquals(PlaceKind.STOP, arret.toLocation().kind)

    val adresse = address("12 rue des Lilas").toColumns().copy(kind = "TELEPORTATION")
    assertEquals(PlaceKind.ADDRESS, adresse.toLocation().kind)
  }

  @Test
  fun `un lieu sans mode desservi se relit avec une liste vide`() {
    assertTrue(address("12 rue des Lilas").toColumns().toLocation().servedModes.isEmpty())
  }

  @Test
  fun `un arret favori fait l aller-retour`() {
    val arret = Stop(
      id = "de:06:1234",
      name = "Gare de Lyon",
      coordinates = LatLon(48.8443, 2.3735),
      modes = listOf(TransitMode.SUBWAY),
    )
    val instant = Instant.parse("2026-03-01T08:10:00Z")

    val releve = arret.toEntity(instant).toStop()

    assertEquals(arret, releve)
    assertEquals(instant.toEpochMilli(), arret.toEntity(instant).createdAt)
  }

  @Test
  fun `une surveillance recurrente fait l aller-retour`() {
    val surveillance = WatchedJourney(
      journeyId = 7,
      schedule = WatchSchedule(
        departureTime = LocalTime.of(8, 10),
        days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
      ),
      itineraryId = "itin-42",
      itineraryCapturedAt = Instant.parse("2026-03-01T07:00:00Z"),
      lastViewedAt = Instant.parse("2026-03-01T07:30:00Z"),
      createdAt = Instant.parse("2026-02-01T12:00:00Z"),
    )

    assertEquals(surveillance, surveillance.toEntity().toWatchedJourney())
    // Les jours sont rangés dans l'ordre de la semaine : deux ensembles égaux s'écrivent pareil.
    assertEquals("MONDAY,FRIDAY", surveillance.toEntity().daysOfWeek)
  }

  @Test
  fun `une surveillance a date unique fait l aller-retour`() {
    val surveillance = WatchedJourney(
      journeyId = 7,
      schedule = WatchSchedule(departureTime = LocalTime.of(5, 45), date = LocalDate.of(2026, 4, 12)),
      createdAt = Instant.parse("2026-02-01T12:00:00Z"),
    )

    val entite = surveillance.toEntity()
    assertEquals("", entite.daysOfWeek)
    assertEquals(LocalDate.of(2026, 4, 12).toEpochDay(), entite.date)
    assertEquals(surveillance, entite.toWatchedJourney())
  }

  @Test
  fun `l heure de depart est enregistree a la minute`() {
    val surveillance = WatchedJourney(
      journeyId = 7,
      schedule = WatchSchedule(
        departureTime = LocalTime.of(8, 10, 30),
        days = setOf(DayOfWeek.MONDAY),
      ),
      createdAt = Instant.parse("2026-02-01T12:00:00Z"),
    )

    // Les secondes d'une heure de départ habituelle n'ont pas de sens : elles sont tronquées, pas
    // conservées à moitié.
    assertEquals(8 * 60 + 10, surveillance.toEntity().departureMinuteOfDay)
    assertEquals(LocalTime.of(8, 10), surveillance.toEntity().toWatchedJourney().schedule.departureTime)
  }

  @Test
  fun `une surveillance sans itineraire retenu se relit sans rien inventer`() {
    val surveillance = WatchedJourney(
      journeyId = 7,
      schedule = WatchSchedule(departureTime = LocalTime.MIDNIGHT, days = setOf(DayOfWeek.SUNDAY)),
      createdAt = Instant.parse("2026-02-01T12:00:00Z"),
    )

    val releve = surveillance.toEntity().toWatchedJourney()
    assertNull(releve.itineraryId)
    assertNull(releve.itineraryCapturedAt)
    assertNull(releve.lastViewedAt)
  }
}
