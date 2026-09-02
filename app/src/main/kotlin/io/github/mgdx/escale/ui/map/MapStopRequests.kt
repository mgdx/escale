package io.github.mgdx.escale.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Un arrêt dont l'usager a demandé les prochains départs depuis la carte (SPEC.md § 5.4 et § 5.7).
 *
 * [token] change à chaque demande : sans lui, redemander deux fois le même arrêt ne déclencherait
 * rien la seconde fois.
 */
data class StopDepartureRequest(val stopId: String, val stopName: String, val token: Long)

/**
 * **Le point d'accroche du jalon 9**, l'écran « prochains départs ».
 *
 * SPEC.md § 5.7 demande que l'infobulle d'un arrêt porte « un bouton menant aux prochains départs
 * (§ 5.4) ». Cet écran n'existe pas encore, et le lot « carte » n'a pas à l'écrire. Plutôt que de
 * laisser un bouton mort, l'appui dépose ici l'arrêt demandé, exactement comme [MapSelection]
 * dépose un point choisi pour l'écran de recherche.
 *
 * Ce qui reste à faire au jalon 9, et rien d'autre :
 *
 * 1. déclarer `DeparturesRoute(stopId)` dans son propre paquet `ui.departures` ;
 * 2. dans `EscaleNavHost.kt`, observer `AppContainer.stopDepartureRequests.request`, naviguer vers
 *    cette route, puis appeler [consume] ;
 * 3. l'infobulle de la carte n'a pas à changer d'une ligne.
 *
 * Tant que personne ne consomme les demandes, la dernière remplace la précédente et rien ne fuit :
 * aucun identifiant d'arrêt n'est journalisé ni persisté (SPEC.md § 8 et § 11).
 */
class StopDepartureRequests {

  private val state = MutableStateFlow<StopDepartureRequest?>(null)

  /** La dernière demande non encore consommée. */
  val request: StateFlow<StopDepartureRequest?> = state.asStateFlow()

  private var tokens = 0L

  /** Dépose une demande, en remplaçant celle qui n'aurait pas été consommée. */
  fun request(stopId: String, stopName: String) {
    tokens += 1
    state.value = StopDepartureRequest(stopId = stopId, stopName = stopName, token = tokens)
  }

  /** Acquitte la demande : appelée par le lot qui l'a consommée. */
  fun consume() {
    state.value = null
  }
}
