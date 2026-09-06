package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalFormFactorSelection
import io.github.mgdx.escale.core.model.SearchPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le chemin complet du réglage de SPEC.md § 5.2 : de la case décochée dans l'écran de réglages
 * jusqu'aux paramètres réellement envoyés, pour les trois emplois du filtre — trajet direct,
 * rabattement vers le premier arrêt, et après le dernier.
 */
class RentalFormFactorQueryTest {

  @Test
  fun `sans reglage, seul le rabattement est filtre, et de la seule voiture partagee`() {
    val allowed = SearchPreferences().allowedRentalFormFactors
    assertEquals(emptySet<RentalFormFactor>(), allowed)
    // Rabattement : le filtre est envoyé même sans réglage, car le défaut du serveur inclut la
    // voiture partagée, que l'onglet Transport en commun ne veut pas (SPEC.md § 5.2).
    val rabattement = RentalFormFactorQuery.parameters(JourneyCategory.TRANSIT, allowed)
    val attendu = "BICYCLE,CARGO_BICYCLE,MOPED,SCOOTER_STANDING,SCOOTER_SEATED,OTHER"
    assertEquals(attendu, rabattement[RentalFormFactorQuery.PRE_TRANSIT])
    assertEquals(attendu, rabattement[RentalFormFactorQuery.POST_TRANSIT])
    assertTrue(RentalFormFactorQuery.transitTabAcceptsRentals(allowed))
    // Onglet Vélo : la liste des trois types de SPEC.md § 5.2, inchangée par rapport à aujourd'hui.
    assertEquals(
      mapOf(RentalFormFactorQuery.DIRECT to "BICYCLE,SCOOTER_STANDING,SCOOTER_SEATED"),
      RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed),
    )
    assertTrue(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
  }

  @Test
  fun `decocher les trottinettes dans les reglages les exclut partout, d un seul endroit`() {
    var allowed = SearchPreferences().allowedRentalFormFactors
    allowed = RentalFormFactorSelection.toggled(allowed, RentalFormFactor.SCOOTER_STANDING, accepted = false)
    allowed = RentalFormFactorSelection.toggled(allowed, RentalFormFactor.SCOOTER_SEATED, accepted = false)

    val rabattement = RentalFormFactorQuery.parameters(JourneyCategory.TRANSIT, allowed)
    val direct = RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed)

    listOf(
      rabattement[RentalFormFactorQuery.PRE_TRANSIT],
      rabattement[RentalFormFactorQuery.POST_TRANSIT],
      direct[RentalFormFactorQuery.DIRECT],
    ).forEach { valeur ->
      assertFalse(valeur.orEmpty().contains(RentalFormFactor.SCOOTER_STANDING.name))
      assertFalse(valeur.orEmpty().contains(RentalFormFactor.SCOOTER_SEATED.name))
      assertTrue(valeur.orEmpty().contains(RentalFormFactor.BICYCLE.name))
    }
  }

  @Test
  fun `ne garder que le velo laisse l onglet velo actif et restreint le rabattement`() {
    val allowed = setOf(RentalFormFactor.BICYCLE)
    assertTrue(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
    assertEquals(
      RentalFormFactor.BICYCLE.name,
      RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed)[RentalFormFactorQuery.DIRECT],
    )
    val rabattement = RentalFormFactorQuery.parameters(JourneyCategory.TRANSIT, allowed)
    assertEquals(RentalFormFactor.BICYCLE.name, rabattement[RentalFormFactorQuery.PRE_TRANSIT])
    assertEquals(RentalFormFactor.BICYCLE.name, rabattement[RentalFormFactorQuery.POST_TRANSIT])
  }

  @Test
  fun `la voiture partagee n est empruntee par aucun onglet`() {
    // Un réglage qui ne garderait que la voiture — ce que l'écran de réglages ne permet plus, mais
    // qu'une version antérieure a pu persister — ne laisse aucun véhicule à proposer nulle part.
    val allowed = setOf(RentalFormFactor.CAR)
    assertFalse(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
    assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed))
    assertFalse(RentalFormFactorQuery.transitTabAcceptsRentals(allowed))
    assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(JourneyCategory.TRANSIT, allowed))

    // Et elle ne se glisse dans aucune valeur envoyée, quel que soit le réglage.
    RentalFormFactorSelection.OFFERED.forEach { assertFalse(it == RentalFormFactor.CAR) }
    JourneyCategory.entries.forEach { categorie ->
      RentalFormFactorQuery.parameters(categorie, emptySet()).values.forEach { valeur ->
        assertFalse(valeur.split(",").contains(RentalFormFactor.CAR.name))
      }
    }
  }

  @Test
  fun `les onglets voiture et a pied ignorent le reglage`() {
    val allowed = setOf(RentalFormFactor.BICYCLE)
    assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(JourneyCategory.CAR, allowed))
    assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(JourneyCategory.WALK, allowed))
  }
}
