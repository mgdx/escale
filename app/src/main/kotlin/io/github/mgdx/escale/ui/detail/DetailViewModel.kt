package io.github.mgdx.escale.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.stationFor
import io.github.mgdx.escale.core.model.withEndpointNames
import io.github.mgdx.escale.core.query.RealtimeRefreshPolicy
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * L'écran de détail d'un trajet (SPEC.md § 5.3).
 *
 * **Ce qui se joue ici, et nulle part ailleurs :**
 *
 * - **La requête détaillée de SPEC.md § 7.6.** La liste de résultats demande `detailedLegs=false`
 *   pour alléger la réponse : le trajet reçu n'a donc ni arrêts intermédiaires, ni instructions
 *   pas-à-pas, ni géométrie. Ils sont demandés **à l'ouverture de cet écran, et seulement là**,
 *   avec `detailedLegs=true` et `detailedTransfers=true` — c'est `PlanQueryBuilder` qui pose les
 *   deux paramètres, ce `ViewModel` ne fait que réclamer la variante détaillée.
 * - **Le repli obligatoire de SPEC.md § 5.5.1.** Le chemin le moins coûteux est
 *   `/api/v6/refresh-itinerary`, qui reconstruit le trajet à partir de son seul identifiant et
 *   rapporte au passage le temps réel du moment. Mais cet identifiant est marqué « expérimental »
 *   côté MOTIS et devient invalide après une mise à jour d'horaires : sur un refus (400/422) ou un
 *   404, la requête `plan` d'origine est rejouée et le trajet le plus proche en heure de départ
 *   est retenu. La règle est dans `JourneyRefresh`, testée en JVM.
 * - **Le même chemin sert au bouton « Rafraîchir »** de SPEC.md § 5.3 : rafraîchir, c'est
 *   redemander le trajet détaillé. Il n'y a donc qu'un seul code réseau à lire et à vérifier.
 *
 * Aucun polling (SPEC.md § 7.4) : les seuls déclencheurs sont l'ouverture de l'écran, l'appui de
 * l'usager sur « Rafraîchir », et le retour au premier plan sur des horaires de plus de 60
 * secondes — la même règle que la feuille de résultats, tenue par le même `RealtimeRefreshPolicy`.
 * Ni minuterie, ni boucle, ni tâche de fond. Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et
 * **ne journalise rien** : il manipule des adresses et des coordonnées (SPEC.md § 11).
 */
class DetailViewModel(
  private val selection: SelectedJourneyStore,
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val rentalsRepository: RentalsRepository,
  private val savedState: SavedStateHandle,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val state = MutableStateFlow(initialState())

  val uiState: StateFlow<DetailUiState> = state.asStateFlow()

  /** Au plus une requête détaillée en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  /** Au plus une requête de disponibilité en vol **par portion**, pour la même raison. */
  private val rentalWork = mutableMapOf<Int, Job>()

  init {
    if (!state.value.closed) load()
  }

  /**
   * Le bouton « Rafraîchir » (SPEC.md § 5.3) et le bouton « Réessayer » du bandeau (§ 8).
   *
   * Il rafraîchit aussi les disponibilités déjà affichées : elles sont plus périssables que les
   * horaires, et les laisser telles quelles pendant que le reste de l'écran se met à jour serait
   * exactement le mensonge que l'heure de relevé sert à éviter.
   */
  fun onRefresh() {
    load()
    state.value.rentals.keys.toList().forEach { loadRental(it, fresh = true) }
  }

  /** Le bouton de rafraîchissement propre à une portion en libre-service (SPEC.md § 5.3). */
  fun onRentalRefresh(index: Int) = loadRental(index, fresh = true)

  /**
   * Le retour au premier plan (SPEC.md § 7.4).
   *
   * Même règle que la feuille de résultats, et surtout le même juge : `RealtimeRefreshPolicy`,
   * dans `:core`, qui porte le seuil de 60 secondes et ses cas limites. Un écran encore en train
   * de charger n'en redemande pas.
   */
  fun onForeground() {
    if (state.value.loading) return
    if (RealtimeRefreshPolicy.shouldRefreshOnForeground(state.value.refreshedAt, now())) load()
  }

  fun onLegToggled(index: Int) {
    state.update { it.copy(expandedLegs = it.expandedLegs.toggled(index)) }
    remember(KEY_LEGS, state.value.expandedLegs)
    // La disponibilité n'est demandée qu'au dépliage : c'est le moment où elle s'affiche, et
    // SPEC.md § 7 interdit d'émettre une requête dont on ne montre pas encore la réponse. Un trajet
    // en transport en commun avec deux rabattements partagés n'en émet donc aucune tant que
    // l'usager n'ouvre pas la portion concernée.
    if (index in state.value.expandedLegs) loadRental(index, fresh = false)
  }

  fun onStopsToggled(index: Int) {
    state.update { it.copy(expandedStops = it.expandedStops.toggled(index)) }
    remember(KEY_STOPS, state.value.expandedStops)
  }

  fun onStepsToggled(index: Int) {
    state.update { it.copy(expandedSteps = it.expandedSteps.toggled(index)) }
    remember(KEY_STEPS, state.value.expandedSteps)
  }

  private fun load() {
    val current = state.value.journey ?: return
    work?.cancel()
    work = viewModelScope.launch {
      state.update { it.copy(loading = true, error = null) }
      when (val outcome = detailed(current)) {
        is Outcome.Success -> onDetailed(outcome.value)
        is Outcome.Failure -> onFailure(outcome.error)
      }
    }
  }

  /**
   * Le trajet détaillé, par le chemin le moins coûteux d'abord.
   *
   * Un trajet sans identifiant — le serveur ne le garantit pas — passe directement par le repli :
   * il n'y a rien à rafraîchir.
   */
  private suspend fun detailed(current: Journey): Outcome<Journey> {
    val itineraryId = current.id
    if (itineraryId != null) {
      val outcome = planRepository.refresh(itineraryId, detailedLegs = true)
      if (outcome is Outcome.Success) return outcome
      val error = (outcome as Outcome.Failure).error
      if (!JourneyRefresh.invalidatesItineraryId(error)) return outcome
    }
    return replanned(current)
  }

  /**
   * Le repli de SPEC.md § 5.5.1 : rejouer la requête `plan` d'origine, en détaillé cette fois, et
   * retenir le trajet le plus proche en heure de départ.
   *
   * La requête est reconstruite depuis `SearchSession`, qui détient toujours la recherche en cours,
   * et depuis la catégorie que `JourneyRefresh` relit dans les portions du trajet : un rabattement
   * à vélo vers une gare doit être rejoué sur l'onglet Transport, pas sur l'onglet Vélo.
   */
  private suspend fun replanned(current: Journey): Outcome<Journey> {
    val query = session.toQuery(JourneyRefresh.categoryOf(current), PREFERENCES)
      ?: return Outcome.Failure(EscaleError.Unknown(cause = NO_SEARCH))
    return when (val outcome = planRepository.plan(query, cursor = null, detailedLegs = true)) {
      is Outcome.Failure -> outcome

      is Outcome.Success -> {
        val page = outcome.value
        val closest = JourneyRefresh.closestToDeparture(page.journeys + page.direct, current.startTime)
        if (closest == null) Outcome.Failure(EscaleError.Unknown(cause = NO_MATCH)) else Outcome.Success(closest)
      }
    }
  }

  /**
   * La disponibilité des stations d'une portion en libre-service (SPEC.md § 5.3).
   *
   * **Deux stations, deux questions distinctes** : celle de prise répond « combien de véhicules »,
   * celle de retour « combien de places libres ». Elles sont donc interrogées séparément, chacune
   * autour de ses propres coordonnées. Quand les deux extrémités désignent la même station, la
   * seconde requête n'atteint pas le réseau : le cache de soixante secondes du dépôt reconnaît la
   * demande identique et rend la réponse déjà obtenue.
   *
   * **Un véhicule en free-floating n'émet aucune requête** : il n'a pas de station dont compter les
   * vélos, et interroger le point où il se trouve ne rendrait que le véhicule lui-même.
   *
   * @param fresh vrai quand c'est l'usager qui a demandé le relevé, et non le dépliage d'une
   *   portion. Le cache d'une minute retient les requêtes automatiques, jamais un geste délibéré :
   *   un bouton qui ne fait rien pendant une minute, sans le dire, fait croire à l'usager qu'il a
   *   redemandé (SPEC.md § 7.4).
   */
  private fun loadRental(index: Int, fresh: Boolean) {
    val leg = state.value.journey?.legs?.getOrNull(index) as? JourneyLeg.Rental ?: return
    val rental = leg.rental ?: return
    if (rental.fromStationName == null && rental.toStationName == null) return
    rentalWork[index]?.cancel()
    rentalWork[index] = viewModelScope.launch {
      updateRental(index) { it.copy(loading = true, error = null) }
      val pickup = station(leg.from.coordinates, rental.fromStationName, fresh)
      val dropoff = station(leg.to.coordinates, rental.toStationName, fresh)
      val failure = listOfNotNull(pickup, dropoff).filterIsInstance<Outcome.Failure>().firstOrNull()
      updateRental(index) {
        it.copy(
          loading = false,
          pickup = pickup.valueOr(it.pickup),
          dropoff = dropoff.valueOr(it.dropoff),
          error = failure?.error,
        )
      }
    }
  }

  /**
   * La station que la portion désigne, parmi celles rendues autour du point.
   *
   * Le tri est dans `:core` : plusieurs exploitants partagent le même parvis, et c'est le nom porté
   * par la portion qui les départage, la distance ne servant qu'ensuite.
   *
   * @return `null` quand la portion n'a pas de station de ce côté — il n'y a alors rien à demander.
   */
  private suspend fun station(point: LatLon, name: String?, fresh: Boolean): Outcome<RentalAvailability?>? {
    if (name == null) return null
    return when (val outcome = rentalsRepository.availabilityNear(point, fresh = fresh)) {
      is Outcome.Failure -> outcome
      is Outcome.Success -> Outcome.Success(outcome.value.stationFor(point, name))
    }
  }

  /**
   * Une réponse obtenue remplace la précédente, même vide : « le serveur ne connaît plus cette
   * station » est une information, pas une raison de continuer à afficher l'ancienne. Un échec, en
   * revanche, laisse en place ce qui était affiché (SPEC.md § 8).
   */
  private fun Outcome<RentalAvailability?>?.valueOr(previous: RentalAvailability?): RentalAvailability? =
    if (this is Outcome.Success) value else previous

  private fun updateRental(index: Int, transform: (RentalLegAvailability) -> RentalLegAvailability) {
    state.update { current ->
      val existing = current.rentals[index] ?: RentalLegAvailability()
      current.copy(rentals = current.rentals + (index to transform(existing)))
    }
  }

  private fun onDetailed(detailed: Journey) {
    val journey = named(detailed)
    val previous = state.value.journey
    // Le repli peut rendre un trajet recomposé : les positions de portions ne désignent alors plus
    // les mêmes portions, et un dépliage restitué au mauvais endroit serait pire que pas de
    // dépliage du tout.
    val sameShape = previous != null && previous.legs.size == journey.legs.size
    if (!sameShape) {
      rentalWork.values.forEach(Job::cancel)
      rentalWork.clear()
    }
    state.update {
      it.copy(
        journey = journey,
        detailed = true,
        loading = false,
        error = null,
        refreshedAt = now(),
        expandedLegs = if (sameShape) it.expandedLegs else emptySet(),
        expandedStops = if (sameShape) it.expandedStops else emptySet(),
        expandedSteps = if (sameShape) it.expandedSteps else emptySet(),
        rentals = if (sameShape) it.rentals else emptyMap(),
      )
    }
    // Le trajet détaillé porte la géométrie des portions, que le trajet sommaire n'avait pas : le
    // publier ici permet au lot « tracé » de dessiner le vrai tracé sans rien demander à cet écran.
    selection.select(journey)
  }

  private fun onFailure(error: EscaleError) {
    // Une requête supplantée n'est jamais montrée : un résultat plus récent arrive derrière
    // (docs/architecture.md § 6).
    if (error == EscaleError.Superseded) return
    state.update { it.copy(loading = false, error = error) }
  }

  private fun initialState(): DetailUiState {
    val chosen = selection.selected.value ?: return DetailUiState(closed = true)
    return DetailUiState(
      journey = named(chosen),
      expandedLegs = restored(KEY_LEGS),
      expandedStops = restored(KEY_STOPS),
      expandedSteps = restored(KEY_STEPS),
    )
  }

  /**
   * Le trajet, ses extrémités anonymes nommées par ce que l'usager a saisi.
   *
   * MOTIS ne nomme pas un point envoyé en coordonnées : `:data` traduit ses marqueurs internes en
   * absence de nom, et le seul endroit où le libellé existe encore est le brouillon de recherche.
   * Le nommage est fait **ici**, et non à l'affichage, pour que le trajet republié dans
   * `SelectedJourneyStore` en profite aussi : le lot « tracé » y lit le nom de ses marqueurs.
   */
  private fun named(journey: Journey): Journey {
    val draft = session.draft.value
    return journey.withEndpointNames(origin = draft.from?.name, destination = draft.to?.name)
  }

  private fun restored(key: String): Set<Int> = savedState.get<IntArray>(key)?.toSet().orEmpty()

  private fun remember(key: String, indexes: Set<Int>) {
    savedState[key] = indexes.toIntArray()
  }

  private fun Set<Int>.toggled(index: Int): Set<Int> = if (contains(index)) minus(index) else plus(index)

  companion object {
    /**
     * Les positions dépliées survivent à la rotation **comme à la mort du processus**. Ce sont des
     * numéros de portion : ils ne disent rien du trajet, et peuvent donc aller sur le disque, là
     * où le trajet lui-même ne le peut pas (SPEC.md § 11).
     */
    private const val KEY_LEGS = "detail.legs"
    private const val KEY_STOPS = "detail.stops"
    private const val KEY_STEPS = "detail.steps"

    /** Aucune recherche en cours : le repli sur `plan` est impossible, il n'y a pas de requête. */
    private const val NO_SEARCH = "NoSearchInProgress"

    /** Le serveur a répondu, mais sans aucun trajet où retrouver celui-ci. */
    private const val NO_MATCH = "NoMatchingItinerary"

    /**
     * Comme `ResultsViewModel` : les réglages de recherche de SPEC.md § 5.6 n'ont pas encore de
     * dépôt. Les valeurs par défaut du domaine sont celles du serveur, si bien que la requête
     * rejouée est exactement celle qu'a servie la liste de résultats.
     */
    private val PREFERENCES = SearchPreferences()

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        DetailViewModel(
          selection = container.selectedJourneyStore,
          session = container.searchSession,
          planRepository = container.planRepository,
          rentalsRepository = container.rentalsRepository,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
