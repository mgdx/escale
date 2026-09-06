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
 * jusqu'aux paramètres réellement envoyés — c'est-à-dire jusqu'au seul onglet qui emprunte encore
 * un véhicule partagé, l'onglet Vélo.
 */
class RentalFormFactorQueryTest {

  @Test
  fun `sans reglage, seul l onglet velo est filtre`() {
    val allowed = SearchPreferences().allowedRentalFormFactors
    assertEquals(emptySet<RentalFormFactor>(), allowed)
    // Onglet Vélo : la liste des trois types de SPEC.md § 5.2.
    assertEquals(
      mapOf(RentalFormFactorQuery.DIRECT to "BICYCLE,SCOOTER_STANDING,SCOOTER_SEATED"),
      RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed),
    )
    assertTrue(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
  }

  @Test
  fun `aucun onglet hors velo ne demande de vehicule partage`() {
    // Le rabattement du transport en commun se fait à pied : il n'y a plus rien à y filtrer, et
    // les onglets Voiture et À pied n'ont jamais emprunté de véhicule partagé (SPEC.md § 5.2).
    listOf(JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.WALK).forEach { categorie ->
      assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(categorie, emptySet()))
      assertEquals(
        emptyMap<String, String>(),
        RentalFormFactorQuery.parameters(categorie, setOf(RentalFormFactor.BICYCLE)),
      )
    }
  }

  @Test
  fun `decocher les trottinettes dans les reglages les exclut du seul onglet qui en propose`() {
    var allowed = SearchPreferences().allowedRentalFormFactors
    allowed = RentalFormFactorSelection.toggled(allowed, RentalFormFactor.SCOOTER_STANDING, accepted = false)
    allowed = RentalFormFactorSelection.toggled(allowed, RentalFormFactor.SCOOTER_SEATED, accepted = false)

    val direct = RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed)[RentalFormFactorQuery.DIRECT]
    assertEquals(RentalFormFactor.BICYCLE.name, direct)
  }

  @Test
  fun `ne garder que le velo laisse l onglet velo actif`() {
    val allowed = setOf(RentalFormFactor.BICYCLE)
    assertTrue(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
    assertEquals(
      RentalFormFactor.BICYCLE.name,
      RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed)[RentalFormFactorQuery.DIRECT],
    )
  }

  @Test
  fun `un reglage sans aucun type offert vide l onglet velo de ses vehicules`() {
    // Réglage qu'une version antérieure a pu persister, du temps où ces types étaient proposés.
    listOf(RentalFormFactor.CAR, RentalFormFactor.MOPED, RentalFormFactor.CARGO_BICYCLE).forEach { type ->
      val allowed = setOf(type)
      assertFalse(RentalFormFactorQuery.bikeTabAcceptsRentals(allowed))
      assertEquals(emptyMap<String, String>(), RentalFormFactorQuery.parameters(JourneyCategory.BIKE, allowed))
    }
  }

  @Test
  fun `aucun vehicule motorise ne se glisse dans une valeur envoyee`() {
    val motorises = listOf(RentalFormFactor.CAR, RentalFormFactor.MOPED)
    motorises.forEach { assertFalse(it in RentalFormFactorSelection.OFFERED) }
    JourneyCategory.entries.forEach { categorie ->
      RentalFormFactorQuery.parameters(categorie, emptySet()).values.forEach { valeur ->
        val types = valeur.split(",")
        motorises.forEach { assertFalse(types.contains(it.name)) }
      }
    }
  }
}
