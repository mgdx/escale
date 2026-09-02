package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.Journey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le trajet choisi dans la liste de résultats, publié pour qui saura le dessiner.
 *
 * SPEC.md § 5.1 veut que la carte cadre le trajet sélectionné, mais le tracé relève du lot
 * « tracé » du sprint suivant : `MapInstance` n'expose aujourd'hui rien pour le recevoir. Le lot
 * « résultats » ne va donc pas écrire dans `ui/map`. Il publie le trajet ici, dans son propre
 * paquet, sous une forme que le lot suivant consommera sans rien changer à cet écran :
 *
 * ```kotlin
 * val journey by SelectedJourneyStore.shared.selected.collectAsStateWithLifecycle()
 * ```
 *
 * **Ce que le lot suivant devra faire** : déplacer [shared] dans `AppContainer`, comme
 * `MapSelection` et `SearchSession`, et injecter l'instance par le constructeur des deux
 * `ViewModel`. Le point d'entrée singleton n'existe que parce que ce lot n'a pas le droit de
 * toucher à `AppContainer` ; le reste du code est déjà écrit pour ce déplacement, puisque
 * `ResultsViewModel` reçoit son magasin par son constructeur.
 *
 * Rien n'est persisté ni journalisé : un trajet porte l'origine et la destination de l'usager,
 * que SPEC.md § 11 interdit d'écrire où que ce soit.
 */
class SelectedJourneyStore {

  private val state = MutableStateFlow<Journey?>(null)

  /** Le trajet choisi, `null` tant qu'aucun ne l'est ou dès qu'une nouvelle recherche part. */
  val selected: StateFlow<Journey?> = state.asStateFlow()

  fun select(journey: Journey?) {
    state.value = journey
  }

  companion object {
    /** L'unique magasin de l'application, en attendant sa place dans `AppContainer`. */
    val shared: SelectedJourneyStore = SelectedJourneyStore()
  }
}
