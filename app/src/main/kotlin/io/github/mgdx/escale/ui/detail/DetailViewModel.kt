package io.github.mgdx.escale.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.JourneyRefresh
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.matching
import io.github.mgdx.escale.core.model.stationFor
import io.github.mgdx.escale.core.model.withEndpointNames
import io.github.mgdx.escale.core.query.RealtimeRefreshPolicy
import io.github.mgdx.escale.core.repository.FavoritesRepository
import io.github.mgdx.escale.core.repository.PlanRepository
import io.github.mgdx.escale.core.repository.RentalsRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.ui.results.SelectedJourneyStore
import io.github.mgdx.escale.ui.session.SearchDraft
import io.github.mgdx.escale.ui.session.SearchSession
import io.github.mgdx.escale.ui.session.decodeSearchDraft
import io.github.mgdx.escale.ui.session.encodeSearchDraft
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
 * - **Le même chemin sert au bouton « Actualiser »** de SPEC.md § 5.3 : rafraîchir, c'est
 *   redemander le trajet détaillé. Il n'y a donc qu'un seul code réseau à lire et à vérifier.
 *
 * Aucun polling (SPEC.md § 7.4) : les seuls déclencheurs sont l'ouverture de l'écran, l'appui de
 * l'usager sur « Actualiser », et le retour au premier plan sur des horaires de plus de 60
 * secondes — la même règle que la feuille de résultats, tenue par le même `RealtimeRefreshPolicy`.
 * Ni minuterie, ni boucle, ni tâche de fond. Ce `ViewModel` n'importe rien de Compose (docs/architecture.md § 8) et
 * **ne journalise rien** : il manipule des adresses et des coordonnées (SPEC.md § 11).
 */
class DetailViewModel(
  private val selection: SelectedJourneyStore,
  private val session: SearchSession,
  private val planRepository: PlanRepository,
  private val rentalsRepository: RentalsRepository,
  private val favoritesRepository: FavoritesRepository,
  private val savedState: SavedStateHandle,
  private val now: () -> Instant = Instant::now,
) : ViewModel() {

  /**
   * La recherche relue dans l'état sauvegardé, quand le processus est mort entre-temps.
   *
   * Elle est reconstruite **avant** [state], parce que [initialState] nomme déjà les extrémités du
   * trajet et a donc besoin d'elle dès la première image. `null` quand rien n'a été sauvegardé.
   */
  private val restoredSession: SearchSession? = restoreSession(savedState)

  private val state = MutableStateFlow(initialState())

  val uiState: StateFlow<DetailUiState> = state.asStateFlow()

  /** Au plus une requête détaillée en vol : un second appui remplace le premier (SPEC.md § 7.2). */
  private var work: Job? = null

  /** Au plus une requête de disponibilité en vol **par portion**, pour la même raison. */
  private val rentalWork = mutableMapOf<Int, Job>()

  /** Les trajets favoris, tels que le dépôt les a émis en dernier. */
  private var knownFavorites: List<FavoriteJourney> = emptyList()

  init {
    if (!state.value.closed) load()
    observeFavorites()
  }

  /**
   * L'état de l'étoile, tenu par les favoris eux-mêmes (SPEC.md § 5.5).
   *
   * Il est **observé**, et non calculé une fois à l'ouverture : mettre le même trajet en favori
   * depuis l'écran des favoris, ou l'y retirer, doit changer l'étoile ici aussi. La comparaison est
   * celle de `:core` — nom, coordonnées, catégorie —, la même que celle de l'index unique de la
   * base : l'écran ne peut donc pas croire absent ce que le stockage refuserait d'insérer.
   */
  private fun observeFavorites() {
    viewModelScope.launch {
      favoritesRepository.journeys.collect { favorites ->
        knownFavorites = favorites
        refreshFavoriteState()
      }
    }
  }

  /**
   * Recalcule l'état de l'étoile.
   *
   * Appelé des deux côtés, parce que les deux comptent : quand les favoris changent, et quand le
   * trajet affiché change — le repli de SPEC.md § 5.5.1 peut rendre un trajet recomposé, dont la
   * catégorie relue n'est plus la même, et l'étoile doit alors désigner l'autre favori, ou aucun.
   */
  private fun refreshFavoriteState() {
    val draft = currentSession().draft.value
    val from = draft.from
    val to = draft.to
    val journey = state.value.journey
    val matched = if (from == null || to == null || journey == null) {
      null
    } else {
      knownFavorites.matching(from, to, JourneyRefresh.categoryOf(journey))
    }
    state.update { it.copy(favoriteId = matched?.id) }
  }

  /**
   * Le bouton « Actualiser » (SPEC.md § 5.3) et le bouton « Réessayer » du bandeau (§ 8).
   *
   * Il rafraîchit aussi les disponibilités déjà affichées : elles sont plus périssables que les
   * horaires, et les laisser telles quelles pendant que le reste de l'écran se met à jour serait
   * exactement le mensonge que l'heure de relevé sert à éviter.
   */
  fun onRefresh() {
    load()
    state.value.rentals.keys.toList().forEach { loadRental(it, fresh = true) }
  }

  /**
   * L'étoile de la barre supérieure : **elle bascule** (SPEC.md § 5.3 et § 5.5).
   *
   * Trajet déjà en favori, l'appui le retire ; absent, il l'ajoute. C'est ce que tout le monde
   * attend d'une étoile, et c'est aussi ce qui rend la duplication impossible par l'interface —
   * l'unicité, elle, est garantie par le stockage, qui ne dépend d'aucun écran.
   *
   * Un trajet favori est un **couple départ / arrivée**, pas un itinéraire figé : ce sont les deux
   * points de la recherche en cours qui sont enregistrés, avec leur identifiant d'arrêt quand ils
   * en ont un (docs/architecture.md § 11.3), et non les extrémités recalculées du trajet affiché.
   * Rejouer le favori dans six mois relance donc une recherche, ce qui a un sens, là où rejouer un
   * itinéraire périmé n'en aurait aucun.
   *
   * La catégorie est celle que `JourneyRefresh` relit dans les portions — un rabattement à vélo
   * vers une gare est un trajet en transport en commun, pas un trajet à vélo — et c'est la trace
   * des « préférences de modes » que SPEC.md § 5.5 autorise à joindre au favori.
   */
  fun onToggleFavorite() {
    val existing = state.value.favoriteId
    if (existing != null) {
      viewModelScope.launch {
        val outcome = favoritesRepository.removeJourney(existing)
        show(if (outcome is Outcome.Success) DetailMessage.FAVORITE_REMOVED else DetailMessage.FAVORITE_FAILED)
      }
      return
    }
    val draft = currentSession().draft.value
    val from = draft.from
    val to = draft.to
    val journey = state.value.journey
    if (from == null || to == null || journey == null) {
      show(DetailMessage.FAVORITE_FAILED)
      return
    }
    viewModelScope.launch {
      val outcome = favoritesRepository.addJourney(
        from = from,
        to = to,
        category = JourneyRefresh.categoryOf(journey),
        // Aucun nom demandé ici : l'écran de détail n'est pas un formulaire, et un trajet sans
        // libellé s'affiche « Départ → Arrivée » dans les favoris.
        label = null,
      )
      show(if (outcome is Outcome.Success) DetailMessage.FAVORITE_ADDED else DetailMessage.FAVORITE_FAILED)
    }
  }

  /** Le message a été montré : on l'oublie, pour qu'une rotation ne le rejoue pas. */
  fun onMessageShown() {
    state.update { it.copy(message = null) }
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

  private fun show(message: DetailMessage) {
    state.update { it.copy(message = message) }
  }

  /**
   * La requête détaillée, dans les deux situations où l'écran en a besoin.
   *
   * Le trajet est déjà là — ouverture depuis la liste, « Actualiser », retour au premier plan — et
   * c'est [detailed] qui s'en charge, repli compris. Sinon l'écran vient d'être restitué après la
   * mort du processus : le magasin en mémoire est vide, il ne reste que l'identifiant conservé, et
   * `refresh-itinerary` suffit à reconstruire le trajet. **Une requête, celle de l'écran consulté,
   * et pas une de plus** (SPEC.md § 7).
   */
  private fun load() {
    val current = state.value.journey
    val itineraryId = savedState.get<String>(KEY_ITINERARY)
    when {
      current != null -> request { detailed(current) }
      itineraryId != null -> request { planRepository.refresh(itineraryId, detailedLegs = true) }
    }
  }

  private fun request(call: suspend () -> Outcome<Journey>) {
    work?.cancel()
    work = viewModelScope.launch {
      state.update { it.copy(loading = true, error = null) }
      when (val outcome = call()) {
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
    val query = currentSession().toQuery(JourneyRefresh.categoryOf(current), PREFERENCES)
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
    // dépliage du tout. Sans trajet précédent — l'écran restitué après la mort du processus —, il
    // n'y a rien à comparer : c'est le même itinéraire qui revient, et le dépliage relu dans
    // l'état sauvegardé désigne bien ses portions.
    val sameShape = previous == null || previous.legs.size == journey.legs.size
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
    // Le repli rend un autre itinéraire, donc un autre identifiant : c'est celui-là qu'il faudra
    // redemander, et non celui d'un trajet que le serveur a déjà refusé.
    savedState[KEY_ITINERARY] = journey.id
    // Le trajet détaillé porte la géométrie des portions, que le trajet sommaire n'avait pas : le
    // publier ici permet au lot « tracé » de dessiner le vrai tracé sans rien demander à cet écran.
    selection.select(journey)
    refreshFavoriteState()
  }

  private fun onFailure(error: EscaleError) {
    // Une requête supplantée n'est jamais montrée : un résultat plus récent arrive derrière
    // (docs/architecture.md § 6).
    if (error == EscaleError.Superseded) return
    // Rien à montrer sous le bandeau : c'est l'écran restitué après la mort du processus, dont
    // l'identifiant conservé était le seul appui. Périmé, il ne laisse aucun repli : la recherche
    // est bien là, mais le repli de SPEC.md § 5.5.1 retient le trajet le plus proche en heure de
    // départ, et cette heure-là s'en est allée avec le trajet qui la portait. L'écran se referme
    // donc comme il le faisait déjà. Une panne passagère, elle, garde le bandeau et son bouton
    // « Réessayer » (SPEC.md § 8).
    if (state.value.journey == null && JourneyRefresh.invalidatesItineraryId(error)) {
      state.update { it.copy(loading = false, error = null, closed = true) }
      return
    }
    state.update { it.copy(loading = false, error = error) }
  }

  /**
   * L'état de départ de l'écran, y compris **au retour d'une mort du processus**.
   *
   * Le trajet choisi vit en mémoire, dans `SelectedJourneyStore`, qui ne survit pas au processus.
   * Ce qui survit, c'est l'identifiant de l'itinéraire **et la recherche qui y a mené**, rangés
   * dans l'état sauvegardé de cette entrée de navigation : le premier suffit à redemander le trajet
   * en une requête, plutôt que de refermer l'écran sous les yeux de l'usager qui le lisait, et la
   * seconde à le renommer et à retrouver son favori. Il n'y va rien de plus que ce que l'état
   * sauvegardé porte déjà — l'écran de recherche y écrit le même brouillon, encodé par la même
   * fonction — et tout disparaît avec la tâche, jamais sur le disque (PRIVACY.md, SPEC.md § 11).
   */
  private fun initialState(): DetailUiState {
    val chosen = selection.selected.value
    if (chosen != null) savedState[KEY_ITINERARY] = chosen.id
    // La recherche n'est copiée que tant qu'elle existe : sur une fiche déjà restituée, la session
    // est vide et l'écrire écraserait le brouillon relu, que la rotation suivante ne trouverait
    // plus.
    val draft = session.draft.value
    if (draft != SearchDraft()) savedState[KEY_SEARCH] = encodeSearchDraft(draft)
    // Ni trajet en mémoire, ni identifiant conservé — un trajet dont le serveur n'a pas donné
    // d'identifiant, par exemple : il n'y a rien à reconstruire, l'écran se referme.
    if (chosen == null && savedState.get<String>(KEY_ITINERARY) == null) return DetailUiState(closed = true)
    return DetailUiState(
      journey = chosen?.let(::named),
      // Le trajet reste à reconstruire : l'écran annonce l'attente dès sa première image.
      loading = chosen == null,
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
    val draft = currentSession().draft.value
    return journey.withEndpointNames(origin = draft.from?.name, destination = draft.to?.name)
  }

  /**
   * La recherche qui a mené à cette fiche, **y compris quand le processus est mort entre-temps**.
   *
   * `SearchSession` vit dans l'`AppContainer` : elle survit à la rotation, pas au processus. Après
   * une mort en arrière-plan, l'écran de détail est le premier recomposé, et il trouve la session
   * vide — le brouillon n'y revient qu'une fois l'accueil recomposé, c'est-à-dire au retour en
   * arrière. Trop tard pour l'écran affiché : l'arrivée envoyée en coordonnées retombait sur son
   * libellé générique, et l'étoile des favoris, qui a besoin du couple cherché, restait creuse.
   *
   * La session en cours fait donc foi tant qu'elle porte quelque chose — elle est la plus
   * récente —, et [restoredSession] prend le relais sinon.
   */
  private fun currentSession(): SearchSession =
    if (session.draft.value == SearchDraft()) restoredSession ?: session else session

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

    /**
     * L'identifiant de l'itinéraire affiché : **ce par quoi l'écran se rouvre** après la mort du
     * processus, au lieu de se refermer sur la liste de résultats.
     *
     * Il vit dans l'état sauvegardé de l'entrée de navigation, que le système garde en mémoire le
     * temps de relancer le processus et qui s'en va avec la tâche. Rien n'est écrit sur le disque :
     * les trajets obtenus restent en mémoire seulement, comme le promet PRIVACY.md.
     */
    private const val KEY_ITINERARY = "detail.itinerary"

    /**
     * La recherche en cours au moment où la fiche s'est ouverte, encodée comme l'écran de recherche
     * encode déjà la sienne.
     *
     * L'itinéraire seul ne suffit pas : MOTIS ne nomme pas un point qu'on lui a envoyé en
     * coordonnées, et le libellé de l'arrivée n'existe que dans ce brouillon. Sans lui, la fiche
     * restituée affichait « Arrivée » là où elle affichait « Grenoble » un instant plus tôt.
     *
     * Même mémoire, même durée de vie et même règle de confidentialité que [KEY_ITINERARY] : le
     * système la garde le temps de relancer le processus, elle s'en va avec la tâche, et rien
     * n'atteint le disque (PRIVACY.md, SPEC.md § 11).
     */
    private const val KEY_SEARCH = "detail.search"

    /**
     * Relit la recherche sauvegardée, ou rend `null` quand il n'y en a pas.
     *
     * Hors de la classe parce qu'elle est appelée depuis un initialiseur de propriété : elle ne
     * doit dépendre d'aucun autre champ que celui qu'on lui passe.
     */
    private fun restoreSession(savedState: SavedStateHandle): SearchSession? {
      val draft = savedState.get<String>(KEY_SEARCH)?.let(::decodeSearchDraft) ?: return null
      return SearchSession().apply {
        setFrom(draft.from)
        setTo(draft.to)
        setTime(draft.time)
      }
    }

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
          favoritesRepository = container.favoritesRepository,
          savedState = createSavedStateHandle(),
        )
      }
    }
  }
}
