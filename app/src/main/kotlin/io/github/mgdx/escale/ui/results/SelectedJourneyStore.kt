package io.github.mgdx.escale.ui.results

import io.github.mgdx.escale.core.model.Journey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le trajet mis en évidence sur la carte, publié pour qui saura le dessiner (SPEC.md § 5.1 :
 * « la carte reste visible en haut et cadre le trajet sélectionné »).
 *
 * **C'est un état, pas un événement.** La distinction est ce qui fait tenir le § 5.1 : la
 * sélection dure tant qu'il y a des résultats à montrer — elle survit à l'ouverture de l'écran de
 * détail comme au retour en arrière — et l'ouverture de cet écran est, elle, un événement à
 * consommation unique porté par `ResultsViewModel.openDetail`. Confondre les deux obligeait à
 * remettre ce magasin à `null` en quittant le détail, faute de quoi un second appui sur la même
 * carte de résultat n'aurait rien émis : une `StateFlow` ne republie pas une valeur égale. Le
 * tracé disparaissait alors dès le retour en arrière.
 *
 * ```kotlin
 * val journey by container.selectedJourneyStore.selected.collectAsStateWithLifecycle()
 * ```
 *
 * Rien n'est persisté ni journalisé : un trajet porte l'origine et la destination de l'usager,
 * que SPEC.md § 11 interdit d'écrire où que ce soit.
 */
class SelectedJourneyStore {

  private val state = MutableStateFlow<Journey?>(null)

  /**
   * Le trajet mis en évidence, `null` seulement tant qu'il n'y a aucun résultat à montrer.
   *
   * Dès qu'une liste de résultats arrive, son premier trajet est publié ici sans aucun appui :
   * c'est littéralement ce que décrit SPEC.md § 5.1.
   */
  val selected: StateFlow<Journey?> = state.asStateFlow()

  fun select(journey: Journey?) {
    state.value = journey
  }

  companion object {
    /**
     * L'unique magasin de l'application, exposé par `AppContainer` sous le nom
     * `selectedJourneyStore` : c'est par là que les écrans y accèdent, jamais par ce champ.
     */
    val shared: SelectedJourneyStore = SelectedJourneyStore()
  }
}
