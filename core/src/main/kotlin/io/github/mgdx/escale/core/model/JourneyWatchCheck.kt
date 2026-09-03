package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.WatchedJourneysRepository
import io.github.mgdx.escale.core.result.Outcome
import java.time.Instant

/** Ce qu'une vérification de surveillance a produit (SPEC.md § 5.5.1). */
sealed interface WatchCheck {
  /** Le trajet a été consulté il y a moins de trente minutes : la donnée est déjà fraîche. */
  data object Skipped : WatchCheck

  /** Le serveur a répondu, et il n'y a aucune différence utile à annoncer. */
  data object NothingToReport : WatchCheck

  /**
   * Il y a quelque chose à dire.
   *
   * [journey] est nul dans le seul cas où le serveur n'a plus aucun trajet à proposer : la
   * notification n'ouvre alors aucun détail, puisqu'il n'y en a pas.
   */
  data class Notify(val notice: WatchNotice, val journey: Journey?) : WatchCheck

  /**
   * La requête a échoué pour une raison passagère.
   *
   * Ce n'est **pas** un message : « un échec du repli n'affiche rien ». L'appelant a droit à une
   * unique reprise cinq minutes plus tard (`WatchPlanning.shouldRetry`), puis se tait.
   */
  data object Failed : WatchCheck
}

/**
 * La vérification d'un trajet surveillé, une heure avant son départ (SPEC.md § 5.5.1).
 *
 * **Tout est ici plutôt que dans la tâche `WorkManager`**, et pour une raison précise : ce code
 * s'exécute sans témoin, dans un processus que personne ne regarde. Écrit dans un `Worker`, il ne
 * serait vérifiable que sur un appareil, à l'heure dite. Écrit ici, sur les interfaces de dépôt de
 * `:core`, il se vérifie en JVM, cas par cas (docs/architecture.md § 1). La tâche ne garde que ce
 * qui est irréductiblement Android : la planification et la notification.
 *
 * Cette classe **ne journalise rien** : elle manipule un départ, une arrivée et une heure, c'est-à-
 * dire exactement ce que SPEC.md § 11 interdit d'écrire dans une trace, y compris en débogage.
 */
class JourneyWatchCheck(private val plans: PlanRepository, private val watched: WatchedJourneysRepository) {

  /**
   * Vérifie [watch] pour l'occurrence qui part à [departure].
   *
   * @param favorite le trajet favori surveillé : il porte le départ, l'arrivée et l'onglet, donc
   *   tout ce qu'il faut pour **rejouer la requête `plan` d'origine** en cas de repli.
   * @param preferences les réglages de recherche du moment. Ils sont passés et non relus ici :
   *   `:core` ne lit pas de préférences.
   */
  suspend fun run(
    watch: WatchedJourney,
    favorite: FavoriteJourney,
    departure: Instant,
    now: Instant,
    settings: WatchAlertSettings = WatchAlertSettings(),
    preferences: SearchPreferences = SearchPreferences(),
  ): WatchCheck {
    if (WatchPlanning.isFresh(watch.lastViewedAt, now)) return WatchCheck.Skipped
    val outcome = locate(watch, favorite, departure, preferences)
    if (outcome is Outcome.Failure) return WatchCheck.Failed
    val journey = (outcome as Outcome.Success).value
    // L'identifiant d'itinéraire est une optimisation périssable : celui que le serveur vient de
    // rendre remplace celui qu'il a refusé, et son absence efface l'ancien plutôt que de le
    // laisser resservir (SPEC.md § 5.5.1).
    if (journey?.id != watch.itineraryId) watched.rememberItinerary(watch.journeyId, journey?.id, now)
    val notice = JourneyWatchComparison.compare(departure, journey, settings)
    return if (notice == null) WatchCheck.NothingToReport else WatchCheck.Notify(notice, journey)
  }

  /**
   * Le trajet rafraîchi : `refresh-itinerary` d'abord, la requête `plan` d'origine ensuite.
   *
   * Publique parce qu'elle sert **deux fois** : à la vérification de fond ci-dessus, et à
   * l'ouverture du trajet depuis la notification (SPEC.md § 5.5.1, « un appui ouvre le détail du
   * trajet rafraîchi »). Les deux chemins doivent obtenir le trajet de la même façon, repli
   * compris : les dupliquer, c'est les laisser diverger.
   *
   * **Une seule requête par occurrence dans le cas normal** (SPEC.md § 5.5.1) : la seconde ne part
   * que si la première a été refusée, ce que `JourneyRefresh.invalidatesItineraryId` décide — 400
   * et 422 pour un identifiant rejeté, 404 que `HttpFailures` traduit en `ApiVersionTooOld` parce
   * que le chemin commence par `/api/v6/`. Une panne passagère, elle, n'autorise pas à insister
   * avec une requête plus lourde (SPEC.md § 7.8).
   *
   * Un succès portant `null` n'est pas un échec : c'est « le serveur ne propose plus rien », que la
   * comparaison traduira en « trajet devenu impossible ».
   */
  suspend fun locate(
    watch: WatchedJourney,
    favorite: FavoriteJourney,
    departure: Instant,
    preferences: SearchPreferences,
  ): Outcome<Journey?> {
    val itineraryId = watch.itineraryId
    if (itineraryId != null) {
      val outcome = plans.refresh(itineraryId, detailedLegs = false)
      if (outcome is Outcome.Success) return outcome
      val error = (outcome as Outcome.Failure).error
      if (!JourneyRefresh.invalidatesItineraryId(error)) return outcome
    }
    return replanned(favorite, departure, preferences)
  }

  /**
   * Le repli obligatoire : rejouer la requête `plan` d'origine et retenir le trajet le plus proche
   * en heure de départ (SPEC.md § 5.5.1).
   *
   * `detailedLegs` reste **faux** : la notification n'a besoin ni des arrêts intermédiaires, ni des
   * instructions pas-à-pas, ni de la géométrie, et une réponse plus légère est une requête moins
   * coûteuse pour l'instance publique (SPEC.md § 7.6). Le détail complet sera demandé si — et
   * seulement si — l'usager ouvre le trajet.
   *
   * `fresh = true` : la vérification a pour objet le temps réel du moment. Servir une réponse mise
   * en cache reviendrait à annoncer un état périmé, ce qui est exactement ce que la surveillance
   * doit éviter.
   */
  private suspend fun replanned(
    favorite: FavoriteJourney,
    departure: Instant,
    preferences: SearchPreferences,
  ): Outcome<Journey?> {
    val query = SearchQuery(
      from = favorite.from,
      to = favorite.to,
      time = TimeChoice.DepartAt(departure),
      category = favorite.category,
      preferences = preferences,
    )
    return when (val outcome = plans.plan(query, cursor = null, detailedLegs = false, fresh = true)) {
      is Outcome.Failure -> outcome

      is Outcome.Success -> Outcome.Success(
        JourneyRefresh.closestToDeparture(outcome.value.journeys + outcome.value.direct, departure),
      )
    }
  }
}
