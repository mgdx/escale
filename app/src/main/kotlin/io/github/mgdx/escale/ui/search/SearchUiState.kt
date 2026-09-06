package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.repository.AutocompleteState

/** Lequel des deux champs de la carte de recherche l'usager est en train de remplir. */
enum class SearchField {
  FROM,
  TO,
}

/**
 * Les entrées que SPEC.md § 5.1 impose **en tête** de la liste d'autocomplétion, avant tout
 * résultat du serveur.
 */
enum class SearchShortcut {
  /** « Ma position » : le libellé lisible vient du géocodage inverse. */
  MY_LOCATION,

  HOME,
  WORK,

  /** « Choisir sur la carte » : replie le clavier et rend la main à l'appui long. */
  PICK_ON_MAP,
}

/** Une puce d'accès rapide, sous les champs et seulement quand ils sont vides (SPEC.md § 5.1). */
sealed interface QuickChip {
  data class Saved(val kind: SavedPlaceKind, val location: Location) : QuickChip

  data class Recent(val search: SearchHistoryEntry) : QuickChip
}

/** Les deux heures que l'API sait interroger : `arriveBy` faux, puis vrai. */
enum class TimeMode {
  DEPART_AT,
  ARRIVE_BY,
}

/** Les trois écrans successifs du sélecteur d'heure (SPEC.md § 5.1). */
enum class TimePickerStep {
  /** « Partir maintenant » / « Partir à… » / « Arriver avant… ». */
  CHOICE,

  DATE,
  TIME,
}

/**
 * Le sélecteur d'heure ouvert, ou `null` s'il est fermé.
 *
 * L'étape vit dans le `ViewModel` et non dans la composition : sans cela, une rotation au milieu du
 * choix de la date ramènerait l'usager à la première question.
 */
data class TimePickerUi(
  val step: TimePickerStep,
  val mode: TimeMode? = null,
  /** La date choisie, en millisecondes UTC, telle que la rend le `DatePicker` de Material 3. */
  val dateUtcMillis: Long? = null,
)

/**
 * Tout ce que la carte de recherche a à afficher, en une seule `data class` exposée en `StateFlow`
 * (docs/architecture.md § 8).
 *
 * Elle contient des adresses et des coordonnées : **rien de tout cela n'est journalisé**, en
 * débogage comme en production (SPEC.md § 8 et § 11).
 */
data class SearchUiState(
  val from: Location? = null,
  val to: Location? = null,
  val time: TimeChoice = TimeChoice.Now,

  /** Non nul quand le champ actif occupe l'écran entier (SPEC.md § 5.1). */
  val activeField: SearchField? = null,

  /** La saisie en cours dans le champ actif. */
  val query: String = "",

  val suggestions: AutocompleteState = AutocompleteState.Idle,

  /**
   * Le bloc « déjà utilisés », au-dessus des suggestions du serveur (SPEC.md § 5.1).
   *
   * Il est tenu à part de [suggestions] parce qu'il ne vient pas du même endroit : il s'affiche dès
   * le premier caractère, sans aucune requête, et reste donc visible quand le serveur est encore au
   * repos, en vol, ou en échec.
   */
  val knownPlaces: List<Location> = emptyList(),

  /** Les entrées en tête de liste, dans l'ordre de SPEC.md § 5.1. */
  val shortcuts: List<SearchShortcut> = emptyList(),

  /** Les puces d'accès rapide. Vide dès qu'un des deux champs est renseigné. */
  val chips: List<QuickChip> = emptyList(),

  /**
   * La puce Domicile ou Travail sur laquelle l'usager a fait un appui long, ou `null`.
   *
   * SPEC.md § 5.5 : « une fois renseignés, ils sont modifiables et supprimables depuis les réglages
   * comme depuis un appui long sur la puce ».
   */
  val savedPlaceMenu: SavedPlaceKind? = null,

  val timePicker: TimePickerUi? = null,

  /** « Choisir sur la carte » : l'écran s'efface et attend un appui long (SPEC.md § 5.1). */
  val awaitingMapPick: Boolean = false,
)
