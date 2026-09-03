package io.github.mgdx.escale.ui.watch

import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.core.model.WatchIssue
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/**
 * Ce qu'a donné la dernière vérification d'une surveillance (SPEC.md § 5.5.1).
 *
 * Elle existe pour une exigence précise de la spec : quand la permission de notification est
 * refusée, « la surveillance est proposée sans notification, **l'état étant alors visible à
 * l'ouverture de l'application** ». Sans cette trace, un refus rendrait la fonction muette.
 *
 * [issue] nul veut dire « rien à signaler » : c'est le cas le plus fréquent, et il mérite d'être
 * montré autant que les autres — savoir que la vérification a bien eu lieu fait partie de ce qui
 * rend la fonction crédible.
 */
data class WatchCheckRecord(
  val checkedAt: Instant,
  val issue: WatchIssue?,
  val lineName: String? = null,
  val delay: Duration? = null,
  val suggestedDeparture: Instant? = null,
  /** Faux quand la notification n'a pas pu être émise, faute de permission. */
  val notified: Boolean = false,
)

/**
 * Les réglages de la surveillance et le résultat de la dernière vérification (SPEC.md § 5.5.1).
 *
 * C'est une interface pour la même raison que `CleartextConsentStore` : elle décrit ce dont les
 * écrans et la tâche de fond ont besoin, sans les lier à DataStore, et rend leur logique
 * vérifiable en JVM (docs/architecture.md § 10).
 */
interface WatchSettingsStore {
  /** Le seuil de retard et « me prévenir même si tout va bien », avec leurs valeurs par défaut. */
  val settings: Flow<WatchAlertSettings>

  suspend fun update(settings: WatchAlertSettings)

  /** Le résultat de la dernière vérification de [journeyId], ou `null` s'il n'y en a pas eu. */
  fun lastCheck(journeyId: Long): Flow<WatchCheckRecord?>

  suspend fun record(journeyId: Long, record: WatchCheckRecord)

  /** Une surveillance arrêtée n'a plus de dernier résultat à montrer. */
  suspend fun forget(journeyId: Long)
}
