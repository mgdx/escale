package io.github.mgdx.escale.ui.departures

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.DepartureModeFilter

/*
 * Ce qui traduit les puces de filtre en ressources : pictogrammes et libellés.
 *
 * Comme `ResultsFormatting`, rien ici ne décide de quoi que ce soit — le regroupement des modes en
 * puces est dans `:core`, testé en JVM (`DepartureFilterTest`). Ce fichier ne fait que choisir la
 * ressource qui correspond, ce qu'aucun test JVM ne peut faire à sa place.
 *
 * **Chaque pictogramme est doublé d'un libellé textuel** (SPEC.md § 9) : les deux tables vont donc
 * toujours par paires. Les dessins sont ceux que le projet possède déjà
 * (docs/architecture.md § 11.2) ; aucun n'est redessiné.
 */

@StringRes
internal fun DepartureModeFilter.labelRes(): Int = when (this) {
  DepartureModeFilter.TRAIN -> R.string.departures_filter_train
  DepartureModeFilter.SUBWAY -> R.string.departures_filter_subway
  DepartureModeFilter.TRAM -> R.string.departures_filter_tram
  DepartureModeFilter.BUS -> R.string.departures_filter_bus
  DepartureModeFilter.FERRY -> R.string.departures_filter_ferry
  DepartureModeFilter.OTHER -> R.string.departures_filter_other
}

@DrawableRes
internal fun DepartureModeFilter.iconRes(): Int = when (this) {
  DepartureModeFilter.TRAIN -> R.drawable.ic_train
  DepartureModeFilter.SUBWAY -> R.drawable.ic_subway
  DepartureModeFilter.TRAM -> R.drawable.ic_tram
  DepartureModeFilter.BUS -> R.drawable.ic_directions_bus
  DepartureModeFilter.FERRY -> R.drawable.ic_directions_boat
  DepartureModeFilter.OTHER -> R.drawable.ic_directions_transit
}
