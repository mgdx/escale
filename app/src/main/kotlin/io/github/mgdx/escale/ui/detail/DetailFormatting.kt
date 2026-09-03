package io.github.mgdx.escale.ui.detail

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.DistanceUnit
import io.github.mgdx.escale.core.format.FormattedDistance
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPropulsionType
import io.github.mgdx.escale.core.model.RentalReturnConstraint
import io.github.mgdx.escale.core.model.StepDirection
import io.github.mgdx.escale.core.model.WheelchairAccess
import java.text.NumberFormat
import java.time.Instant

/*
 * Ce qui traduit le domaine de l'écran de détail en ressources : pictogrammes et libellés.
 *
 * Comme `ResultsFormatting`, rien ici ne décide de quoi que ce soit — les règles sont dans `:core`,
 * testées en JVM. Ce fichier ne fait que choisir la ressource qui correspond.
 *
 * **Chaque pictogramme est doublé d'un libellé textuel** (SPEC.md § 9) : c'est pour cela que les
 * deux tables vont toujours par paires.
 */

/**
 * Le pictogramme d'une manœuvre.
 *
 * Trois tracés servent deux fois, retournés à l'affichage par [isMirrored] : un « tourner à
 * droite » est le « tourner à gauche » vu dans un miroir. Le libellé, lui, reste distinct, et c'est
 * lui qui porte l'information (SPEC.md § 9).
 */
@DrawableRes
internal fun StepDirection.iconRes(): Int = when (this) {
  StepDirection.DEPART -> R.drawable.ic_trip_origin

  StepDirection.CONTINUE -> R.drawable.ic_straight

  StepDirection.LEFT, StepDirection.SLIGHTLY_LEFT, StepDirection.HARD_LEFT,
  StepDirection.RIGHT, StepDirection.SLIGHTLY_RIGHT, StepDirection.HARD_RIGHT,
  -> R.drawable.ic_turn_left

  StepDirection.CIRCLE_CLOCKWISE, StepDirection.CIRCLE_COUNTERCLOCKWISE -> R.drawable.ic_roundabout_left

  StepDirection.STAIRS -> R.drawable.ic_stairs

  StepDirection.ELEVATOR -> R.drawable.ic_elevator

  StepDirection.UTURN_LEFT, StepDirection.UTURN_RIGHT -> R.drawable.ic_u_turn_left
}

/** Vrai quand le tracé de [iconRes] doit être retourné horizontalement pour dire « à droite ». */
internal fun StepDirection.isMirrored(): Boolean = when (this) {
  StepDirection.RIGHT,
  StepDirection.SLIGHTLY_RIGHT,
  StepDirection.HARD_RIGHT,
  StepDirection.UTURN_RIGHT,
  StepDirection.CIRCLE_CLOCKWISE,
  -> true

  else -> false
}

// Une table de correspondance, pas un algorithme : detekt y compte une branche par manœuvre, ce
// qui n'a pas de sens ici. La découper en sous-fonctions rendrait la table moins lisible, pas plus.
@Suppress("CyclomaticComplexMethod")
@StringRes
internal fun StepDirection.labelRes(): Int = when (this) {
  StepDirection.DEPART -> R.string.detail_step_depart
  StepDirection.CONTINUE -> R.string.detail_step_continue
  StepDirection.LEFT -> R.string.detail_step_left
  StepDirection.SLIGHTLY_LEFT -> R.string.detail_step_slightly_left
  StepDirection.HARD_LEFT -> R.string.detail_step_hard_left
  StepDirection.RIGHT -> R.string.detail_step_right
  StepDirection.SLIGHTLY_RIGHT -> R.string.detail_step_slightly_right
  StepDirection.HARD_RIGHT -> R.string.detail_step_hard_right
  StepDirection.CIRCLE_CLOCKWISE -> R.string.detail_step_circle_clockwise
  StepDirection.CIRCLE_COUNTERCLOCKWISE -> R.string.detail_step_circle_counterclockwise
  StepDirection.STAIRS -> R.string.detail_step_stairs
  StepDirection.ELEVATOR -> R.string.detail_step_elevator
  StepDirection.UTURN_LEFT -> R.string.detail_step_uturn_left
  StepDirection.UTURN_RIGHT -> R.string.detail_step_uturn_right
}

@StringRes
internal fun WheelchairAccess.labelRes(): Int = when (this) {
  WheelchairAccess.ACCESSIBLE -> R.string.detail_wheelchair_accessible
  WheelchairAccess.NOT_ACCESSIBLE -> R.string.detail_wheelchair_not_accessible
}

@DrawableRes
internal fun WheelchairAccess.iconRes(): Int = when (this) {
  WheelchairAccess.ACCESSIBLE -> R.drawable.ic_accessible
  WheelchairAccess.NOT_ACCESSIBLE -> R.drawable.ic_not_accessible
}

@StringRes
internal fun RentalPropulsionType.labelRes(): Int = when (this) {
  RentalPropulsionType.HUMAN -> R.string.detail_rental_propulsion_human
  RentalPropulsionType.ELECTRIC_ASSIST -> R.string.detail_rental_propulsion_electric_assist
  RentalPropulsionType.ELECTRIC -> R.string.detail_rental_propulsion_electric
  RentalPropulsionType.COMBUSTION -> R.string.detail_rental_propulsion_combustion
  RentalPropulsionType.COMBUSTION_DIESEL -> R.string.detail_rental_propulsion_combustion_diesel
  RentalPropulsionType.HYBRID -> R.string.detail_rental_propulsion_hybrid
  RentalPropulsionType.PLUG_IN_HYBRID -> R.string.detail_rental_propulsion_plug_in_hybrid
  RentalPropulsionType.HYDROGEN_FUEL_CELL -> R.string.detail_rental_propulsion_hydrogen
}

/**
 * Le pluriel qui nomme les véhicules disponibles d'une station : « 7 vélos disponibles »,
 * « 3 trottinettes disponibles » (SPEC.md § 5.3).
 *
 * Le nom du véhicule est **choisi par le type de la station ou de la portion**, jamais générique par
 * commodité : annoncer « 7 véhicules » devant une station de trottinettes fait espérer autre chose
 * que ce qu'on y trouvera. « Véhicule » ne sert que lorsque le type est vraiment inconnu.
 *
 * Le vocabulaire est celui de l'onglet Vélo (`strings_results.xml`) : un `MOPED` est un scooter,
 * un `SCOOTER_STANDING` une trottinette. Les intervertir est l'erreur classique du domaine.
 */
@PluralsRes
internal fun RentalFormFactor?.availablePluralRes(): Int = when (this) {
  RentalFormFactor.BICYCLE -> R.plurals.detail_rental_available_bikes
  RentalFormFactor.CARGO_BICYCLE -> R.plurals.detail_rental_available_cargo_bikes
  RentalFormFactor.SCOOTER_STANDING, RentalFormFactor.SCOOTER_SEATED -> R.plurals.detail_rental_available_scooters
  RentalFormFactor.MOPED -> R.plurals.detail_rental_available_mopeds
  RentalFormFactor.CAR -> R.plurals.detail_rental_available_cars
  RentalFormFactor.OTHER, null -> R.plurals.detail_rental_available_vehicles
}

/**
 * La contrainte de retour d'un véhicule partagé, **en clair** (SPEC.md § 5.3).
 *
 * Un exploitant qui ne la publie pas mérite sa propre phrase : « on ne sait pas » n'est pas « on
 * peut le laisser n'importe où », et laisser croire le second coûterait une amende à l'usager.
 */
@StringRes
internal fun RentalReturnConstraint?.labelRes(): Int = when (this) {
  RentalReturnConstraint.NONE -> R.string.detail_rental_return_none
  RentalReturnConstraint.ANY_STATION -> R.string.detail_rental_return_any_station
  RentalReturnConstraint.ROUNDTRIP_STATION -> R.string.detail_rental_return_roundtrip
  null -> R.string.detail_rental_return_unknown
}

/**
 * Le nom d'un point du trajet, garanti lisible.
 *
 * MOTIS ne nomme pas une extrémité envoyée en coordonnées : `:data` traduit ses marqueurs internes
 * `"START"` et `"END"` en absence de nom, et `DetailViewModel` y substitue ce que l'usager a saisi.
 * Ce dernier repli ne sert donc que lorsque même cela manque — après la mort du processus, par
 * exemple, où le brouillon de recherche est vide. **Aucun vocabulaire d'API ne doit s'afficher.**
 */
@Composable
internal fun placeLabel(name: String, @StringRes genericRes: Int): String =
  name.takeIf(String::isNotBlank) ?: stringResource(genericRes)

/**
 * Une distance mise en forme dans la langue de l'usager : « 450 m », « 2,3 km ».
 *
 * L'arrondi vient de `:core` ; seule la ponctuation du nombre est ici, parce qu'elle dépend de la
 * langue du système et qu'un « 2.3 km » dans une interface française serait une faute.
 */
@Composable
internal fun distanceText(meters: Double): String {
  val distance = FormattedDistance.of(meters)
  val locale = LocalConfiguration.current.locales[0]
  val number = remember(distance, locale) {
    NumberFormat.getNumberInstance(locale).apply {
      minimumFractionDigits = distance.decimals
      maximumFractionDigits = distance.decimals
    }.format(distance.value)
  }
  return when (distance.unit) {
    DistanceUnit.METERS -> stringResource(R.string.detail_distance_meters, number)
    DistanceUnit.KILOMETERS -> stringResource(R.string.detail_distance_kilometers, number)
  }
}

/**
 * Une date et une heure, pour la période de validité d'une perturbation.
 *
 * Le format vient de la plateforme, qui connaît la langue et le réglage 12 h / 24 h : aucun motif
 * n'est codé en dur.
 */
@Composable
internal fun dateTimeText(instant: Instant): String {
  val context = LocalContext.current
  return remember(context, instant) {
    DateUtils.formatDateTime(
      context,
      instant.toEpochMilli(),
      DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
  }
}

/** Le retour d'un ajout aux favoris (SPEC.md § 5.5), nommé par le `ViewModel`, écrit ici. */
@StringRes
internal fun DetailMessage.textRes(): Int = when (this) {
  DetailMessage.FAVORITE_ADDED -> R.string.detail_favorite_added
  DetailMessage.FAVORITE_FAILED -> R.string.detail_favorite_failed
}
