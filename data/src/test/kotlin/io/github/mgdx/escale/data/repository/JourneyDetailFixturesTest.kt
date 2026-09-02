package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.format.elevationOf
import io.github.mgdx.escale.core.format.shareLinesOf
import io.github.mgdx.escale.core.format.transitLineLabel
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.travelSteps
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.data.PlanTestSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles de l'écran de détail (SPEC.md § 5.3), passées sur les **réponses réelles** capturées
 * depuis `api.transitous.org` et rangées dans `src/test/resources/fixtures/`.
 *
 * Les tests de `:core` vérifient les règles sur des données construites à la main ; ceux-ci
 * vérifient qu'elles tiennent sur ce que le serveur envoie vraiment, décodage compris. C'est la
 * seule façon d'attraper un champ que le mapping ne remonterait pas.
 */
class JourneyDetailFixturesTest {

  @Test
  fun `une portion en transport en commun porte sa ligne, sa direction et son transporteur`() = runTest {
    val leg = transitLeg(backgroundScope)

    assertEquals("E5", transitLineLabel(leg))
    assertEquals("Tiergarten", leg.headsign)
    assertEquals("Bus Nürnberg", leg.agencyName)
  }

  @Test
  fun `les arrets intermediaires arrivent avec leurs heures de passage`() = runTest {
    val leg = transitLeg(backgroundScope)

    assertEquals(10, leg.intermediateStops.size)
    assertTrue(leg.intermediateStops.all { it.arrival != null || it.departure != null })
  }

  @Test
  fun `le quai et les indications d'accessibilite sont remontes tels quels`() = runTest {
    val leg = transitLeg(backgroundScope)

    assertEquals("E", leg.from.track)
    assertEquals("2", leg.to.track)
    assertNotNull(leg.wheelchairAccessible)
    assertEquals(false, leg.bikesAllowed)
  }

  @Test
  fun `les marqueurs START et END du serveur ne remontent jamais dans le domaine`() = runTest {
    val page = PlanTestSupport.page("plan_walk_detailed.json", backgroundScope, JourneyCategory.WALK)
    val leg = page.direct.single().legs.single()

    // La fixture porte littéralement "START" et "END" : le mapping les traduit en absence de nom.
    assertEquals("", leg.from.name)
    assertEquals("", leg.to.name)
  }

  @Test
  fun `un vrai nom d'arret traverse le mapping intact`() = runTest {
    val leg = transitLeg(backgroundScope)

    assertEquals("Nürnberg Hbf", leg.from.name)
    assertEquals("Nürnberg Tiergarten", leg.to.name)
  }

  @Test
  fun `un cheminement detaille porte ses manoeuvres et son denivele`() = runTest {
    val page = PlanTestSupport.page("plan_walk_detailed.json", backgroundScope, JourneyCategory.WALK)
    val leg = page.direct.single().legs.single()

    assertEquals(41, leg.travelSteps.size)
    // Le serveur renvoie bien le champ, même à zéro : « plat » n'est pas « inconnu ».
    assertNotNull(elevationOf(leg.travelSteps))
  }

  @Test
  fun `une portion en libre-service porte son systeme, sa contrainte de retour et son lien`() = runTest {
    val page = PlanTestSupport.page("plan_rental_direct.json", backgroundScope, JourneyCategory.BIKE)
    val rental = page.direct.flatMap { it.legs }
      .filterIsInstance<JourneyLeg.Rental>()
      .firstNotNullOf { it.rental }

    assertEquals("nextbike Berlin", rental.systemName)
    assertNotNull(rental.returnConstraint)
    assertNotNull(rental.rentalUriAndroid)
    assertNotNull(rental.formFactor)
  }

  @Test
  fun `une perturbation arrive avec son titre, sa gravite, sa periode et son lien`() = runTest {
    val page = PlanTestSupport.page("plan_with_alerts.json", backgroundScope)
    val alert = page.journeys.flatMap { it.alerts }.first()

    assertEquals("Le 05 & 06/09 : Triathlon de Bordeaux !", alert.headerText)
    assertTrue(alert.descriptionText.isNotBlank())
    assertEquals(1, alert.periods.size)
    assertNotNull(alert.url)
  }

  @Test
  fun `le trajet rafraichi est un trajet complet, exploitable par l'ecran de detail`() = runTest {
    val repository = PlanTestSupport.repository(
      PlanTestSupport.engineServing("refresh_itinerary.json"),
      backgroundScope,
    )

    val journey = repository.refresh(itineraryId = "peu-importe", detailedLegs = true)

    val value = (journey as Outcome.Success).value
    assertEquals(3, value.legs.size)
    assertEquals(JourneyCategory.TRANSIT, JourneyRefresh.categoryOf(value))
    // Un en-tête, le départ, une ligne par portion, l'arrivée.
    assertEquals(3 + 3, shareLinesOf(value).size)
  }

  private suspend fun transitLeg(scope: CoroutineScope): JourneyLeg.Transit {
    val page = PlanTestSupport.page("plan_transit_transfer.json", scope)
    return page.journeys.first().legs.filterIsInstance<JourneyLeg.Transit>().first()
  }
}
