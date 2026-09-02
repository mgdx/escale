// Les nombres de ce fichier — coordonnées, minutes, distances — **sont** la donnée d'exemple :
// les baptiser un par un n'apprendrait rien à personne. La suppression est limitée à ce fichier
// d'aperçus, qui ne porte aucune règle.
@file:Suppress("MagicNumber")

package io.github.mgdx.escale.ui.detail

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.DisruptionEffect
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.RentalPropulsionType
import io.github.mgdx.escale.core.model.RentalReturnConstraint
import io.github.mgdx.escale.core.model.StepDirection
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TimeWindow
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.TravelStep
import io.github.mgdx.escale.core.model.WheelchairAccess
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Duration
import java.time.Instant

/**
 * Aperçus de l'écran de détail.
 *
 * Les valeurs qui suivent sont des **données** d'exemple — noms d'arrêts, numéros de ligne, nom
 * d'exploitant, texte de perturbation — et non des libellés d'interface : elles ne se traduisent
 * pas et ne s'affichent jamais dans l'application, qui ne connaît que ce que le serveur renvoie.
 *
 * Chaque cas est décliné en clair, en sombre et à 200 % d'agrandissement, la limite que
 * SPEC.md § 9 impose de tenir sans troncature.
 */
private val ORIGIN: Instant = Instant.parse("2026-09-01T08:00:00Z")

private fun at(minutes: Long): Instant = ORIGIN.plusSeconds(minutes * 60)

private fun place(name: String, time: Instant, track: String? = null, delayMinutes: Long = 0) = Place(
  name = name,
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  stopId = null,
  track = track,
  scheduledTime = time,
  time = time.plusSeconds(delayMinutes * 60),
)

private fun stop(name: String, minutes: Long) = StopVisit(
  place = place(name, at(minutes)),
  arrival = at(minutes),
  departure = at(minutes),
)

private fun step(direction: StepDirection, street: String, meters: Double, up: Int = 0, down: Int = 0) = TravelStep(
  direction = direction,
  streetName = street,
  distanceMeters = meters,
  elevationUpMeters = up,
  elevationDownMeters = down,
)

private val walkLeg = JourneyLeg.Walk(
  startTime = at(0),
  endTime = at(6),
  scheduledStartTime = at(0),
  scheduledEndTime = at(6),
  duration = Duration.ofMinutes(6),
  from = place("Rue de Bercy", at(0)),
  to = place("Gare de Lyon", at(6)),
  distanceMeters = 430.0,
  steps = listOf(
    step(StepDirection.DEPART, "Rue de Bercy", 40.0),
    step(StepDirection.LEFT, "Boulevard Diderot", 210.0, up = 4),
    step(StepDirection.STAIRS, "", 12.0, up = 8),
    step(StepDirection.SLIGHTLY_RIGHT, "Place Louis-Armand", 168.0, down = 3),
  ),
)

private val disruption = Disruption(
  headerText = "Umleitung der Linie 1",
  descriptionText = "Wegen Gleisbauarbeiten fährt die Linie über eine Umleitung. " +
    "Bitte planen Sie fünf Minuten mehr ein.",
  severity = DisruptionSeverity.WARNING,
  effect = DisruptionEffect.DETOUR,
  periods = listOf(TimeWindow(start = at(-120), end = at(600))),
  url = "https://exemple.org/meldungen",
)

private val transitLeg = JourneyLeg.Transit(
  startTime = at(8),
  endTime = at(23),
  scheduledStartTime = at(6),
  scheduledEndTime = at(21),
  duration = Duration.ofMinutes(15),
  from = place("Gare de Lyon", at(6), track = "3", delayMinutes = 2),
  to = place("Nation", at(21), track = "B", delayMinutes = 2),
  distanceMeters = null,
  realTime = true,
  alerts = listOf(disruption),
  mode = TransitMode.SUBWAY,
  tripId = "course-1",
  lineName = "1",
  routeShortName = "1",
  headsign = "Château de Vincennes",
  agencyName = "RATP",
  routeColor = "#FFCD00",
  routeTextColor = "#000000",
  intermediateStops = listOf(
    stop("Reuilly — Diderot", 11),
    stop("Nation — Dorian", 16),
  ),
  wheelchairAccessible = WheelchairAccess.ACCESSIBLE,
  bikesAllowed = false,
)

private val rentalLeg = JourneyLeg.Rental(
  startTime = at(23),
  endTime = at(31),
  scheduledStartTime = at(23),
  scheduledEndTime = at(31),
  duration = Duration.ofMinutes(8),
  from = place("Place de la Nation", at(23)),
  to = place("Rue des Boulets", at(31)),
  distanceMeters = 1620.0,
  rental = RentalInfo(
    systemId = "velib",
    systemName = "Vélib' Métropole",
    providerId = null,
    color = "#1D9E75",
    url = "https://exemple.org/velib",
    fromStationName = "Nation — Place de la Nation",
    toStationName = null,
    rentalUriAndroid = "https://exemple.org/station/4201",
    formFactor = RentalFormFactor.BICYCLE,
    propulsionType = RentalPropulsionType.ELECTRIC_ASSIST,
    returnConstraint = RentalReturnConstraint.ANY_STATION,
  ),
  steps = listOf(step(StepDirection.CONTINUE, "Cours de Vincennes", 900.0, up = 12)),
)

private val sampleJourney = Journey(
  id = "trajet-1",
  startTime = walkLeg.startTime,
  endTime = rentalLeg.endTime,
  scheduledStartTime = walkLeg.scheduledStartTime,
  scheduledEndTime = rentalLeg.scheduledEndTime,
  duration = Duration.between(walkLeg.startTime, rentalLeg.endTime),
  transfers = 0,
  legs = listOf(walkLeg, transitLeg, rentalLeg),
)

private val noActions = DetailActions(
  onRefresh = {},
  onLegToggled = {},
  onStopsToggled = {},
  onStepsToggled = {},
)

@Composable
private fun DetailPreview(state: DetailUiState) {
  EscaleTheme(dynamicColor = false) {
    DetailContent(state = state, actions = noActions, onBack = {})
  }
}

@Preview(showBackground = true, name = "Détail, thème clair")
@Preview(showBackground = true, name = "Détail, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Détail, 200 %", fontScale = 2f, heightDp = 1400)
@Composable
private fun DetailCollapsedPreview() {
  DetailPreview(DetailUiState(journey = sampleJourney, detailed = true, refreshedAt = at(24)))
}

@Preview(showBackground = true, name = "Portions dépliées, thème clair", heightDp = 1400)
@Preview(
  showBackground = true,
  name = "Portions dépliées, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  heightDp = 1400,
)
@Preview(showBackground = true, name = "Portions dépliées, 200 %", fontScale = 2f, heightDp = 2600)
@Composable
private fun DetailExpandedPreview() {
  DetailPreview(
    DetailUiState(
      journey = sampleJourney,
      detailed = true,
      refreshedAt = at(24),
      expandedLegs = setOf(0, 1, 2),
      expandedStops = setOf(1),
      expandedSteps = setOf(0),
    ),
  )
}

@Preview(showBackground = true, name = "Détail en cours de chargement")
@Preview(
  showBackground = true,
  name = "Détail en cours de chargement, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailLoadingPreview() {
  DetailPreview(DetailUiState(journey = sampleJourney, loading = true))
}

@Preview(showBackground = true, name = "Détail indisponible")
@Preview(showBackground = true, name = "Détail indisponible, thème sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailErrorPreview() {
  DetailPreview(DetailUiState(journey = sampleJourney, error = EscaleError.NoNetwork))
}
