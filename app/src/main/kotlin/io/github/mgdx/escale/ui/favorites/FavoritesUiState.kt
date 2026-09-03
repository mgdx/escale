package io.github.mgdx.escale.ui.favorites

import io.github.mgdx.escale.core.model.FavoriteJourney
import io.github.mgdx.escale.core.model.FavoritePlace
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.repository.AutocompleteState
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.search.SavedPlaceKind

/**
 * Tout ce que l'écran des favoris a à afficher, en une seule `data class` exposée en `StateFlow`
 * (docs/architecture.md § 8).
 *
 * Elle contient le domicile et le lieu de travail de l'usager : **rien de tout cela n'est
 * journalisé**, en débogage comme en production (SPEC.md § 11).
 */
data class FavoritesUiState(
  /** Nuls tant qu'ils ne sont pas renseignés, ce qui est un état normal (SPEC.md § 5.5). */
  val home: Location? = null,
  val work: Location? = null,
  val places: List<FavoritePlace> = emptyList(),
  val stops: List<FavoriteStopUi> = emptyList(),
  val journeys: List<FavoriteJourney> = emptyList(),
  val recentSearches: List<SearchHistoryEntry> = emptyList(),
  /** Faux quand l'usager a désactivé l'enregistrement des recherches (SPEC.md § 5.6). */
  val historyEnabled: Boolean = true,
  /** Le choix de lieu ouvert, ou `null`. Un seul à la fois. */
  val picker: PlacePickerUi? = null,
  /** La confirmation ouverte, ou `null`. */
  val dialog: FavoritesDialog? = null,
  /** Le message à afficher une fois, puis à oublier. */
  val message: FavoritesMessage? = null,
) {
  /**
   * Vrai quand il n'y a **rien** à montrer.
   *
   * L'historique n'en fait pas partie : une liste de recherches récentes sans le moindre favori
   * reste un écran qui a quelque chose à dire.
   */
  val empty: Boolean
    get() = home == null && work == null && places.isEmpty() && stops.isEmpty() &&
      journeys.isEmpty() && recentSearches.isEmpty()
}

/**
 * Un arrêt favori, et ce que l'on sait de son identifiant.
 *
 * SPEC.md § 5.6.1 : « les identifiants d'arrêts enregistrés dans les favoris peuvent ne plus être
 * reconnus par le nouveau serveur — dans ce cas le favori reste affiché avec ses coordonnées et un
 * signalement discret, il n'est jamais supprimé automatiquement ». Les coordonnées sont déjà dans
 * [stop] ; c'est [recognition] qui porte le signalement.
 */
data class FavoriteStopUi(val stop: Stop, val recognition: StopRecognition = StopRecognition.UNKNOWN)

/** Ce que le serveur courant a répondu quand on lui a présenté l'identifiant de l'arrêt. */
enum class StopRecognition {
  /**
   * Rien n'a été demandé, ou la réponse ne permet pas de conclure — appareil hors ligne, serveur
   * muet. **Aucun signalement** : accuser un favori parce que le réseau manque serait le pire des
   * deux mondes.
   */
  UNKNOWN,

  /** Le serveur connaît cet arrêt. */
  KNOWN,

  /** Le serveur a refusé l'identifiant : le favori reste, avec ses coordonnées et un signalement. */
  UNRECOGNIZED,
}

/**
 * Conclut, à partir d'un échec, si l'identifiant d'arrêt est en cause.
 *
 * Seul un refus du serveur — 400 ou 422, `EscaleError.BadRequest` — met en cause l'identifiant.
 * Tout le reste laisse le doute : une panne de réseau, un serveur injoignable ou une version d'API
 * trop ancienne ne disent rien de cet arrêt-là. Un 404 est ici particulièrement trompeur : il est
 * traduit en `ApiVersionTooOld` par `:data` (docs/architecture.md § 7), et le prendre pour un arrêt
 * inconnu ferait signaler tous les favoris d'un coup sur un serveur trop vieux.
 */
fun stopRecognition(error: EscaleError?): StopRecognition = when (error) {
  null -> StopRecognition.KNOWN
  is EscaleError.BadRequest -> StopRecognition.UNRECOGNIZED
  else -> StopRecognition.UNKNOWN
}

/** Ce que le choix de lieu ouvert est en train de renseigner. */
sealed interface PickerTarget {
  /** Le domicile ou le lieu de travail (SPEC.md § 5.5). */
  data class Named(val kind: SavedPlaceKind) : PickerTarget

  /** Un lieu nommé de plus, avec le nom que l'usager lui donne. */
  data object Place : PickerTarget

  /** Un arrêt : seules les suggestions de genre `STOP` sont proposées. */
  data object Stop : PickerTarget
}

/**
 * Le choix d'un lieu, par la même autocomplétion que la carte de recherche (SPEC.md § 5.1).
 *
 * [label] n'est utilisé que pour [PickerTarget.Place] : c'est le « lieu nommé » de SPEC.md § 5.5,
 * et il est saisi avant le lieu pour que le choix d'une suggestion suffise à valider.
 */
data class PlacePickerUi(
  val target: PickerTarget,
  val query: String = "",
  val label: String = "",
  val suggestions: AutocompleteState = AutocompleteState.Idle,
)

/** Les confirmations de l'écran. Une seule à la fois. */
sealed interface FavoritesDialog {
  /** « Tout effacer » : SPEC.md § 5.5 veut l'historique effaçable en bloc, § 5.6 le confirme. */
  data object ClearHistory : FavoritesDialog
}

/**
 * Retour affiché après une action, traduit en chaîne par l'écran.
 *
 * Le `ViewModel` nomme le message et n'en connaît pas le texte : c'est ce qui lui permet de ne
 * référencer aucune ressource et de rester lisible en JVM.
 */
enum class FavoritesMessage {
  SAVED,
  DELETED,
  HISTORY_CLEARED,
  FAILED,
}
