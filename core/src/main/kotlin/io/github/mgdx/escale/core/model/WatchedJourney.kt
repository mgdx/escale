package io.github.mgdx.escale.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Quand un trajet surveillé doit être vérifié (SPEC.md § 5.5.1).
 *
 * Deux formes, et deux seulement :
 * - **récurrente** : [days] non vide, [date] nulle — « lundi à vendredi, 8 h 10 » ;
 * - **à date unique** : [days] vide, [date] renseignée — la surveillance « vaut pour une date
 *   unique puis se désactive d'elle-même ».
 *
 * L'heure est une [LocalTime] et non un [Instant] : « 8 h 10 » est une heure de la vie de l'usager,
 * pas un point sur la ligne du temps. La convertir en instant à l'enregistrement la figerait à un
 * fuseau et à un état de l'heure d'été qui ne seront plus ceux du jour surveillé.
 */
data class WatchSchedule(
  val departureTime: LocalTime,
  /** Jours de la semaine concernés. Vide quand la surveillance vaut pour une date unique. */
  val days: Set<DayOfWeek> = emptySet(),
  /** Date unique, quand il n'y a pas de récurrence. Nulle quand [days] est renseigné. */
  val date: LocalDate? = null,
) {
  val isRecurring: Boolean get() = days.isNotEmpty()
}

/**
 * Un trajet favori sous surveillance (SPEC.md § 5.5.1).
 *
 * Ce type n'emporte aucune planification : c'est l'état persisté, que le lot des notifications
 * lira pour programmer sa tâche `WorkManager` à T − 60 minutes.
 */
data class WatchedJourney(
  /** Identifiant du [FavoriteJourney] surveillé. Un favori supprimé emporte sa surveillance. */
  val journeyId: Long,
  val schedule: WatchSchedule,
  /**
   * L'`id` d'itinéraire à rejouer avec `refresh-itinerary`.
   *
   * Nul tant qu'aucun itinéraire n'a été retenu. MOTIS le marque « expérimental » : son format peut
   * changer, et il devient invalide après une mise à jour d'horaires côté serveur. Il est donc
   * enregistré comme une **optimisation**, jamais comme la seule façon de retrouver le trajet — le
   * repli de SPEC.md § 5.5.1 rejoue la requête `plan` d'origine, que [journeyId] permet de
   * reconstruire.
   */
  val itineraryId: String? = null,
  /** Quand [itineraryId] a été retenu, pour savoir s'il vaut encore la peine d'être essayé. */
  val itineraryCapturedAt: Instant? = null,
  /**
   * Dernière consultation du trajet dans l'application.
   *
   * SPEC.md § 5.5.1 : « la surveillance ne s'exécute pas si le trajet a déjà été consulté dans
   * l'application dans les 30 dernières minutes ». Sans cette date, la règle n'est pas applicable.
   */
  val lastViewedAt: Instant? = null,
  val createdAt: Instant,
)

/** Ce que le stockage répond à une demande de surveillance. */
enum class WatchDecision {
  ACCEPTED,

  /** Cinq trajets sont déjà surveillés : l'interface le dit, elle ne supprime rien d'elle-même. */
  LIMIT_REACHED,

  /** Ni jour de la semaine, ni date : la surveillance ne pourrait jamais être programmée. */
  INVALID_SCHEDULE,
}

/**
 * La limite de 5 trajets surveillés simultanés (SPEC.md § 5.5.1).
 *
 * Elle vit ici, en Kotlin pur, et pas dans l'écran qui proposera la bascule : un plafond appliqué
 * par l'affichage est un plafond qu'une autre entrée — un lien profond, une restauration, un futur
 * écran de réglages — contourne sans le savoir. Le dépôt l'applique à l'écriture, l'interface s'en
 * sert pour griser la bascule avant même de la proposer.
 */
object JourneyWatchLimit {
  /** « Limite de 5 trajets surveillés simultanés, pour que la fonction reste frugale. » */
  const val MAX_WATCHED = 5

  /**
   * Décide si [journeyId] peut être surveillé selon [schedule], sachant [watchedIds] déjà surveillés.
   *
   * Reconfigurer un trajet **déjà** surveillé reste possible une fois la limite atteinte : cela ne
   * crée pas de sixième surveillance, et refuser à quelqu'un de corriger son heure de départ parce
   * qu'il en a cinq serait absurde.
   */
  fun decide(watchedIds: Collection<Long>, journeyId: Long, schedule: WatchSchedule): WatchDecision = when {
    !schedule.isRecurring && schedule.date == null -> WatchDecision.INVALID_SCHEDULE
    journeyId in watchedIds -> WatchDecision.ACCEPTED
    watchedIds.toSet().size >= MAX_WATCHED -> WatchDecision.LIMIT_REACHED
    else -> WatchDecision.ACCEPTED
  }
}
