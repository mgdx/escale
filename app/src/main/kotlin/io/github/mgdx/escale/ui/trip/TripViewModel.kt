package io.github.mgdx.escale.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.query.RealtimeRefreshPolicy
import io.github.mgdx.escale.core.repository.TripRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Le détail d'une course (SPEC.md § 5.3, « écran Détail de la course avec la desserte complète »).
 *
 * **Ce qui se joue ici, et nulle part ailleurs :**
 *
 * - **La géométrie n'est pas demandée.** `detailedLegs=false` divise par sept la taille de la
 *   réponse — 55 ko contre 8 ko sur un ICE Hambourg → Nuremberg — et cet écran liste des arrêts,
 *   il ne trace rien : le tracé d'un trajet appartient à l'écran de détail et à la carte
 *   (SPEC.md § 7.6). Le paramètre est posé par `StopTimesQueryBuilder`, ce `ViewModel` ne fait que
 *   réclamer la variante sommaire.
 * - **La course se redemande entière, il n'y a pas de rafraîchissement partiel.** `/api/v6/trip`
 *   reconstruit la desserte avec le temps réel du moment à partir du seul `tripId` : rafraîchir,
 *   c'est refaire la même requête. Il n'y a donc qu'un seul code réseau à lire et à vérifier.
 *
 * Aucun polling (SPEC.md § 7.4) : les seuls déclencheurs sont l'ouverture de l'écran, l'appui de
 * l'usager sur « Rafraîchir », et le retour au premier plan sur des horaires de plus de 60
 * secondes — la même règle que partout ailleurs, tenue par le même `RealtimeRefreshPolicy`.
 *
 * Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et **ne journalise rien** :
 * la desserte d'une course dit quels arrêts l'usager consulte (SPEC.md § 8 et § 11).
 */
class TripViewModel(
  private val tripId: String,
  lineName: String,
  headsign: String,
  private val tripRepository: TripRepository,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(TripUiState(lineName = lineName, headsign = headsign))

  val uiState: StateFlow<TripUiState> = state.asStateFlow()

  /** Au plus une requête en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  init {
    load()
  }

  /** Le bouton « Rafraîchir » de la barre et le bouton « Réessayer » du bandeau d'erreur (§ 8). */
  fun onRefresh() = load()

  /** Le retour au premier plan (SPEC.md § 7.4), jugé par `RealtimeRefreshPolicy` comme ailleurs. */
  fun onForeground() {
    if (state.value.loading) return
    if (RealtimeRefreshPolicy.shouldRefreshOnForeground(state.value.loadedAt, now())) load()
  }

  /**
   * (Re)charge la desserte.
   *
   * La desserte affichée n'est pas vidée : un rafraîchissement qui échoue doit laisser les arrêts
   * précédents à l'écran plutôt qu'une page blanche (SPEC.md § 8).
   */
  private fun load() {
    work?.cancel()
    work = viewModelScope.launch {
      val first = state.value.journey == null
      state.update { it.copy(loading = first, refreshing = !first, error = null) }
      // La géométrie ne sert à rien ici : cet écran liste des arrêts (SPEC.md § 7.6).
      when (val outcome = tripRepository.trip(tripId, detailedLegs = false)) {
        is Outcome.Success -> onLoaded(outcome.value)
        is Outcome.Failure -> onFailure(outcome.error)
      }
    }
  }

  private fun onLoaded(journey: Journey) {
    val leg = journey.legs.filterIsInstance<JourneyLeg.Transit>().firstOrNull()
    state.update { current ->
      current.copy(
        journey = journey,
        // Le serveur fait foi dès qu'il a répondu ; ce que la navigation avait transmis n'était
        // qu'un titre provisoire, pour ne pas afficher un écran anonyme pendant la requête.
        lineName = leg?.lineName?.takeIf(String::isNotBlank) ?: current.lineName,
        headsign = leg?.headsign?.takeIf(String::isNotBlank) ?: current.headsign,
        loading = false,
        refreshing = false,
        error = null,
        loadedAt = now(),
      )
    }
  }

  private fun onFailure(error: EscaleError) {
    // Une requête supplantée n'est jamais montrée : un résultat plus récent arrive derrière
    // (docs/architecture.md § 6).
    if (error == EscaleError.Superseded) return
    state.update { it.copy(loading = false, refreshing = false, error = error) }
  }

  companion object {
    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        // La course vient de la route typée : c'est un objet public du réseau, pas une donnée de
        // l'usager, et `TripRoute` dit pourquoi elle a le droit d'y figurer.
        val route = createSavedStateHandle().toRoute<TripRoute>()
        TripViewModel(
          tripId = route.tripId,
          lineName = route.lineName,
          headsign = route.headsign,
          tripRepository = container.tripRepository,
        )
      }
    }
  }
}
