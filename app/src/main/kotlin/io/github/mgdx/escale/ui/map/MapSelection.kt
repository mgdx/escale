package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ce que l'usager veut faire du point qu'il vient de choisir sur la carte (SPEC.md § 5.1). */
enum class MapPickPurpose {
  /** « Partir d'ici » : le point devient l'origine de la recherche. */
  DEPARTURE,

  /** « Aller ici » : le point devient la destination. */
  DESTINATION,
}

/**
 * Un point choisi par appui long sur la carte.
 *
 * [label] est nul tant que `/api/v1/reverse-geocode` n'a pas rendu de nom lisible, et le reste si
 * le serveur ne sait rien de cet endroit. Le consommateur affiche alors les coordonnées ou un
 * libellé générique : un point sans nom reste un point utilisable.
 */
data class MapPick(val purpose: MapPickPurpose, val point: LatLon, val label: Location? = null)

/**
 * Le point de rendez-vous entre la carte et l'écran de recherche.
 *
 * L'appui long sur la carte appartient au lot « carte » ; la carte de recherche qui consommera le
 * point appartient au lot « recherche », qui n'existe pas encore. Plutôt que de coupler les deux,
 * le choix est déposé ici, dans un objet à durée de vie applicative détenu par l'`AppContainer` :
 * la carte écrit, la recherche lit et acquitte, et aucun des deux ne connaît l'autre.
 */
class MapSelection {

  private val state = MutableStateFlow<MapPick?>(null)

  /** Le dernier point choisi et non encore consommé. */
  val pick: StateFlow<MapPick?> = state.asStateFlow()

  /** Dépose un point choisi, en remplaçant celui qui n'aurait pas été consommé. */
  fun select(purpose: MapPickPurpose, point: LatLon) {
    state.value = MapPick(purpose = purpose, point = point)
  }

  /** Complète le point courant du libellé rendu par le géocodage inverse, s'il porte le même point. */
  fun attachLabel(point: LatLon, label: Location?) {
    state.update { current -> if (current?.point == point) current.copy(label = label) else current }
  }

  /** Acquitte le point : appelé par le lot qui l'a consommé. */
  fun consume() {
    state.value = null
  }
}
