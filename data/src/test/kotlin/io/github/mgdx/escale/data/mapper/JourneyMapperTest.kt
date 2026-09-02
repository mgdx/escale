package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.geo.PolylineDecoder
import io.github.mgdx.escale.core.model.DisruptionCause
import io.github.mgdx.escale.core.model.DisruptionEffect
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.WheelchairAccess
import io.github.mgdx.escale.data.PlanTestSupport
import io.github.mgdx.escale.data.dto.PlanResponseDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Mapping de `/api/v6/plan` vers le domaine, éprouvé sur des réponses réelles capturées depuis
 * `api.transitous.org` (SPEC.md § 10). Aucun test ne touche le réseau réel.
 */
class JourneyMapperTest {

  // --- Le piège des polylignes, SPEC.md § 4.3 ------------------------------------------------

  @Test
  fun `les polylignes v6 sont decodees en precision 6, pas 7`() {
    // La fixture est un trajet à pied réel entre deux points de Berlin, demandé avec
    // detailedLegs=true. Le serveur y annonce lui-même la précision de son encodage.
    val geometry = checkNotNull(walkFixtureDto().direct.single().legs.single().legGeometry)
    assertEquals(PolylineDecoder.PRECISION_V6, geometry.precision)

    // Décodée avec la précision que le serveur annonce, la polyligne tombe sur Berlin.
    val right = PolylineDecoder.decode(geometry.points, geometry.precision)
    assertEquals(52.525094, right.first().lat, TOLERANCE)
    assertEquals(13.369404, right.first().lon, TOLERANCE)

    // Décodée en précision 7 — celle des points d'entrée /api/v1 — la même polyligne atterrirait
    // au large de l'Afrique de l'Ouest. C'est le bug que SPEC.md § 4.3 signale nommément.
    val wrong = PolylineDecoder.decode(geometry.points, PolylineDecoder.PRECISION_V1)
    assertEquals(5.2525094, wrong.first().lat, TOLERANCE)
    assertEquals(1.3369404, wrong.first().lon, TOLERANCE)
  }

  @Test
  fun `le mapping passe au decodeur la precision annoncee par la reponse`() = runTest {
    // Le mapping ne code pas « 6 » en dur : c'est le champ `precision` du schéma EncodedPolyline
    // qui est transmis, ce qui restera juste si MOTIS en change un jour.
    val geometry = checkNotNull(walkFixtureDto().direct.single().legs.single().legGeometry)
    val page = PlanTestSupport.page("plan_walk_detailed.json", backgroundScope, JourneyCategory.WALK)
    val mapped = page.direct.single().legs.single().geometry
    assertEquals(PolylineDecoder.decode(geometry.points, geometry.precision), mapped)
    assertEquals(88, mapped.size)
  }

  @Test
  fun `sans detailedLegs le trace est vide et ce n est pas une erreur`() = runTest {
    val page = PlanTestSupport.page("plan_car_direct.json", backgroundScope, JourneyCategory.CAR)
    assertTrue(page.direct.single().legs.single().geometry.isEmpty())
  }

  // --- Transport en commun -------------------------------------------------------------------

  @Test
  fun `un trajet avec correspondance rend ses portions dans l ordre et son compte de correspondances`() = runTest {
    val page = PlanTestSupport.page("plan_transit_transfer.json", backgroundScope)
    val journey = page.journeys.single { it.transfers == 1 }
    assertEquals(
      listOf(JourneyLeg.Transit::class, JourneyLeg.Walk::class, JourneyLeg.Transit::class),
      journey.legs.map { it::class },
    )
    // Les trajets sans horaire arrivent dans `direct`, jamais mélangés aux autres.
    assertTrue(page.direct.isEmpty())
  }

  @Test
  fun `une portion en transport en commun porte tout ce que reclame l ecran de detail`() = runTest {
    val page = PlanTestSupport.page("plan_transit_transfer.json", backgroundScope)
    val leg = page.journeys.single { it.transfers == 1 }.legs.first() as JourneyLeg.Transit
    assertEquals(TransitMode.SUBURBAN, leg.mode)
    assertEquals("S2", leg.lineName)
    assertEquals("Altdorf (b Nürnberg)", leg.headsign)
    assertNotNull(leg.agencyName)
    assertNotNull(leg.tripId)
    // Quai, accessibilité et transport de vélos, tous exigés par SPEC.md § 5.3.
    assertEquals("3", leg.from.track)
    assertEquals(WheelchairAccess.NOT_ACCESSIBLE, leg.wheelchairAccessible)
    assertEquals(false, leg.bikesAllowed)
    // Arrêts intermédiaires avec leurs heures de passage.
    assertEquals(2, leg.intermediateStops.size)
    assertNotNull(leg.intermediateStops.first().departure)
    // Une portion en transport en commun ne porte pas de distance : l'API n'en donne pas.
    assertNull(leg.distanceMeters)
  }

  @Test
  fun `les couleurs de ligne recoivent le diese que l interface attend`() = runTest {
    val page = PlanTestSupport.page("plan_transit_transfer.json", backgroundScope)
    val colors = page.journeys
      .flatMap { it.legs }
      .filterIsInstance<JourneyLeg.Transit>()
      .mapNotNull { it.routeColor }
    assertTrue("aucune couleur de ligne dans la fixture", colors.isNotEmpty())
    // GTFS transmet `702082`, le domaine veut `#702082`.
    assertTrue(colors.all { it.startsWith("#") })
  }

  @Test
  fun `les heures theoriques et reelles sont toutes deux conservees`() = runTest {
    val page = PlanTestSupport.page("plan_transit_transfer.json", backgroundScope)
    val journey = page.journeys.first()
    // L'API ne donne pas d'horaire théorique au niveau de l'itinéraire : il vient des portions.
    assertEquals(journey.legs.first().scheduledStartTime, journey.scheduledStartTime)
    assertEquals(journey.legs.last().scheduledEndTime, journey.scheduledEndTime)
    // Sans cette paire, aucun retard ne serait calculable (SPEC.md § 5.2).
    assertTrue(journey.startTime >= Instant.EPOCH)
    assertTrue(journey.legs.all { it.scheduledStartTime <= it.scheduledEndTime })
  }

  @Test
  fun `une portion sans temps reel est reportee comme telle`() = runTest {
    val page = PlanTestSupport.page("plan_rental_direct.json", backgroundScope, JourneyCategory.BIKE)
    // Un trajet direct ne dépend d'aucune base horaire : rien à annoncer « à l'heure ».
    assertTrue(page.direct.flatMap { it.legs }.none { it.realTime })
    assertFalse(page.direct.flatMap { it.legs }.any { it.cancelled })
  }

  // --- Libre-service -------------------------------------------------------------------------

  @Test
  fun `un trajet en libre-service porte son systeme, son type de vehicule et ses stations`() = runTest {
    val page = PlanTestSupport.page("plan_rental_direct.json", backgroundScope, JourneyCategory.BIKE)
    val rentalLegs = page.direct.flatMap { it.legs }.filterIsInstance<JourneyLeg.Rental>()
    assertTrue("aucune portion en libre-service dans la fixture", rentalLegs.isNotEmpty())

    val bike = rentalLegs.first { it.rental?.formFactor == RentalFormFactor.BICYCLE }
    val rental = checkNotNull(bike.rental)
    assertTrue(rental.systemId.isNotEmpty())
    assertNotNull(rental.systemName)
    assertNotNull(rental.rentalUriAndroid)
    assertNotNull(rental.returnConstraint)
    // Une station renseignée signale un système avec bornes ; nulle, un véhicule en free-floating.
    assertNotNull(rental.fromStationName)

    // Vélo personnel et véhicule partagé cohabitent bien dans le même onglet (SPEC.md § 5.2).
    assertTrue(page.direct.flatMap { it.legs }.any { it is JourneyLeg.Bike })
    // Les trottinettes en font partie, elles aussi.
    assertTrue(rentalLegs.any { it.rental?.formFactor == RentalFormFactor.SCOOTER_STANDING })
  }

  // --- Trajets directs, perturbations, cas limites -------------------------------------------

  @Test
  fun `un trajet en voiture arrive par le champ direct et non par itineraries`() = runTest {
    val page = PlanTestSupport.page("plan_car_direct.json", backgroundScope, JourneyCategory.CAR)
    assertTrue(page.journeys.isEmpty())
    val leg = page.direct.single().legs.single()
    assertTrue(leg is JourneyLeg.Car)
    assertEquals(5245.0, checkNotNull(leg.distanceMeters), TOLERANCE)
  }

  @Test
  fun `les instructions pas-a-pas sont reprises avec leur denivele`() = runTest {
    val page = PlanTestSupport.page("plan_walk_detailed.json", backgroundScope, JourneyCategory.WALK)
    val walk = page.direct.single().legs.single() as JourneyLeg.Walk
    assertEquals(41, walk.steps.size)
    assertTrue(walk.steps.all { it.geometry.isNotEmpty() })
    assertNotNull(walk.steps.first().elevationUpMeters)
  }

  @Test
  fun `une perturbation reelle est reprise avec son titre, son lien et sa periode d impact`() = runTest {
    // Capture réelle sur api.transitous.org : deux trajets en tram portent le même message du
    // réseau, dont la période d'impact est à venir. C'est le cas courant — les alertes riches en
    // métadonnées sont rares, d'où la fixture enrichie ci-dessous.
    val page = PlanTestSupport.page("plan_with_alerts.json", backgroundScope)
    val disruption = page.journeys.flatMap { it.alerts }.distinct().single()
    assertEquals("Le 05 & 06/09 : Triathlon de Bordeaux !", disruption.headerText)
    assertTrue(disruption.descriptionText.isNotBlank())
    assertEquals("https://www.infotbm.com/fr/perturbations", disruption.url)
    // Le serveur ne publie pas toujours la gravité : l'absence se traduit en `UNKNOWN_SEVERITY`,
    // jamais en gravité inventée.
    assertEquals(DisruptionSeverity.UNKNOWN_SEVERITY, disruption.severity)
    assertEquals(DisruptionCause.UNKNOWN_CAUSE, disruption.cause)
    assertEquals(DisruptionEffect.OTHER_EFFECT, disruption.effect)
    assertEquals(Instant.parse("2026-09-05T06:00:00Z"), disruption.periods.single().start)
    assertEquals(Instant.parse("2026-09-06T18:00:00Z"), disruption.periods.single().end)
  }

  @Test
  fun `une perturbation est reprise avec sa gravite, sa cause et sa periode d impact`() = runTest {
    // Fixture **enrichie à la main** : aucune alerte réellement capturée ne portait à la fois
    // `severityLevel`, `cause` et `effect`, et ces trois champs doivent tout de même être mappés.
    val page = PlanTestSupport.page("plan_with_alerts_enriched.json", backgroundScope)
    val disruption = page.journeys.single().alerts.single()
    assertEquals("Umleitung der Linie S2", disruption.headerText)
    assertEquals(DisruptionSeverity.WARNING, disruption.severity)
    assertEquals(DisruptionCause.CONSTRUCTION, disruption.cause)
    assertEquals(DisruptionEffect.DETOUR, disruption.effect)
    assertEquals("https://www.vag.de/meldungen", disruption.url)
    // C'est la période d'impact qui est retenue, pas la période de communication.
    assertEquals(Instant.parse("2026-09-01T22:00:00Z"), disruption.periods.single().start)
    assertEquals(Instant.parse("2026-09-02T03:00:00Z"), disruption.periods.single().end)
  }

  @Test
  fun `une recherche sans resultat rend une page vide, pas une erreur`() = runTest {
    val page = PlanTestSupport.page("plan_empty.json", backgroundScope, JourneyCategory.WALK)
    assertTrue(page.isEmpty)
    assertNull(page.nextPageCursor)
  }

  @Test
  fun `les champs inconnus de la reponse sont ignores`() = runTest {
    // Non négociable : l'API MOTIS ajoute des champs sans préavis, à tous les niveaux.
    val page = PlanTestSupport.page("plan_unknown_fields.json", backgroundScope, JourneyCategory.CAR)
    assertEquals(1, page.direct.size)
    assertEquals(5245.0, checkNotNull(page.direct.single().legs.single().distanceMeters), TOLERANCE)
  }

  /** La fixture de marche, lue en DTO : c'est là que se trouve la précision annoncée. */
  private fun walkFixtureDto(): PlanResponseDto = fixtureJson.decodeFromString(
    PlanResponseDto.serializer(),
    PlanTestSupport.fixture("plan_walk_detailed.json"),
  )

  private companion object {
    const val TOLERANCE = 1e-6

    /** Même configuration que celle du `MotisClient` : les champs inconnus sont ignorés. */
    val fixtureJson = Json { ignoreUnknownKeys = true }
  }
}
