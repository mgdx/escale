package io.github.mgdx.escale.ui.results

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.ClockTime
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.format.FormattedDuration
import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.ui.settings.uses24HourClock
import io.github.mgdx.escale.ui.theme.LocalDarkTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/*
 * Ce qui traduit le domaine en ressources : pictogrammes, libellés, heures, durées et couleurs.
 *
 * Rien ici ne décide de quoi que ce soit — les règles sont dans `:core`, testées en JVM. Ce fichier
 * ne fait que choisir la ressource qui correspond, ce qu'aucun test JVM ne peut faire à sa place.
 */

/** Le pictogramme d'une portion. Il est **toujours** doublé d'un libellé textuel (SPEC.md § 9). */
@DrawableRes
internal fun JourneyLeg.modeIcon(): Int = when (this) {
  is JourneyLeg.Transit -> mode.iconRes()
  is JourneyLeg.Walk -> R.drawable.ic_directions_walk
  is JourneyLeg.Bike -> R.drawable.ic_pedal_bike
  is JourneyLeg.Car -> R.drawable.ic_directions_car
  is JourneyLeg.Rental -> rental?.formFactor.rentalIconRes()
}

/** Le libellé d'une portion, celui que lit aussi bien l'œil que le lecteur d'écran. */
@StringRes
internal fun JourneyLeg.modeLabel(): Int = when (this) {
  is JourneyLeg.Transit -> mode.labelRes()
  is JourneyLeg.Walk -> R.string.results_mode_walk
  is JourneyLeg.Bike -> R.string.results_mode_bike
  is JourneyLeg.Car -> R.string.results_mode_car
  is JourneyLeg.Rental -> rental?.formFactor.rentalLabelRes()
}

/** Le pictogramme d'un onglet, qui sert aussi d'illustration à son état vide (SPEC.md § 8). */
@DrawableRes
internal fun JourneyCategory.iconRes(): Int = when (this) {
  JourneyCategory.TRANSIT -> R.drawable.ic_directions_transit
  JourneyCategory.CAR -> R.drawable.ic_directions_car
  JourneyCategory.BIKE -> R.drawable.ic_pedal_bike
  JourneyCategory.WALK -> R.drawable.ic_directions_walk
}

@StringRes
internal fun JourneyCategory.labelRes(): Int = when (this) {
  JourneyCategory.TRANSIT -> R.string.results_tab_transit
  JourneyCategory.CAR -> R.string.results_tab_car
  JourneyCategory.BIKE -> R.string.results_tab_bike
  JourneyCategory.WALK -> R.string.results_tab_walk
}

// Une table de correspondance, pas un algorithme : detekt y compte une branche par mode, ce qui
// n'a pas de sens ici. La découper en sous-fonctions rendrait la table moins lisible, pas plus.
@Suppress("CyclomaticComplexMethod")
@DrawableRes
private fun TransitMode.iconRes(): Int = when (this) {
  TransitMode.WALK -> R.drawable.ic_directions_walk

  TransitMode.BIKE -> R.drawable.ic_pedal_bike

  TransitMode.RENTAL -> R.drawable.ic_bike_scooter

  TransitMode.CAR, TransitMode.HGV, TransitMode.CAR_PARKING, TransitMode.CAR_DROPOFF ->
    R.drawable.ic_directions_car

  TransitMode.ODM, TransitMode.RIDE_SHARING, TransitMode.FLEX -> R.drawable.ic_local_taxi

  TransitMode.TRAM -> R.drawable.ic_tram

  TransitMode.SUBWAY -> R.drawable.ic_subway

  TransitMode.FERRY -> R.drawable.ic_directions_boat

  TransitMode.AIRPLANE -> R.drawable.ic_flight

  TransitMode.BUS -> R.drawable.ic_directions_bus

  TransitMode.COACH -> R.drawable.ic_airport_shuttle

  TransitMode.RAIL, TransitMode.REGIONAL_RAIL, TransitMode.SUBURBAN -> R.drawable.ic_directions_railway

  TransitMode.HIGHSPEED_RAIL, TransitMode.LONG_DISTANCE, TransitMode.NIGHT_RAIL -> R.drawable.ic_train

  TransitMode.FUNICULAR -> R.drawable.ic_funicular

  TransitMode.AERIAL_LIFT -> R.drawable.ic_cable_car

  TransitMode.TRANSIT, TransitMode.OTHER -> R.drawable.ic_directions_transit
}

@Suppress("CyclomaticComplexMethod")
@StringRes
private fun TransitMode.labelRes(): Int = when (this) {
  TransitMode.WALK -> R.string.results_mode_walk
  TransitMode.BIKE -> R.string.results_mode_bike
  TransitMode.RENTAL -> R.string.results_mode_rental
  TransitMode.CAR -> R.string.results_mode_car
  TransitMode.HGV -> R.string.results_mode_hgv
  TransitMode.CAR_PARKING -> R.string.results_mode_car_parking
  TransitMode.CAR_DROPOFF -> R.string.results_mode_car_dropoff
  TransitMode.ODM, TransitMode.RIDE_SHARING, TransitMode.FLEX -> R.string.results_mode_on_demand
  TransitMode.TRAM -> R.string.results_mode_tram
  TransitMode.SUBWAY -> R.string.results_mode_subway
  TransitMode.FERRY -> R.string.results_mode_ferry
  TransitMode.AIRPLANE -> R.string.results_mode_airplane
  TransitMode.BUS -> R.string.results_mode_bus
  TransitMode.COACH -> R.string.results_mode_coach
  TransitMode.RAIL -> R.string.results_mode_rail
  TransitMode.REGIONAL_RAIL -> R.string.results_mode_regional_rail
  TransitMode.SUBURBAN -> R.string.results_mode_suburban
  TransitMode.HIGHSPEED_RAIL -> R.string.results_mode_highspeed_rail
  TransitMode.LONG_DISTANCE -> R.string.results_mode_long_distance
  TransitMode.NIGHT_RAIL -> R.string.results_mode_night_rail
  TransitMode.FUNICULAR -> R.string.results_mode_funicular
  TransitMode.AERIAL_LIFT -> R.string.results_mode_aerial_lift
  TransitMode.TRANSIT, TransitMode.OTHER -> R.string.results_mode_transit
}

// Le type de véhicule partagé vient du bloc `rental`, marqué « expérimental » côté MOTIS : il est
// souvent absent. Sans lui, on annonce « véhicule partagé », ce qui reste vrai (SPEC.md § 5.2).
@DrawableRes
private fun RentalFormFactor?.rentalIconRes(): Int = when (this) {
  RentalFormFactor.BICYCLE, RentalFormFactor.CARGO_BICYCLE -> R.drawable.ic_pedal_bike
  RentalFormFactor.CAR -> R.drawable.ic_directions_car
  RentalFormFactor.MOPED -> R.drawable.ic_moped
  RentalFormFactor.SCOOTER_STANDING, RentalFormFactor.SCOOTER_SEATED -> R.drawable.ic_electric_scooter
  RentalFormFactor.OTHER, null -> R.drawable.ic_bike_scooter
}

@StringRes
private fun RentalFormFactor?.rentalLabelRes(): Int = when (this) {
  RentalFormFactor.BICYCLE -> R.string.results_mode_rental_bike
  RentalFormFactor.CARGO_BICYCLE -> R.string.results_mode_rental_cargo_bike
  RentalFormFactor.CAR -> R.string.results_mode_rental_car
  RentalFormFactor.MOPED -> R.string.results_mode_rental_moped
  RentalFormFactor.SCOOTER_STANDING, RentalFormFactor.SCOOTER_SEATED -> R.string.results_mode_rental_scooter
  RentalFormFactor.OTHER, null -> R.string.results_mode_rental
}

/**
 * L'heure d'une portion, dans le fuseau de l'appareil, dans sa langue, et **au format choisi par
 * l'usager** (SPEC.md § 5.6).
 *
 * Le réglage 12 h / 24 h l'emporte sur celui du système quand il est explicite : `uses24HourClock`
 * fait la lecture, `ClockTime` le formatage, et cet écran ne décide de rien. C'est le seul moyen
 * d'être cohérent avec l'écran de recherche et le sélecteur d'heure, qui passent par les mêmes
 * deux fonctions.
 *
 * `LocalConfiguration`, et non `LocalContext.current.resources` : seul le premier invalide la
 * composition quand la langue ou le fuseau de l'appareil changent.
 */
@Composable
internal fun rememberTimeFormatter(): (Instant) -> String {
  val locale = LocalConfiguration.current.locales[0]
  val use24Hour = uses24HourClock()
  val zone = ZoneId.systemDefault()
  return remember(locale, use24Hour, zone) {
    { instant: Instant -> ClockTime.format(instant, zone, locale, use24Hour) }
  }
}

/** Une durée en toutes lettres. Le découpage vient de `:core`, la langue de `strings_results.xml`. */
@Composable
internal fun durationText(duration: Duration): String = durationText(FormattedDuration.of(duration))

@Composable
internal fun durationText(value: FormattedDuration): String = when {
  value.isZero -> stringResource(R.string.results_duration_under_minute)
  value.hours == 0L -> stringResource(R.string.results_duration_minutes, value.minutes)
  value.minutes == 0L -> stringResource(R.string.results_duration_hours, value.hours)
  else -> stringResource(R.string.results_duration_hours_minutes, value.hours, value.minutes)
}

/**
 * Ce qu'un onglet affiche sous son libellé : la durée du trajet le plus rapide qu'il propose, un
 * marqueur d'attente tant que sa réponse n'est pas là, un tiret quand il n'a rien (SPEC.md § 5.2).
 *
 * Les deux marqueurs ne sont pas des mots : le lecteur d'écran n'en entendrait rien de sensé, et
 * c'est [tabDescription] qui les dit en toutes lettres.
 */
@Composable
internal fun headlineText(headline: TabHeadline): String = when (headline) {
  TabHeadline.Pending -> stringResource(R.string.results_tab_duration_pending)
  TabHeadline.None -> stringResource(R.string.results_tab_duration_none)
  is TabHeadline.Fastest -> compactDurationText(headline.duration)
}

/**
 * La même durée, mais dans sa forme courte quand elle en a une : « 1 h 17 » plutôt que
 * « 1 h 17 min ».
 *
 * Le bandeau donne le quart de la largeur de l'écran à chaque onglet, quelle que soit la taille de
 * texte réglée par l'usager : au-delà de 130 % d'agrandissement, la forme longue s'y coupait en
 * deux lignes et l'onglet concerné devenait plus haut que ses voisins. C'est `:core` qui dit
 * quelles durées gagnent à être raccourcies, pas cet écran.
 */
@Composable
private fun compactDurationText(duration: Duration): String {
  val value = FormattedDuration.of(duration)
  return if (value.spellsBothUnits) {
    stringResource(R.string.results_tab_duration_hours_minutes, value.hours, value.minutes)
  } else {
    durationText(value)
  }
}

/** L'onglet tel qu'un lecteur d'écran l'annonce : sa catégorie, puis ce qu'elle propose. */
@Composable
internal fun tabDescription(category: JourneyCategory, headline: TabHeadline): String {
  val label = stringResource(category.labelRes())
  return when (headline) {
    TabHeadline.Pending -> stringResource(R.string.results_tab_description_pending, label)

    TabHeadline.None -> stringResource(R.string.results_tab_description_none, label)

    is TabHeadline.Fastest ->
      stringResource(R.string.results_tab_description_fastest, label, durationText(headline.duration))
  }
}

/**
 * La couleur d'un écart à l'horaire : vert à l'heure, orange, rouge (SPEC.md § 5.2).
 *
 * **Elle ne porte jamais l'information à elle seule** : l'appelant l'accompagne toujours d'un texte
 * et d'une icône (SPEC.md § 9). Le vert et l'orange ne viennent pas de la palette Material, qui
 * n'en a pas ; ils sont choisis pour rester au niveau AA sur la surface des deux thèmes.
 */
@Composable
internal fun delayColor(quality: DelayQuality): Color {
  val dark = LocalDarkTheme.current
  return when (quality) {
    DelayQuality.ON_TIME, DelayQuality.EARLY -> if (dark) OnTimeDark else OnTimeLight
    DelayQuality.SLIGHT -> if (dark) SlightDark else SlightLight
    DelayQuality.SEVERE -> MaterialTheme.colorScheme.error
  }
}

private const val ON_TIME_LIGHT = 0xFF1B5E20
private const val ON_TIME_DARK = 0xFF7ED18F
private const val SLIGHT_LIGHT = 0xFF8F4700
private const val SLIGHT_DARK = 0xFFFFB77C

private val OnTimeLight = Color(ON_TIME_LIGHT)
private val OnTimeDark = Color(ON_TIME_DARK)
private val SlightLight = Color(SLIGHT_LIGHT)
private val SlightDark = Color(SLIGHT_DARK)

/** La couleur de ligne du serveur, ou `null` quand il n'en publie pas (SPEC.md § 5.2). */
internal fun routeColor(value: String?): Color? = HexColor.parse(value)?.let(::Color)

/**
 * La couleur de texte à poser sur une pastille de ligne colorée par le réseau.
 *
 * Trois cas, et un seul endroit pour les trancher (SPEC.md § 9) :
 * - le serveur publie une couleur de texte **qui se lit** sur le fond : c'est elle ;
 * - il n'en publie pas, ou celle qu'il publie n'atteint pas le niveau AA : le noir ou le blanc,
 *   celui des deux qui contraste — un blanc posé sur le jaune d'un tramway ne se lit pas ;
 * - le serveur ne publie **aucune** couleur de fond : la pastille est alors celle du thème, et
 *   c'est le `on…` du thème qui va avec. Une `routeTextColor` isolée n'aurait aucun contraste
 *   garanti sur un fond dont elle ne sait rien.
 */
internal fun onRouteColor(textColor: String?, background: String?, fallback: Color): Color {
  val resolved = HexColor.textOn(background = background, preferred = textColor) ?: return fallback
  return Color(checkNotNull(HexColor.parse(resolved)))
}
