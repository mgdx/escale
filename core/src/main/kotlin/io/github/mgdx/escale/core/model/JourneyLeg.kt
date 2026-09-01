package io.github.mgdx.escale.core.model

import java.time.Duration
import java.time.Instant

/**
 * Une portion de trajet, décalque du schéma `Leg` de l'OpenAPI MOTIS.
 *
 * Les sous-types sont exactement ceux du contrat (docs/architecture.md § 4) : [Transit], [Walk],
 * [Bike], [Car] et [Rental]. Les modes hors périmètre de la v1 (`ODM`, `RIDE_SHARING`, `FLEX`,
 * `HGV`, `CAR_PARKING`, `CAR_DROPOFF`) ne créent pas de sixième sous-type : ils se rangent dans
 * [Transit] ou [Car] selon qu'ils portent ou non un horaire, et s'affichent sans interface dédiée.
 */
sealed interface JourneyLeg {
  /** Heure de départ effective, égale à [scheduledStartTime] en l'absence de temps réel. */
  val startTime: Instant

  /** Heure d'arrivée effective, égale à [scheduledEndTime] en l'absence de temps réel. */
  val endTime: Instant

  val scheduledStartTime: Instant

  val scheduledEndTime: Instant

  /**
   * Durée annoncée par le serveur. Elle n'est pas toujours égale à `endTime - startTime` : sur un
   * cheminement de correspondance, MOTIS y a déjà intégré `transferTimeFactor` et
   * `additionalTransferTime`.
   */
  val duration: Duration

  val from: Place

  val to: Place

  /** Distance parcourue en mètres. Nulle sur une portion en transport en commun. */
  val distanceMeters: Double?

  /**
   * Tracé de la portion, déjà décodé. Vide lorsque la requête a demandé `detailedLegs=false`,
   * ce qui est le cas de la liste de résultats (SPEC.md § 7.6).
   */
  val geometry: List<LatLon>

  /** Vrai si une donnée temps réel existe. Faux : ne rien colorer, ne pas annoncer « à l'heure ». */
  val realTime: Boolean

  val cancelled: Boolean

  val alerts: List<Disruption>

  /** Portion en transport en commun : c'est la seule qui porte une ligne et des arrêts. */
  data class Transit(
    override val startTime: Instant,
    override val endTime: Instant,
    override val scheduledStartTime: Instant,
    override val scheduledEndTime: Instant,
    override val duration: Duration,
    override val from: Place,
    override val to: Place,
    override val distanceMeters: Double? = null,
    override val geometry: List<LatLon> = emptyList(),
    override val realTime: Boolean = false,
    override val cancelled: Boolean = false,
    override val alerts: List<Disruption> = emptyList(),
    val mode: TransitMode = TransitMode.OTHER,
    /** Identifiant de la course, à passer à `/api/v6/trip`. Nul sur une course ajoutée en temps réel. */
    val tripId: String? = null,
    /** Libellé de ligne arbitré par le serveur (`displayName`). */
    val lineName: String = "",
    /** Numéro court de la ligne (`routeShortName`), quand il diffère de [lineName]. */
    val routeShortName: String? = null,
    /** Girouette du véhicule (`headsign`) : la direction annoncée à l'usager. */
    val headsign: String? = null,
    val agencyName: String? = null,
    /** Couleur de la ligne, au format `#RRGGBB`. */
    val routeColor: String? = null,
    /** Couleur du texte à poser sur [routeColor], au format `#RRGGBB`. */
    val routeTextColor: String? = null,
    /** Arrêts desservis entre [from] et [to], exclus. Vide si la requête ne les a pas demandés. */
    val intermediateStops: List<StopVisit> = emptyList(),
    /** Nul quand le transporteur ne publie pas l'information (à ne pas confondre avec « non accessible »). */
    val wheelchairAccessible: WheelchairAccess? = null,
    /** Nul quand le transporteur ne publie pas l'information. */
    val bikesAllowed: Boolean? = null,
    /** Vrai si l'usager reste dans le même véhicule qu'à la portion précédente, malgré un changement de ligne. */
    val interlineWithPreviousLeg: Boolean = false,
  ) : JourneyLeg

  /** Portion à pied, y compris les cheminements de correspondance. */
  data class Walk(
    override val startTime: Instant,
    override val endTime: Instant,
    override val scheduledStartTime: Instant,
    override val scheduledEndTime: Instant,
    override val duration: Duration,
    override val from: Place,
    override val to: Place,
    override val distanceMeters: Double? = null,
    override val geometry: List<LatLon> = emptyList(),
    override val realTime: Boolean = false,
    override val cancelled: Boolean = false,
    override val alerts: List<Disruption> = emptyList(),
    /** Instructions pas-à-pas. Vide hors de l'écran de détail (SPEC.md § 7.6). */
    val steps: List<TravelStep> = emptyList(),
  ) : JourneyLeg

  /** Portion à vélo personnel. Un vélo en libre-service est un [Rental]. */
  data class Bike(
    override val startTime: Instant,
    override val endTime: Instant,
    override val scheduledStartTime: Instant,
    override val scheduledEndTime: Instant,
    override val duration: Duration,
    override val from: Place,
    override val to: Place,
    override val distanceMeters: Double? = null,
    override val geometry: List<LatLon> = emptyList(),
    override val realTime: Boolean = false,
    override val cancelled: Boolean = false,
    override val alerts: List<Disruption> = emptyList(),
    val steps: List<TravelStep> = emptyList(),
  ) : JourneyLeg

  /** Portion en voiture, y compris les variantes `HGV`, `CAR_PARKING` et `CAR_DROPOFF`. */
  data class Car(
    override val startTime: Instant,
    override val endTime: Instant,
    override val scheduledStartTime: Instant,
    override val scheduledEndTime: Instant,
    override val duration: Duration,
    override val from: Place,
    override val to: Place,
    override val distanceMeters: Double? = null,
    override val geometry: List<LatLon> = emptyList(),
    override val realTime: Boolean = false,
    override val cancelled: Boolean = false,
    override val alerts: List<Disruption> = emptyList(),
    val steps: List<TravelStep> = emptyList(),
  ) : JourneyLeg

  /** Portion en véhicule partagé : vélo, trottinette, scooter ou voiture en libre-service. */
  data class Rental(
    override val startTime: Instant,
    override val endTime: Instant,
    override val scheduledStartTime: Instant,
    override val scheduledEndTime: Instant,
    override val duration: Duration,
    override val from: Place,
    override val to: Place,
    override val distanceMeters: Double? = null,
    override val geometry: List<LatLon> = emptyList(),
    override val realTime: Boolean = false,
    override val cancelled: Boolean = false,
    override val alerts: List<Disruption> = emptyList(),
    /** Le système emprunté. Nul si le serveur n'a pas rempli le bloc `rental`. */
    val rental: RentalInfo? = null,
    val steps: List<TravelStep> = emptyList(),
  ) : JourneyLeg
}
