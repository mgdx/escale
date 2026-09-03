package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.core.model.JourneyWatchLimit
import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.core.model.WatchSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** L'heure proposée quand le trajet affiché n'en fournit pas : un départ du matin plausible. */
val DEFAULT_DEPARTURE_TIME: LocalTime = LocalTime.of(DEFAULT_DEPARTURE_HOUR, 0)

private const val DEFAULT_DEPARTURE_HOUR = 8

/**
 * Valeur d'attente de [WatchUiState.singleDate], remplacée dès la construction de l'état par la
 * prochaine occurrence de l'heure choisie. `LocalDate.EPOCH` ferait la même chose, mais la
 * constante n'existe qu'à partir d'Android 14 : `ofEpochDay` est disponible depuis l'API 26.
 */
private val PLACEHOLDER_DATE: LocalDate = LocalDate.ofEpochDay(0)

/**
 * L'état de l'activation d'une surveillance (SPEC.md § 5.5.1).
 *
 * [favoriteId] nul veut dire « ce trajet n'est pas en favori » : la commande ne s'affiche alors
 * pas du tout, la spec ne proposant la surveillance que depuis un trajet favori. Ce n'est pas un
 * état d'erreur, c'est le cas le plus courant.
 */
data class WatchUiState(
  val favoriteId: Long? = null,
  val watched: Boolean = false,
  /** L'heure de départ habituelle en cours d'édition. */
  val time: LocalTime = DEFAULT_DEPARTURE_TIME,
  /** Les jours concernés. Vide : la surveillance vaut pour une date unique (voir [singleDate]). */
  val days: Set<DayOfWeek> = emptySet(),
  /** La date que prendra une surveillance sans récurrence, calculée d'après [time]. */
  val singleDate: LocalDate = PLACEHOLDER_DATE,
  val settings: WatchAlertSettings = WatchAlertSettings(),
  /** Nombre de trajets déjà surveillés, pour annoncer la limite avant de la heurter. */
  val watchedCount: Int = 0,
  /** Vrai quand la dernière demande a été refusée faute de place (`JourneyWatchLimit`). */
  val limitReached: Boolean = false,
  /** Faux quand `POST_NOTIFICATIONS` est refusée ou le canal éteint : la surveillance continue. */
  val notificationsAllowed: Boolean = true,
  val lastCheck: WatchCheckRecord? = null,
) {
  /** La limite de cinq est atteinte par d'autres trajets que celui-ci (`JourneyWatchLimit`). */
  val limitBlocking: Boolean
    get() = !watched && watchedCount >= JourneyWatchLimit.MAX_WATCHED

  /** La configuration à enregistrer : récurrente si des jours sont cochés, à date unique sinon. */
  val schedule: WatchSchedule
    get() = if (days.isEmpty()) {
      WatchSchedule(departureTime = time, date = singleDate)
    } else {
      WatchSchedule(departureTime = time, days = days)
    }
}
