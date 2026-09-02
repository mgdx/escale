package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.result.EscaleError
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue

/**
 * Les règles du rafraîchissement d'un trajet déjà obtenu (SPEC.md § 5.3 et § 5.5.1).
 *
 * L'écran de détail s'en sert deux fois : à son ouverture, pour obtenir le détail complet des
 * portions que la liste de résultats n'a pas demandé (`detailedLegs=false`, SPEC.md § 7.6), et
 * ensuite chaque fois que l'usager appuie sur « Rafraîchir ».
 *
 * Tout est ici, en Kotlin pur, pour être vérifiable en JVM (docs/architecture.md § 1) : un repli
 * réseau qui ne se déclenche pas au bon moment ne se voit pas à l'écran, il se voit dans un test.
 */
object JourneyRefresh {

  /**
   * Vrai quand l'échec de `/api/v6/refresh-itinerary` impose de rejouer la requête `plan`
   * d'origine (SPEC.md § 5.5.1, « repli obligatoire »).
   *
   * L'identifiant d'itinéraire est marqué « expérimental » côté MOTIS : son format peut changer, et
   * il devient invalide après une mise à jour d'horaires côté serveur. Deux réponses le signalent :
   * - **400 / 422**, traduits en [EscaleError.BadRequest] : le serveur refuse l'identifiant ;
   * - **404**, que `HttpFailures` traduit en [EscaleError.ApiVersionTooOld] parce que le chemin
   *   commence par `/api/v6/`. Le repli est tenté quand même : si le serveur est réellement trop
   *   ancien, la requête `plan` échouera de la même façon et c'est **son** erreur qui s'affichera,
   *   ce qui est le message juste. Deviner ici lequel des deux cas s'est produit demanderait une
   *   information que la réponse ne donne pas.
   *
   * Tout le reste — pas de réseau, expiration, 5xx — est une panne passagère : rejouer une requête
   * plus lourde n'y changerait rien, et SPEC.md § 7.8 interdit d'insister.
   */
  fun invalidatesItineraryId(error: EscaleError): Boolean = when (error) {
    is EscaleError.BadRequest, is EscaleError.ApiVersionTooOld -> true

    EscaleError.NoNetwork,
    EscaleError.HostNotFound,
    EscaleError.Timeout,
    EscaleError.Superseded,
    is EscaleError.ServerUnreachable,
    is EscaleError.Unknown,
    -> false
  }

  /**
   * Le trajet de [journeys] dont l'heure de départ est la plus proche de [reference], ou `null` si
   * la liste est vide.
   *
   * C'est ce que SPEC.md § 5.5.1 demande après un repli sur `plan` : le serveur a recalculé toute
   * une page, et il faut y retrouver le trajet que l'usager regardait. À égalité d'écart, le
   * premier de la liste l'emporte — l'ordre du serveur fait alors foi.
   */
  fun closestToDeparture(journeys: List<Journey>, reference: Instant): Journey? =
    journeys.minByOrNull { Duration.between(reference, it.startTime).toMillis().absoluteValue }

  /**
   * L'onglet de résultats dont relève [journey] (SPEC.md § 5.2).
   *
   * Le repli sur `plan` doit rejouer **la requête de l'onglet d'où vient le trajet** : une portion
   * en transport en commun ne se retrouve pas dans une requête `directModes=WALK`. La catégorie
   * n'est pas transportée par le domaine — un trajet ne sait pas d'où il vient — elle se relit donc
   * dans ses portions.
   *
   * L'ordre des tests suit celui des onglets : un rabattement à vélo vers une gare relève de
   * l'onglet Transport, pas de l'onglet Vélo.
   */
  fun categoryOf(journey: Journey): JourneyCategory = when {
    journey.legs.any { it is JourneyLeg.Transit } -> JourneyCategory.TRANSIT
    journey.legs.any { it is JourneyLeg.Car } -> JourneyCategory.CAR
    journey.legs.any { it is JourneyLeg.Bike || it is JourneyLeg.Rental } -> JourneyCategory.BIKE
    else -> JourneyCategory.WALK
  }
}
