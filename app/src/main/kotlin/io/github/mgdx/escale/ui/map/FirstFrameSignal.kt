package io.github.mgdx.escale.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le témoin de la première image rendue par la carte.
 *
 * **Ceci est un instrument de mesure, pas une règle métier.** Rien dans l'application ne dépend de
 * cet état : aucun écran ne l'attend, aucune donnée ne s'y accroche, et le supprimer ne changerait
 * pas une seule image à l'écran. Il n'existe que pour l'objectif chiffré de SPEC.md § 5.7 —
 * « démarrage à froid jusqu'à la première image de carte : moins de 1,5 s » — que
 * `Activity.reportFullyDrawn()` est le seul moyen de mesurer honnêtement : `am start -W` rend la
 * première image de l'**activité**, or la carte s'initialise après elle.
 *
 * Deux propriétés font tout l'intérêt de cette classe, et ce sont les deux que le test couvre :
 *
 * 1. **Une bascule, et une seule.** L'écouteur MapLibre se déclenche à *chaque* image ; [markRendered]
 *    ne rend `true` qu'au premier appel, ce qui donne à l'appelant le signal exact pour se retirer.
 *    La règle 6 du § 5.7 protège la fluidité : on ne la dépense pas pour une mesure.
 * 2. **Un état qui dure, pas un événement.** La règle 8 du § 5.7 veut une carte unique pour toute la
 *    vie du processus : au second lancement de l'activité, l'image est déjà rendue et l'écouteur ne
 *    se redéclenchera jamais. Un `StateFlow` latché rend `true` immédiatement à qui le collecte,
 *    là où un `SharedFlow` d'événements laisserait l'activité attendre pour rien.
 */
internal class FirstFrameSignal {

  private val state = MutableStateFlow(false)

  /** Vrai dès que la carte a rendu sa première image, et pour toujours ensuite. */
  val rendered: StateFlow<Boolean> = state.asStateFlow()

  /**
   * Signale une image rendue.
   *
   * @return `true` si c'était la première — donc si l'écouteur qui appelle doit se retirer.
   */
  fun markRendered(): Boolean = state.compareAndSet(expect = false, update = true)
}
