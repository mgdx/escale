package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.TimeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * SPEC.md § 10 exige un test de « l'assemblage des paramètres de requête pour chacune des
 * catégories ». Ces tests sont le filet qui protège le projet le jour où MOTIS renommera un
 * paramètre marqué « expérimental » : c'est ici que la rupture se verra.
 */
class PlanQueryBuilderTest {

  private val from = Location(
    id = null,
    name = "Place de la Gare",
    description = null,
    coordinates = LatLon(lat = 49.4458, lon = 11.0821),
    kind = PlaceKind.ADDRESS,
  )

  private val to = Location(
    id = null,
    name = "Jardin zoologique",
    description = null,
    coordinates = LatLon(lat = 49.4478, lon = 11.1497),
    kind = PlaceKind.PLACE,
  )

  private fun query(
    category: JourneyCategory,
    time: TimeChoice = TimeChoice.Now,
    preferences: SearchPreferences = SearchPreferences(),
    language: String? = null,
  ) = SearchQuery(
    from = from,
    to = to,
    time = time,
    category = category,
    preferences = preferences,
    language = language,
  )

  // --- Un onglet, une requête ----------------------------------------------------------------

  @Test
  fun `l onglet transport en commun vide directModes et se rabat a pied`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))
    assertEquals("TRANSIT", parameters["transitModes"])
    // Le premier et le dernier kilomètre se font à pied, jamais en véhicule partagé (SPEC.md § 5.2).
    assertEquals("WALK", parameters["preTransitModes"])
    assertEquals("WALK", parameters["postTransitModes"])
    assertEquals("", parameters["directModes"])
    // Le plafond de durée directe ne veut rien dire quand on ne demande aucun trajet direct.
    assertNull(parameters["maxDirectTime"])
  }

  @Test
  fun `l onglet transport en commun cherche les arrets a trente minutes de marche`() {
    // Défaut serveur : 900 s. L'onglet rendait une liste vide hors ville dense, dès que le premier
    // arrêt était à plus d'un quart d'heure de marche.
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))
    assertEquals("1800", parameters["maxPreTransitTime"])
    assertEquals("1800", parameters["maxPostTransitTime"])

    // « Plus tôt » / « Plus tard » renvoient la requête telle quelle : le plafond suit.
    val paged = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT), cursor = "LATER|1756785600")
    assertEquals("1800", paged["maxPreTransitTime"])
    assertEquals("1800", paged["maxPostTransitTime"])
  }

  @Test
  fun `le rabattement a pied n est plafonne que la ou il existe`() {
    listOf(JourneyCategory.CAR, JourneyCategory.BIKE, JourneyCategory.WALK).forEach { category ->
      val parameters = PlanQueryBuilder.build(query(category))
      assertNull("$category ne fait aucun rabattement", parameters["maxPreTransitTime"])
      assertNull("$category ne fait aucun rabattement", parameters["maxPostTransitTime"])
    }
  }

  @Test
  fun `le plafond de marche annonce est celui reellement envoye`() {
    // L'état vide de l'onglet nomme cette limite : les deux ne peuvent pas diverger.
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))
    val announced = PlanQueryBuilder.maxPrePostTransitTime().seconds.toString()
    assertEquals(announced, parameters["maxPreTransitTime"])
    assertEquals(announced, parameters["maxPostTransitTime"])
  }

  @Test
  fun `aucun reglage de vehicules ne rouvre le rabattement en libre-service`() {
    RentalFormFactor.entries.forEach { type ->
      val preferences = SearchPreferences(allowedRentalFormFactors = setOf(type))
      val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT, preferences = preferences))
      assertEquals("WALK", parameters["preTransitModes"])
      assertEquals("WALK", parameters["postTransitModes"])
      // Et aucun paramètre expérimental de types de véhicules ne part avec cette requête.
      assertNull(parameters[RentalFormFactorQuery.DIRECT])
    }
  }

  @Test
  fun `l onglet voiture vide transitModes et desserre le plafond de trente minutes`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.CAR))
    assertEquals("CAR", parameters["directModes"])
    assertEquals("", parameters["transitModes"])
    // Défaut serveur : 1800 s, soit trente minutes. SPEC.md § 5.2 demande environ quatre heures.
    assertEquals("14400", parameters["maxDirectTime"])
    assertNull(parameters["preTransitModes"])
    assertNull(parameters["postTransitModes"])
  }

  @Test
  fun `l onglet velo melange velo personnel et libre-service`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.BIKE))
    assertEquals("BIKE,RENTAL", parameters["directModes"])
    assertEquals("", parameters["transitModes"])
    assertEquals("10800", parameters["maxDirectTime"])
    assertEquals(
      "BICYCLE,SCOOTER_STANDING,SCOOTER_SEATED",
      parameters[RentalFormFactorQuery.DIRECT],
    )
  }

  @Test
  fun `l onglet a pied ne demande que la marche`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.WALK))
    assertEquals("WALK", parameters["directModes"])
    assertEquals("", parameters["transitModes"])
    assertEquals("7200", parameters["maxDirectTime"])
    assertNull(parameters[RentalFormFactorQuery.DIRECT])
  }

  @Test
  fun `aucun onglet ne melange transport en commun et modes directs`() {
    // Le motif est expliqué par l'API elle-même : un trajet en transport en commun plus lent que
    // le meilleur trajet direct est éliminé pendant la recherche.
    JourneyCategory.entries.forEach { category ->
      val parameters = PlanQueryBuilder.build(query(category))
      val transit = parameters["transitModes"].orEmpty()
      val direct = parameters["directModes"].orEmpty()
      assertTrue(
        "les deux familles de modes sont peuplées pour $category",
        transit.isEmpty() || direct.isEmpty(),
      )
    }
  }

  // --- Détail et pagination ------------------------------------------------------------------

  @Test
  fun `la liste de resultats demande des portions allegees`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))
    assertEquals("false", parameters["detailedLegs"])
    assertNull(parameters["detailedTransfers"])
  }

  @Test
  fun `l ecran de detail demande les traces et les correspondances detaillees`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT), detailedLegs = true)
    assertEquals("true", parameters["detailedLegs"])
    assertEquals("true", parameters["detailedTransfers"])
  }

  @Test
  fun `la pagination renvoie la requete telle quelle et ne change que le curseur`() {
    val search = query(JourneyCategory.TRANSIT, time = TimeChoice.DepartAt(Instant.parse("2026-09-02T06:00:00Z")))
    val first = PlanQueryBuilder.build(search)
    val next = PlanQueryBuilder.build(search, cursor = "EARLIER|1756785600")
    assertEquals("EARLIER|1756785600", next["pageCursor"])
    assertNull(first["pageCursor"])
    assertEquals(first, next - "pageCursor")
  }

  // --- Heure ---------------------------------------------------------------------------------

  @Test
  fun `partir maintenant n envoie aucune heure`() {
    // L'heure doit être celle du moment où la requête part, pas celle de la saisie : on la laisse
    // au serveur plutôt que de la figer côté application.
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT, time = TimeChoice.Now))
    assertNull(parameters["time"])
    assertNull(parameters["arriveBy"])
  }

  @Test
  fun `partir a une heure donnee envoie une date-heure ISO complete`() {
    val time = TimeChoice.DepartAt(Instant.parse("2026-09-02T06:00:00Z"))
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT, time = time))
    assertEquals("2026-09-02T06:00:00Z", parameters["time"])
    assertNull(parameters["arriveBy"])
  }

  @Test
  fun `arriver avant une heure donnee bascule arriveBy`() {
    val time = TimeChoice.ArriveBy(Instant.parse("2026-09-02T18:30:45Z"))
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT, time = time))
    assertEquals("2026-09-02T18:30:45Z", parameters["time"])
    assertEquals("true", parameters["arriveBy"])
  }

  // --- Lieux ---------------------------------------------------------------------------------

  @Test
  fun `un arret est designe par son identifiant, une adresse par ses coordonnees`() {
    val stop = Location(
      id = "de-DELFI_de:09564:1434:11:11",
      name = "Nürnberg Tiergarten",
      description = null,
      coordinates = LatLon(lat = 49.450714, lon = 11.137636),
      kind = PlaceKind.STOP,
    )
    val parameters = PlanQueryBuilder.build(
      SearchQuery(from = from, to = stop, time = TimeChoice.Now, category = JourneyCategory.TRANSIT),
    )
    assertEquals("49.4458,11.0821", parameters["fromPlace"])
    assertEquals("de-DELFI_de:09564:1434:11:11", parameters["toPlace"])
  }

  @Test
  fun `les coordonnees sont ecrites sans notation scientifique ni virgule decimale`() {
    val nearNullIsland = Location(
      id = null,
      name = "Point d'essai",
      description = null,
      coordinates = LatLon(lat = 0.0001, lon = -0.00025),
      kind = PlaceKind.ADDRESS,
    )
    val parameters = PlanQueryBuilder.build(
      SearchQuery(from = nearNullIsland, to = to, time = TimeChoice.Now, category = JourneyCategory.WALK),
    )
    assertEquals("0.0001,-0.00025", parameters["fromPlace"])
  }

  // --- Réglages de recherche -----------------------------------------------------------------

  @Test
  fun `les reglages laisses par defaut n alourdissent pas la requete`() {
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))
    listOf(
      "pedestrianSpeed",
      "pedestrianProfile",
      "cyclingSpeed",
      "elevationCosts",
      "additionalTransferTime",
      "maxTransfers",
      "requireBikeTransport",
    ).forEach { assertNull("$it ne devrait pas être envoyé", parameters[it]) }
  }

  @Test
  fun `les reglages de recherche se traduisent en parametres de l onglet transport en commun`() {
    val preferences = SearchPreferences(
      pedestrianSpeedMetersPerSecond = 0.9,
      pedestrianProfile = PedestrianProfile.WHEELCHAIR,
      cyclingSpeedMetersPerSecond = 4.5,
      elevationCosts = ElevationCosts.HIGH,
      additionalTransferTime = Duration.ofMinutes(5),
      maxTransfers = 2,
      requireBikeTransport = true,
    )
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.TRANSIT, preferences = preferences))
    assertEquals("0.9", parameters["pedestrianSpeed"])
    assertEquals("WHEELCHAIR", parameters["pedestrianProfile"])
    assertEquals("4.5", parameters["cyclingSpeed"])
    assertEquals("HIGH", parameters["elevationCosts"])
    // Le serveur compte la marge de correspondance en minutes.
    assertEquals("5", parameters["additionalTransferTime"])
    assertEquals("2", parameters["maxTransfers"])
    assertEquals("true", parameters["requireBikeTransport"])
  }

  @Test
  fun `les reglages sans effet sur un onglet ne lui sont pas envoyes`() {
    val preferences = SearchPreferences(
      pedestrianSpeedMetersPerSecond = 0.9,
      elevationCosts = ElevationCosts.LOW,
      maxTransfers = 1,
      requireBikeTransport = true,
    )
    val car = PlanQueryBuilder.build(query(JourneyCategory.CAR, preferences = preferences))
    assertNull(car["pedestrianSpeed"])
    assertNull(car["maxTransfers"])
    assertNull(car["requireBikeTransport"])

    val walk = PlanQueryBuilder.build(query(JourneyCategory.WALK, preferences = preferences))
    assertEquals("0.9", walk["pedestrianSpeed"])
    assertNull(walk["elevationCosts"])
    assertNull(walk["maxTransfers"])
  }

  // --- Types de véhicules partagés, paramètres expérimentaux ---------------------------------

  @Test
  fun `exclure un type de vehicule le retire de l onglet velo`() {
    val preferences = SearchPreferences(
      allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE, RentalFormFactor.CARGO_BICYCLE),
    )
    val bike = PlanQueryBuilder.build(query(JourneyCategory.BIKE, preferences = preferences))
    assertEquals("BIKE,RENTAL", bike["directModes"])
    // Le vélo cargo n'appartient pas à l'onglet Vélo tel que SPEC.md § 5.2 le définit ; les
    // trottinettes, elles, viennent d'être exclues par l'usager.
    assertEquals("BICYCLE", bike[RentalFormFactorQuery.DIRECT])
  }

  @Test
  fun `exclure tous les vehicules partages laisse l onglet velo au velo personnel`() {
    val preferences = SearchPreferences(allowedRentalFormFactors = setOf(RentalFormFactor.CAR))
    val parameters = PlanQueryBuilder.build(query(JourneyCategory.BIKE, preferences = preferences))
    // Un filtre vide voudrait dire « tous les véhicules » côté serveur : il faut retirer le mode.
    assertEquals("BIKE", parameters["directModes"])
    assertNull(parameters[RentalFormFactorQuery.DIRECT])
  }

  @Test
  fun `les onglets voiture et a pied n emettent aucun parametre experimental`() {
    val preferences = SearchPreferences(allowedRentalFormFactors = setOf(RentalFormFactor.BICYCLE))
    listOf(JourneyCategory.CAR, JourneyCategory.WALK).forEach { category ->
      val parameters = PlanQueryBuilder.build(query(category, preferences = preferences))
      assertFalse(
        "$category ne doit filtrer aucun véhicule partagé",
        parameters.keys.any { it.contains("RentalFormFactors") },
      )
    }
  }

  // --- Langue des libellés ---------------------------------------------------------------------

  @Test
  fun `la langue de l interface part avec la recherche`() {
    // Sans elle, les noms d'arrêts et les destinations affichées arrivent dans la langue par
    // défaut du flux, et non dans celle de l'usager.
    JourneyCategory.entries.forEach { category ->
      assertEquals("fr", PlanQueryBuilder.build(query(category, language = "fr"))["language"])
    }
  }

  @Test
  fun `une recherche sans langue n envoie pas le parametre`() {
    assertNull(PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))["language"])
  }

  @Test
  fun `la pagination conserve la langue`() {
    val search = query(JourneyCategory.TRANSIT, language = "de")
    assertEquals("de", PlanQueryBuilder.build(search, cursor = "LATER|1756785600")["language"])
  }

  // --- Rafraîchissement ----------------------------------------------------------------------

  @Test
  fun `un rafraichissement ne renvoie que l identifiant et le niveau de detail`() {
    val parameters = PlanQueryBuilder.refresh("opaque-id", detailedLegs = true)
    assertEquals(
      mapOf("itineraryId" to "opaque-id", "detailedLegs" to "true", "detailedTransfers" to "true"),
      parameters,
    )
  }

  // --- Plafond de durée lisible par l'interface ------------------------------------------------

  @Test
  fun `le plafond de duree annonce est celui reellement envoye`() {
    // SPEC.md § 5.2 : l'état vide d'un onglet direct nomme la limite de durée. Elle doit être la
    // même que celle de la requête, sans quoi le message mentirait.
    listOf(JourneyCategory.CAR, JourneyCategory.BIKE, JourneyCategory.WALK).forEach { category ->
      val sent = PlanQueryBuilder.build(query(category))["maxDirectTime"]
      assertEquals(sent, PlanQueryBuilder.maxDirectTime(category)?.seconds?.toString())
    }
  }

  @Test
  fun `l onglet transport en commun n a pas de plafond de duree`() {
    assertNull(PlanQueryBuilder.maxDirectTime(JourneyCategory.TRANSIT))
    assertNull(PlanQueryBuilder.build(query(JourneyCategory.TRANSIT))["maxDirectTime"])
  }
}
