package io.github.mgdx.escale.core.follow

import io.github.mgdx.escale.core.format.transitLineLabel
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.TransitMode
import java.time.Duration
import java.time.Instant

/**
 * Un trajet réduit à ce que le suivi de trajet (SPEC.md § 5.3.1) a besoin de savoir.
 *
 * C'est la « forme réduite » que la spec impose au service : portions, arrêts, heures, libellés,
 * **sans géométrie ni instructions pas-à-pas**. Elle tient en quelques kilooctets, ce qui lui permet
 * de voyager dans l'intent qui démarre le service et d'y revenir si le système relance celui-ci.
 *
 * Les heures sont les heures **effectives**, temps réel appliqué quand il existe, telles que reçues
 * au dernier chargement ou rafraîchissement : c'est sur elles, et sur elles seules, que la
 * progression se calcule. Rien ici ne lit une position ni ne touche au réseau.
 *
 * @property key ce qui identifie le trajet suivi d'un rafraîchissement à l'autre : l'identifiant
 *   d'itinéraire quand le serveur en a donné un, une empreinte des heures sinon.
 * @property itineraryId l'identifiant d'itinéraire MOTIS, ou `null` s'il n'a pas été fourni. C'est
 *   par lui que l'écran de détail se rouvre depuis la notification après la mort du processus.
 */
data class FollowPlan(val key: String, val itineraryId: String?, val legs: List<FollowLeg>) {
  init {
    require(legs.isNotEmpty()) { "Un plan de suivi porte au moins une portion." }
  }

  val start: Instant
    get() = legs.first().start

  val end: Instant
    get() = legs.last().end

  /** Vrai dès qu'une portion est annulée : le suivi le dit, puis continue sur les heures connues. */
  val hasCancelledLeg: Boolean
    get() = legs.any { it.cancelled }

  companion object {
    /** La forme réduite de [journey], sous la clé [key] — celle du trajet suivi, par défaut la sienne. */
    fun of(journey: Journey, key: String = keyOf(journey)): FollowPlan = FollowPlan(
      key = key,
      itineraryId = journey.id,
      legs = journey.legs.map(::reduce),
    )

    /**
     * La clé sous laquelle un trajet se suit.
     *
     * L'identifiant d'itinéraire est marqué « expérimental » côté MOTIS et peut manquer : l'empreinte
     * de repli ne dit rien de plus que ce que la liste de résultats affiche déjà.
     */
    fun keyOf(journey: Journey): String =
      journey.id ?: "${journey.startTime.epochSecond}-${journey.endTime.epochSecond}-${journey.legs.size}"

    private fun reduce(leg: JourneyLeg): FollowLeg = when (leg) {
      is JourneyLeg.Transit -> FollowLeg.Transit(
        start = leg.startTime,
        end = leg.endTime,
        fromName = leg.from.name,
        toName = leg.to.name,
        cancelled = leg.cancelled,
        mode = leg.mode,
        lineLabel = transitLineLabel(leg),
        headsign = leg.headsign?.takeIf(String::isNotBlank),
        track = leg.from.track?.takeIf(String::isNotBlank),
        stops = leg.intermediateStops.map { visit ->
          FollowStop(
            name = visit.place.name,
            // Le départ de l'arrêt est l'instant où l'on cesse d'y être : c'est lui qui compte.
            // Un terminus n'en a pas, son arrivée en tient lieu ; sans aucune heure, le départ de
            // la portion évite un trou dans la chronologie.
            time = visit.departure ?: visit.arrival ?: leg.startTime,
            cancelled = visit.cancelled,
          )
        },
      )

      is JourneyLeg.Walk -> street(leg, StreetKind.WALK)

      is JourneyLeg.Bike -> street(leg, StreetKind.BIKE)

      is JourneyLeg.Car -> street(leg, StreetKind.CAR)

      is JourneyLeg.Rental -> street(leg, StreetKind.RENTAL)
    }

    private fun street(leg: JourneyLeg, kind: StreetKind) = FollowLeg.Street(
      start = leg.startTime,
      end = leg.endTime,
      fromName = leg.from.name,
      toName = leg.to.name,
      cancelled = leg.cancelled,
      kind = kind,
      duration = leg.duration,
    )
  }
}

/** Une portion du plan de suivi : en véhicule de transport en commun, ou dans la rue. */
sealed interface FollowLeg {
  val start: Instant
  val end: Instant
  val fromName: String
  val toName: String
  val cancelled: Boolean

  /** La seule portion qui compte des arrêts : c'est sur elle que portent les annonces de descente. */
  data class Transit(
    override val start: Instant,
    override val end: Instant,
    override val fromName: String,
    override val toName: String,
    override val cancelled: Boolean,
    val mode: TransitMode,
    /** Le libellé de ligne arbitré par `transitLineLabel`, ou `null` si le serveur ne la nomme pas. */
    val lineLabel: String?,
    val headsign: String?,
    /** Le quai de montée, quand il est connu. */
    val track: String?,
    /** Les arrêts desservis entre la montée et la descente, **arrêts supprimés compris**, marqués. */
    val stops: List<FollowStop>,
  ) : FollowLeg

  /** Une portion parcourue par l'usager lui-même : marche, vélo, voiture, libre-service. */
  data class Street(
    override val start: Instant,
    override val end: Instant,
    override val fromName: String,
    override val toName: String,
    override val cancelled: Boolean,
    val kind: StreetKind,
    val duration: Duration,
  ) : FollowLeg
}

/** Un arrêt intermédiaire, réduit à son nom, à l'instant où le véhicule le quitte, et à son sort. */
data class FollowStop(val name: String, val time: Instant, val cancelled: Boolean)

enum class StreetKind { WALK, BIKE, CAR, RENTAL }
