package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Ces tests portent la promesse centrale du lot : **un usager qui n'a rien réglé obtient exactement
 * le comportement d'avant.** Chaque liste de choix doit donc contenir la valeur par défaut de
 * [SearchPreferences], et la reconnaître comme celle qui est cochée.
 */
class SearchPreferenceOptionsTest {

  @Test
  fun `l allure par defaut est celle de SearchPreferences`() {
    assertEquals(
      SearchPreferences().pedestrianSpeedMetersPerSecond,
      PedestrianSpeedOption.DEFAULT.metersPerSecond,
      0.0,
    )
    assertEquals(
      PedestrianSpeedOption.DEFAULT,
      PedestrianSpeedOption.nearest(SearchPreferences().pedestrianSpeedMetersPerSecond),
    )
  }

  @Test
  fun `la vitesse a velo par defaut est celle de SearchPreferences`() {
    assertEquals(
      SearchPreferences().cyclingSpeedMetersPerSecond,
      CyclingSpeedOption.DEFAULT.metersPerSecond,
      0.0,
    )
    assertEquals(
      CyclingSpeedOption.DEFAULT,
      CyclingSpeedOption.nearest(SearchPreferences().cyclingSpeedMetersPerSecond),
    )
  }

  @Test
  fun `les allures sont ordonnees de la plus lente a la plus rapide`() {
    val pieton = PedestrianSpeedOption.entries.map { it.metersPerSecond }
    assertEquals(pieton.sorted(), pieton)
    val velo = CyclingSpeedOption.entries.map { it.metersPerSecond }
    assertEquals(velo.sorted(), velo)
  }

  @Test
  fun `une valeur persistee hors liste se rattache au choix voisin`() {
    assertEquals(PedestrianSpeedOption.NORMAL, PedestrianSpeedOption.nearest(1.25))
    assertEquals(PedestrianSpeedOption.VERY_FAST, PedestrianSpeedOption.nearest(9.0))
    assertEquals(CyclingSpeedOption.VERY_SLOW, CyclingSpeedOption.nearest(0.0))
  }

  @Test
  fun `la marge de correspondance par defaut est nulle et figure dans les choix`() {
    assertEquals(Duration.ZERO, AdditionalTransferTimeOptions.DEFAULT)
    assertTrue(AdditionalTransferTimeOptions.DEFAULT in AdditionalTransferTimeOptions.VALUES)
    assertEquals(Duration.ZERO, AdditionalTransferTimeOptions.nearest(Duration.ofSeconds(20)))
    assertEquals(Duration.ofMinutes(10), AdditionalTransferTimeOptions.nearest(Duration.ofHours(1)))
  }

  @Test
  fun `sans limite de correspondances est le choix par defaut`() {
    assertEquals(null, MaxTransfersOptions.DEFAULT)
    assertTrue(null in MaxTransfersOptions.VALUES)
    assertEquals(null, MaxTransfersOptions.nearest(null))
    assertEquals(2, MaxTransfersOptions.nearest(2))
    assertEquals(4, MaxTransfersOptions.nearest(12))
    assertEquals(null, MaxTransfersOptions.nearest(-1))
  }

  @Test
  fun `sans filtre, toutes les cases de types de vehicules sont cochees`() {
    assertEquals(
      RentalFormFactorSelection.OFFERED.toSet(),
      RentalFormFactorSelection.selected(emptySet()),
    )
  }

  @Test
  fun `decocher un type le retire, sans toucher aux autres`() {
    val apres = RentalFormFactorSelection.toggled(
      allowed = emptySet(),
      formFactor = RentalFormFactor.SCOOTER_STANDING,
      accepted = false,
    )
    assertFalse(RentalFormFactor.SCOOTER_STANDING in apres)
    assertTrue(RentalFormFactor.BICYCLE in apres)
    assertEquals(RentalFormFactorSelection.OFFERED.size - 1, apres.size)
  }

  @Test
  fun `tout recocher revient a ne persister aucun filtre`() {
    val sansTrottinette =
      RentalFormFactorSelection.toggled(emptySet(), RentalFormFactor.SCOOTER_STANDING, accepted = false)
    val remis = RentalFormFactorSelection.toggled(sansTrottinette, RentalFormFactor.SCOOTER_STANDING, accepted = true)
    assertEquals(emptySet<RentalFormFactor>(), remis)
  }

  @Test
  fun `decocher la derniere case est refuse, un filtre vide voudrait dire l inverse`() {
    val velosSeuls = setOf(RentalFormFactor.BICYCLE)
    assertTrue(RentalFormFactorSelection.isLastAccepted(velosSeuls, RentalFormFactor.BICYCLE))
    assertEquals(
      velosSeuls,
      RentalFormFactorSelection.toggled(velosSeuls, RentalFormFactor.BICYCLE, accepted = false),
    )
  }
}
