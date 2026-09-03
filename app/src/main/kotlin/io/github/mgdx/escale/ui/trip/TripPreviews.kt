package io.github.mgdx.escale.ui.trip

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.WheelchairAccess
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Duration
import java.time.Instant

/*
 * Les aperçus de la desserte d'une course.
 *
 * Ils servent d'abord SPEC.md § 9 : le nom de la ligne et sa direction vivent dans une barre de
 * titre que Material fige à 64 dp, et rien ne disait qu'ils y tenaient à 200 %. « TER Nouvelle-
 * Aquitaine » est là pour ça — c'est le genre de nom qui se coupait.
 *
 * Aucune donnée d'usager : une ligne publique, des gares publiques, des heures fixes.
 */

private val ORIGIN: Instant = Instant.parse("2026-03-03T08:00:00Z")

private fun at(minutes: Long): Instant = ORIGIN.plusSeconds(minutes * 60)

private fun place(name: String, minutes: Long, delayMinutes: Long = 0, track: String? = null) = Place(
  name = name,
  coordinates = LatLon(lat = 44.84, lon = -0.58),
  stopId = name,
  track = track,
  scheduledTime = at(minutes),
  time = at(minutes + delayMinutes),
)

private fun call(
  name: String,
  minutes: Long,
  delayMinutes: Long = 0,
  track: String? = null,
  cancelled: Boolean = false,
  alerts: List<Disruption> = emptyList(),
) = StopVisit(
  place = place(name, minutes, delayMinutes, track).copy(alerts = alerts),
  arrival = at(minutes + delayMinutes),
  departure = at(minutes + delayMinutes + 1),
  cancelled = cancelled,
)

private val previewLeg = JourneyLeg.Transit(
  startTime = at(0),
  endTime = at(96),
  scheduledStartTime = at(0),
  scheduledEndTime = at(96),
  duration = Duration.ofMinutes(96),
  from = place("Bordeaux-Saint-Jean", 0, track = "5"),
  to = place("Périgueux", 96),
  realTime = true,
  mode = TransitMode.REGIONAL_RAIL,
  lineName = "TER Nouvelle-Aquitaine",
  routeShortName = "TER",
  routeColor = "#4DBD38",
  routeTextColor = "#FFFFFF",
  headsign = "Périgueux",
  agencyName = "SNCF Voyageurs",
  wheelchairAccessible = WheelchairAccess.ACCESSIBLE,
  bikesAllowed = true,
  intermediateStops = listOf(
    call("Libourne", 28, track = "B"),
    call("Coutras", 42, delayMinutes = 3),
    call(
      name = "Montpon-Ménestérol",
      minutes = 58,
      cancelled = true,
      alerts = listOf(
        Disruption(
          headerText = "Arrêt non desservi",
          descriptionText = "Travaux sur le quai jusqu'au 12 mars.",
          severity = DisruptionSeverity.WARNING,
        ),
      ),
    ),
    call("Mussidan", 72),
    call("Saint-Astier", 84),
  ),
)

private val previewState = TripUiState(
  lineName = "TER Nouvelle-Aquitaine",
  headsign = "Périgueux",
  journey = Journey(
    id = "trip",
    startTime = previewLeg.startTime,
    endTime = previewLeg.endTime,
    scheduledStartTime = previewLeg.scheduledStartTime,
    scheduledEndTime = previewLeg.scheduledEndTime,
    duration = previewLeg.duration,
    transfers = 0,
    legs = listOf(previewLeg),
  ),
  loadedAt = ORIGIN,
)

@Composable
private fun PreviewTrip(state: TripUiState) {
  EscaleTheme(dynamicColor = false) {
    TripContent(state = state, onBack = {}, onRefresh = {})
  }
}

@Preview(name = "Desserte, thème clair", heightDp = 900, showBackground = true)
@Preview(
  name = "Desserte, thème sombre",
  heightDp = 900,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TripListPreview() = PreviewTrip(previewState)

/** Le cas qui compte pour SPEC.md § 9 : un nom de ligne long, une direction, du texte doublé. */
@Preview(name = "Desserte, texte à 200 %", heightDp = 1600, fontScale = 2f, showBackground = true)
@Composable
private fun TripLargeTextPreview() = PreviewTrip(previewState)

@Preview(name = "Desserte vide", heightDp = 400, showBackground = true)
@Composable
private fun TripEmptyPreview() = PreviewTrip(
  previewState.copy(journey = previewState.journey?.copy(legs = emptyList())),
)

@Preview(name = "Desserte, serveur injoignable", heightDp = 400, showBackground = true)
@Composable
private fun TripErrorPreview() = PreviewTrip(
  previewState.copy(journey = null, error = EscaleError.ServerUnreachable(statusCode = 503)),
)

@Preview(name = "Desserte, chargement", heightDp = 400, showBackground = true)
@Composable
private fun TripLoadingPreview() = PreviewTrip(previewState.copy(journey = null, loading = true))
