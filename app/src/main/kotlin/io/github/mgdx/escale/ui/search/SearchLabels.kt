package io.github.mgdx.escale.ui.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.SearchTime
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.settings.uses24HourClock
import java.time.ZoneId

/**
 * Le libellé de la troisième ligne de la carte de recherche (SPEC.md § 5.1).
 *
 * Il **reflète toujours le choix en cours** : « Maintenant », « Départ jeu. 14:30 », « Arrivée
 * avant ven. 09:00 ». Il se compose d'une chaîne de `strings_search.xml` et d'une date formatée par
 * `:core` — jamais de texte concaténé en dur. Le fuseau est celui de l'appareil, et le format
 * 12 h / 24 h celui que l'usager a réglé (SPEC.md § 5.6).
 */
@Composable
fun timeChoiceLabel(time: TimeChoice): String {
  // `LocalConfiguration`, et non `LocalContext.current.resources` : seul le premier invalide la
  // composition quand la langue ou le fuseau de l'appareil changent.
  val locale = LocalConfiguration.current.locales[0]
  val use24Hour = uses24HourClock()
  val zone = ZoneId.systemDefault()
  return when (time) {
    TimeChoice.Now -> stringResource(R.string.search_time_now)

    is TimeChoice.DepartAt ->
      stringResource(R.string.search_time_depart_at, SearchTime.format(time.instant, zone, locale, use24Hour))

    is TimeChoice.ArriveBy ->
      stringResource(R.string.search_time_arrive_by, SearchTime.format(time.instant, zone, locale, use24Hour))
  }
}

/**
 * Le pictogramme qui distingue une adresse, un arrêt et un lieu dans la liste (SPEC.md § 5.1).
 *
 * Il est toujours doublé du libellé de [labelRes], porté par sa `contentDescription` : aucune
 * information n'est confiée au seul dessin, ni à la seule couleur (SPEC.md § 9).
 */
@DrawableRes
fun PlaceKind.iconRes(): Int = when (this) {
  PlaceKind.ADDRESS -> R.drawable.ic_place

  PlaceKind.PLACE -> R.drawable.ic_business

  // L'anneau est le symbole cartographique usuel d'un point d'arrêt.
  PlaceKind.STOP -> R.drawable.ic_trip_origin
}

@StringRes
fun PlaceKind.labelRes(): Int = when (this) {
  PlaceKind.ADDRESS -> R.string.search_kind_address
  PlaceKind.PLACE -> R.string.search_kind_place
  PlaceKind.STOP -> R.string.search_kind_stop
}

/**
 * Le nom d'un mode desservi par un arrêt, **en toutes lettres** (SPEC.md § 5.1).
 *
 * Les pictogrammes de mode appartiennent à la frise de l'écran des résultats et n'existent pas
 * encore : ici, le mot fait le travail, et il le ferait de toute façon pour un lecteur d'écran.
 */
@StringRes
fun TransitMode.labelRes(): Int = modeLabels[this] ?: R.string.search_mode_other

/**
 * Une table plutôt qu'un `when` de vingt-quatre branches : les modes de rue et les valeurs agrégées
 * ne décrivent pas un arrêt et n'ont pas à y être nommés un par un — ils retombent tous sur
 * « Autre ».
 *
 * Les mots viennent de `strings_results.xml` : les libellés de mode y vivent tous, et les autres
 * écrans les réutilisent tels quels (CONTRIBUTING.md, « Où sont les chaînes »). Seul « Autre »,
 * qui n'est pas un mode mais l'absence de mode connu, appartient à cet écran.
 */
private val modeLabels: Map<TransitMode, Int> = mapOf(
  TransitMode.TRAM to R.string.results_mode_tram,
  TransitMode.SUBWAY to R.string.results_mode_subway,
  TransitMode.FERRY to R.string.results_mode_ferry,
  TransitMode.AIRPLANE to R.string.results_mode_airplane,
  TransitMode.BUS to R.string.results_mode_bus,
  TransitMode.COACH to R.string.results_mode_coach,
  TransitMode.RAIL to R.string.results_mode_rail,
  TransitMode.HIGHSPEED_RAIL to R.string.results_mode_highspeed_rail,
  TransitMode.LONG_DISTANCE to R.string.results_mode_long_distance,
  TransitMode.NIGHT_RAIL to R.string.results_mode_night_rail,
  TransitMode.REGIONAL_RAIL to R.string.results_mode_regional_rail,
  TransitMode.SUBURBAN to R.string.results_mode_suburban,
  TransitMode.FUNICULAR to R.string.results_mode_funicular,
  TransitMode.AERIAL_LIFT to R.string.results_mode_aerial_lift,
)

/** « Desservi par Bus · Tram », ou une chaîne vide quand l'arrêt n'annonce aucun mode. */
@Composable
fun servedModesLabel(modes: List<TransitMode>): String {
  if (modes.isEmpty()) return ""
  val separator = stringResource(R.string.search_mode_separator)
  // `map` est une fonction en ligne : le contexte de composition y survit, contrairement à
  // `joinToString`, dont la transformation n'est pas en ligne.
  val names = modes.distinct().map { stringResource(it.labelRes()) }.joinToString(separator)
  return stringResource(R.string.search_served_modes, names)
}

/** L'intitulé d'un des deux champs, pour l'étiquette comme pour le lecteur d'écran. */
@StringRes
fun SearchField.labelRes(): Int = when (this) {
  SearchField.FROM -> R.string.search_from_label
  SearchField.TO -> R.string.search_to_label
}

@StringRes
fun SearchField.hintRes(): Int = when (this) {
  SearchField.FROM -> R.string.search_from_hint
  SearchField.TO -> R.string.search_to_hint
}

@DrawableRes
fun SearchField.iconRes(): Int = when (this) {
  SearchField.FROM -> R.drawable.ic_trip_origin
  SearchField.TO -> R.drawable.ic_place
}

@StringRes
fun SearchShortcut.labelRes(): Int = when (this) {
  SearchShortcut.MY_LOCATION -> R.string.search_entry_my_location
  SearchShortcut.HOME -> R.string.search_entry_home
  SearchShortcut.WORK -> R.string.search_entry_work
  SearchShortcut.PICK_ON_MAP -> R.string.search_entry_pick_on_map
}

@DrawableRes
fun SearchShortcut.iconRes(): Int = when (this) {
  SearchShortcut.MY_LOCATION -> R.drawable.ic_my_location
  SearchShortcut.HOME -> R.drawable.ic_home
  SearchShortcut.WORK -> R.drawable.ic_work
  SearchShortcut.PICK_ON_MAP -> R.drawable.ic_map
}

@StringRes
fun SavedPlaceKind.labelRes(): Int = when (this) {
  SavedPlaceKind.HOME -> R.string.search_entry_home
  SavedPlaceKind.WORK -> R.string.search_entry_work
}

@DrawableRes
fun SavedPlaceKind.iconRes(): Int = when (this) {
  SavedPlaceKind.HOME -> R.drawable.ic_home
  SavedPlaceKind.WORK -> R.drawable.ic_work
}
