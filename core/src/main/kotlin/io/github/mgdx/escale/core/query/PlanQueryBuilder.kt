package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.SearchQuery
import io.github.mgdx.escale.core.model.TimeChoice
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Assemblage des paramètres de `GET /api/v6/plan` et de `GET /api/v6/refresh-itinerary`.
 *
 * Cette classe est du Kotlin pur : elle rend une simple table de paramètres, que `:data` pose sur
 * la requête HTTP. C'est ce qui permet de la couvrir en JVM, comme l'exige SPEC.md § 10.
 *
 * Trois règles de SPEC.md § 5.2 sont matérialisées ici et nulle part ailleurs :
 * - **une requête par onglet**, jamais un mélange de `transitModes` et de `directModes` : côté
 *   MOTIS, un trajet en transport en commun plus lent que le meilleur trajet direct est éliminé
 *   pendant la recherche, si bien qu'une requête mixte fait disparaître des résultats ;
 * - **`maxDirectTime` explicite** sur les onglets sans transport en commun, le défaut serveur de
 *   1800 s coupant tout trajet direct de plus de trente minutes. Le serveur peut plafonner ces
 *   valeurs, c'est son droit ;
 * - **`detailedLegs=false` sur la liste**, `true` seulement à l'ouverture d'un trajet (§ 7.6).
 */
object PlanQueryBuilder {

  /** Plafond de durée des trajets directs, par onglet (SPEC.md § 5.2). */
  private const val MAX_DIRECT_TIME_CAR_SECONDS = 4 * 60 * 60

  private const val MAX_DIRECT_TIME_BIKE_SECONDS = 3 * 60 * 60

  private const val MAX_DIRECT_TIME_WALK_SECONDS = 2 * 60 * 60

  /**
   * Le plafond de durée effectivement envoyé pour un onglet, ou `null` pour l'onglet transport en
   * commun, qui n'en envoie pas.
   *
   * L'état vide de SPEC.md § 5.2 doit nommer cette limite à l'usager (« aucun trajet trouvé dans la
   * limite de durée ») : l'interface la lit donc ici, au lieu d'en recopier une seconde qui
   * finirait par diverger de la requête réellement émise.
   */
  fun maxDirectTime(category: JourneyCategory): Duration? = when (category) {
    JourneyCategory.TRANSIT -> null
    JourneyCategory.CAR -> Duration.ofSeconds(MAX_DIRECT_TIME_CAR_SECONDS.toLong())
    JourneyCategory.BIKE -> Duration.ofSeconds(MAX_DIRECT_TIME_BIKE_SECONDS.toLong())
    JourneyCategory.WALK -> Duration.ofSeconds(MAX_DIRECT_TIME_WALK_SECONDS.toLong())
  }

  /**
   * Le serveur attend une date-heure ISO-8601. `Instant.toString()` omet les secondes quand elles
   * sont nulles, ce que tous les analyseurs n'acceptent pas : on les écrit toujours.
   */
  private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

  /**
   * Les paramètres d'une recherche.
   *
   * @param cursor `previousPageCursor` ou `nextPageCursor` d'une page déjà obtenue. La requête est
   *   par ailleurs renvoyée telle quelle : c'est exactement ce que demande SPEC.md § 5.2, et c'est
   *   ce que garantit cette fonction en ne dépendant que de [query] pour tout le reste.
   * @param detailedLegs `true` uniquement quand l'écran de détail est ouvert (SPEC.md § 7.6).
   */
  fun build(query: SearchQuery, cursor: String? = null, detailedLegs: Boolean = false): Map<String, String> {
    val parameters = LinkedHashMap<String, String>()
    parameters[PLACE_FROM] = place(query.from)
    parameters[PLACE_TO] = place(query.to)
    parameters.putAll(timeParameters(query.time))
    parameters.putAll(modeParameters(query.category, query.preferences))
    parameters.putAll(preferenceParameters(query.category, query.preferences))
    parameters.putAll(detailParameters(detailedLegs))
    if (cursor != null) parameters[PAGE_CURSOR] = cursor
    return parameters
  }

  /**
   * Les paramètres d'un rafraîchissement d'itinéraire. Le point d'entrée reconstruit le trajet à
   * partir de son seul identifiant : ni lieux, ni heure, ni modes n'ont à être renvoyés.
   */
  fun refresh(itineraryId: String, detailedLegs: Boolean = true): Map<String, String> {
    val parameters = LinkedHashMap<String, String>()
    parameters[ITINERARY_ID] = itineraryId
    parameters.putAll(detailParameters(detailedLegs))
    return parameters
  }

  /**
   * Un arrêt est désigné par son identifiant, tout le reste par ses coordonnées. Passer les
   * coordonnées d'une gare plutôt que son identifiant obligerait le serveur à un appariement
   * approximatif sur le réseau de rues, pour un résultat moins bon.
   */
  private fun place(location: Location): String {
    val stopId = location.id
    return if (location.kind == PlaceKind.STOP && !stopId.isNullOrBlank()) {
      stopId
    } else {
      "${number(location.coordinates.lat)},${number(location.coordinates.lon)}"
    }
  }

  /**
   * [TimeChoice.Now] n'envoie **pas** d'heure : le serveur prend la sienne au moment où il traite
   * la requête. C'est ce que demande la documentation de [TimeChoice] — l'heure du départ ne doit
   * pas être figée à l'instant où l'usager a fini de taper sa destination.
   */
  private fun timeParameters(time: TimeChoice): Map<String, String> = when (time) {
    TimeChoice.Now -> emptyMap()
    is TimeChoice.DepartAt -> mapOf(TIME to format(time.instant))
    is TimeChoice.ArriveBy -> mapOf(TIME to format(time.instant), ARRIVE_BY to "true")
  }

  private fun modeParameters(category: JourneyCategory, preferences: SearchPreferences): Map<String, String> {
    val formFactors = RentalFormFactorQuery.parameters(category, preferences.allowedRentalFormFactors)
    return when (category) {
      // Le seul onglet qui interroge la base horaire. `directModes` est vidé pour que les trajets
      // directs n'éliminent pas les trajets en transport en commun plus lents.
      //
      // Le rabattement se fait **à pied et seulement à pied** (SPEC.md § 5.2) : un premier ou
      // dernier kilomètre en véhicule partagé ne relève pas de cet onglet. C'est aussi pourquoi
      // aucun filtre de types de véhicules n'y est joint — il n'aurait plus rien à filtrer.
      JourneyCategory.TRANSIT -> linkedMapOf(
        TRANSIT_MODES to "TRANSIT",
        PRE_TRANSIT_MODES to "WALK",
        POST_TRANSIT_MODES to "WALK",
        DIRECT_MODES to "",
      )

      JourneyCategory.CAR -> directOnly("CAR", MAX_DIRECT_TIME_CAR_SECONDS)

      // `RENTAL` disparaît quand l'usager a exclu tous les types de véhicules partagés : il ne
      // reste alors que le vélo personnel, ce qui vaut mieux qu'un filtre vide, lequel signifierait
      // « tous les véhicules » côté serveur.
      JourneyCategory.BIKE -> {
        val modes = if (RentalFormFactorQuery.bikeTabAcceptsRentals(preferences.allowedRentalFormFactors)) {
          "BIKE,RENTAL"
        } else {
          "BIKE"
        }
        directOnly(modes, MAX_DIRECT_TIME_BIKE_SECONDS) + formFactors
      }

      JourneyCategory.WALK -> directOnly("WALK", MAX_DIRECT_TIME_WALK_SECONDS)
    }
  }

  /** Un onglet sans transport en commun : `transitModes` vide, et un plafond de durée explicite. */
  private fun directOnly(modes: String, maxDirectTimeSeconds: Int): Map<String, String> = linkedMapOf(
    TRANSIT_MODES to "",
    DIRECT_MODES to modes,
    MAX_DIRECT_TIME to maxDirectTimeSeconds.toString(),
  )

  /**
   * Les réglages de recherche (SPEC.md § 5.6), traduits en paramètres.
   *
   * Une préférence laissée à sa valeur par défaut n'est pas envoyée : la valeur par défaut du
   * domaine est celle du serveur, et une requête plus courte est une requête moins fragile.
   * Chaque réglage n'est joint qu'aux onglets où il change quelque chose.
   */
  private fun preferenceParameters(category: JourneyCategory, preferences: SearchPreferences): Map<String, String> {
    val parameters = LinkedHashMap<String, String>()
    // La marche sert partout sauf en voiture : trajet direct, rabattement, correspondances.
    if (category != JourneyCategory.CAR) {
      if (preferences.pedestrianSpeedMetersPerSecond != SearchPreferences.DEFAULT_PEDESTRIAN_SPEED) {
        parameters[PEDESTRIAN_SPEED] = number(preferences.pedestrianSpeedMetersPerSecond)
      }
      if (preferences.pedestrianProfile != PedestrianProfile.FOOT) {
        parameters[PEDESTRIAN_PROFILE] = preferences.pedestrianProfile.name
      }
    }
    // Le vélo sert dans son onglet et en rabattement libre-service vers un arrêt.
    if (category == JourneyCategory.BIKE || category == JourneyCategory.TRANSIT) {
      if (preferences.cyclingSpeedMetersPerSecond != SearchPreferences.DEFAULT_CYCLING_SPEED) {
        parameters[CYCLING_SPEED] = number(preferences.cyclingSpeedMetersPerSecond)
      }
      // L'OpenAPI ne fait jouer le profil de dénivelé que sur le mode `BIKE`.
      if (preferences.elevationCosts != ElevationCosts.NONE) {
        parameters[ELEVATION_COSTS] = preferences.elevationCosts.name
      }
    }
    // Correspondances et transport de vélos n'existent que là où il y a des courses.
    if (category == JourneyCategory.TRANSIT) {
      if (!preferences.additionalTransferTime.isZero) {
        // Le serveur compte cette marge en minutes, pas en secondes.
        parameters[ADDITIONAL_TRANSFER_TIME] = preferences.additionalTransferTime.toMinutes().toString()
      }
      preferences.maxTransfers?.let { parameters[MAX_TRANSFERS] = it.toString() }
      if (preferences.requireBikeTransport) parameters[REQUIRE_BIKE_TRANSPORT] = "true"
    }
    return parameters
  }

  /**
   * `detailedTransfers` hérite de `detailedLegs` quand il est absent : il n'est donc envoyé que
   * pour l'écran de détail, où SPEC.md § 5.3 le veut explicitement à `true`.
   */
  private fun detailParameters(detailedLegs: Boolean): Map<String, String> = if (detailedLegs) {
    linkedMapOf(DETAILED_LEGS to "true", DETAILED_TRANSFERS to "true")
  } else {
    linkedMapOf(DETAILED_LEGS to "false")
  }

  private fun format(instant: Instant): String = TIME_FORMAT.format(instant)

  /**
   * Écrit un nombre sans notation scientifique et sans dépendre de la locale : `49.4458` et non
   * `49,4458` ni `1.0E-4`, que le serveur refuserait.
   */
  private fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

  // --- Noms des paramètres, écrits une seule fois --------------------------------------------
  // Les trois paramètres expérimentaux de types de véhicules ne sont pas ici : SPEC.md § 5.2 exige
  // qu'ils vivent dans RentalFormFactorQuery, et là seulement.

  private const val PLACE_FROM = "fromPlace"
  private const val PLACE_TO = "toPlace"
  private const val TIME = "time"
  private const val ARRIVE_BY = "arriveBy"
  private const val TRANSIT_MODES = "transitModes"
  private const val DIRECT_MODES = "directModes"
  private const val PRE_TRANSIT_MODES = "preTransitModes"
  private const val POST_TRANSIT_MODES = "postTransitModes"
  private const val MAX_DIRECT_TIME = "maxDirectTime"
  private const val PEDESTRIAN_SPEED = "pedestrianSpeed"
  private const val PEDESTRIAN_PROFILE = "pedestrianProfile"
  private const val CYCLING_SPEED = "cyclingSpeed"
  private const val ELEVATION_COSTS = "elevationCosts"
  private const val ADDITIONAL_TRANSFER_TIME = "additionalTransferTime"
  private const val MAX_TRANSFERS = "maxTransfers"
  private const val REQUIRE_BIKE_TRANSPORT = "requireBikeTransport"
  private const val DETAILED_LEGS = "detailedLegs"
  private const val DETAILED_TRANSFERS = "detailedTransfers"
  private const val PAGE_CURSOR = "pageCursor"
  private const val ITINERARY_ID = "itineraryId"
}
