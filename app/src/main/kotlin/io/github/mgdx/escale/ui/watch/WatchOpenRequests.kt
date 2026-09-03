package io.github.mgdx.escale.ui.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Une demande d'ouverture d'un trajet surveillé, venue d'un appui sur sa notification.
 *
 * [token] change à chaque demande, y compris pour le même trajet : sans lui, deux appuis successifs
 * sur la même notification ne produiraient qu'une seule ouverture — une `StateFlow` ne republie pas
 * une valeur égale. C'est la leçon déjà tirée par `StopDepartureRequests` et par l'ouverture du
 * détail depuis la feuille de résultats.
 */
data class WatchOpenRequest(val journeyId: Long, val token: Long)

/**
 * Le trajet surveillé que l'usager demande à ouvrir (SPEC.md § 5.5.1, « un appui ouvre le détail du
 * trajet rafraîchi »).
 *
 * **C'est un événement à consommation unique, pas un état.** La distinction est ce qui fait tenir
 * la fonction : la notification est lue une fois, la navigation a lieu une fois, et une rotation
 * de l'écran ne rouvre pas le trajet. Même dispositif que `StopDepartureRequests`
 * (docs/architecture.md § 11.4) : l'émetteur dépose, la navigation consomme.
 *
 * Rien n'est persisté ni journalisé : un identifiant de favori n'est pas une donnée de
 * localisation, mais il n'a pas non plus de raison de survivre au processus.
 */
class WatchOpenRequests {

  private val state = MutableStateFlow<WatchOpenRequest?>(null)

  /** La dernière demande non encore consommée. */
  val request: StateFlow<WatchOpenRequest?> = state.asStateFlow()

  private var tokens = 0L

  /** Dépose une demande, en remplaçant celle qui n'aurait pas été consommée. */
  fun open(journeyId: Long) {
    tokens += 1
    state.value = WatchOpenRequest(journeyId = journeyId, token = tokens)
  }

  /** Acquitte la demande : appelée une fois le trajet ouvert, ou l'ouverture abandonnée. */
  fun consume() {
    state.value = null
  }
}
