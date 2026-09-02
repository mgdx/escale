// Les nombres de ce fichier — coordonnées, minutes, hauteurs d'aperçu — **sont** la donnée
// d'exemple : les baptiser un par un n'apprendrait rien à personne. La suppression est limitée à ce
// fichier d'aperçus, qui ne porte aucune règle.
@file:Suppress("MagicNumber")

package io.github.mgdx.escale.ui.results

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.JourneyFeed
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Duration
import java.time.Instant

/**
 * Aperçus de la feuille de résultats.
 *
 * Les valeurs qui suivent sont des **données** d'exemple — noms d'arrêts, numéros de ligne, nom
 * d'exploitant — et non des libellés d'interface : elles ne se traduisent pas et ne s'affichent
 * jamais dans l'application, qui ne connaît que ce que le serveur lui renvoie. Tout ce qui est
 * libellé, lui, vient de `strings_results.xml`.
 *
 * Les quatre cas vérifiés ici sont ceux que SPEC.md § 10 impose à `:app` : liste, vide, erreur,
 * chargement — en clair, en sombre, et à 200 % d'agrandissement, le pire cas d'une frise de trajet.
 */
private val ORIGIN: Instant = Instant.parse("2026-09-01T08:00:00Z")

private fun at(minutes: Long): Instant = ORIGIN.plusSeconds(minutes * 60)

private fun place(name: String, time: Instant) = Place(
  name = name,
  coordinates = LatLon(lat = 48.8443, lon = 2.3735),
  stopId = null,
  track = null,
  scheduledTime = time,
  time = time,
)

private fun walkLeg(from: Long, to: Long) = JourneyLeg.Walk(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("Rue de Bercy", at(from)),
  to = place("Gare de Lyon", at(to)),
)

private fun transitLeg(
  from: Long,
  to: Long,
  mode: TransitMode,
  line: String,
  color: String?,
  delayMinutes: Long = 0,
  alerts: List<Disruption> = emptyList(),
) = JourneyLeg.Transit(
  startTime = at(from + delayMinutes),
  endTime = at(to + delayMinutes),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("Gare de Lyon", at(from + delayMinutes)),
  to = place("Nation", at(to + delayMinutes)),
  realTime = true,
  alerts = alerts,
  mode = mode,
  lineName = line,
  routeShortName = line,
  routeColor = color,
  routeTextColor = null,
)

private fun rentalLeg(from: Long, to: Long) = JourneyLeg.Rental(
  startTime = at(from),
  endTime = at(to),
  scheduledStartTime = at(from),
  scheduledEndTime = at(to),
  duration = Duration.ofMinutes(to - from),
  from = place("Quai de la Rapée", at(from)),
  to = place("Place de la Nation", at(to)),
  rental = RentalInfo(
    systemId = "velib",
    systemName = "Vélib'",
    providerId = null,
    color = "#1D9E75",
    url = null,
    fromStationName = null,
    toStationName = null,
    rentalUriAndroid = null,
    formFactor = RentalFormFactor.BICYCLE,
    propulsionType = null,
    returnConstraint = null,
  ),
)

private fun sampleJourney(id: String, legs: List<JourneyLeg>) = Journey(
  id = id,
  startTime = legs.first().startTime,
  endTime = legs.last().endTime,
  scheduledStartTime = legs.first().scheduledStartTime,
  scheduledEndTime = legs.last().scheduledEndTime,
  duration = Duration.between(legs.first().startTime, legs.last().endTime),
  transfers = legs.count { it is JourneyLeg.Transit }.coerceAtLeast(1) - 1,
  legs = legs,
)

private val transitJourney = sampleJourney(
  id = "transit",
  legs = listOf(
    walkLeg(0, 6),
    transitLeg(6, 21, TransitMode.SUBWAY, "1", "#FFCD00", delayMinutes = 3),
    walkLeg(21, 23),
    transitLeg(
      from = 23,
      to = 44,
      mode = TransitMode.BUS,
      line = "86",
      color = null,
      alerts = listOf(Disruption(headerText = "Travaux", descriptionText = "Arrêt non desservi")),
    ),
    walkLeg(44, 48),
  ),
)

private val rentalJourney = sampleJourney(id = "rental", legs = listOf(walkLeg(0, 3), rentalLeg(3, 27)))

private val previewState = ResultsUiState(
  open = true,
  category = JourneyCategory.TRANSIT,
  tabs = mapOf(
    JourneyCategory.TRANSIT to TabResults(
      feed = JourneyFeed(
        journeys = listOf(transitJourney, rentalJourney),
        previousPageCursor = "avant",
        nextPageCursor = "apres",
      ),
      // L'heure du chargement : c'est elle qui décide des perturbations « en vigueur ».
      loadedAt = ORIGIN,
    ),
  ),
  selectedKey = "transit",
)

private val previewActions = ResultsActions(
  onCategorySelected = {},
  onRetry = {},
  onEarlier = {},
  onLater = {},
  onJourneySelected = {},
  onBikeFilterChanged = {},
  onRefresh = {},
)

@Composable
private fun PreviewSheet(state: ResultsUiState) {
  EscaleTheme(dynamicColor = false) {
    ResultsSheet(state = state, actions = previewActions, padding = PaddingValues())
  }
}

@Preview(name = "Résultats, thème clair", heightDp = 600, showBackground = true)
@Preview(
  name = "Résultats, thème sombre",
  heightDp = 600,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ResultsListPreview() = PreviewSheet(previewState)

@Preview(name = "Résultats à 200 %", heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun ResultsLargeTextPreview() = PreviewSheet(previewState)

@Preview(name = "Onglet à pied sans résultat", heightDp = 500, showBackground = true)
@Composable
private fun ResultsEmptyPreview() = PreviewSheet(
  previewState.copy(
    category = JourneyCategory.WALK,
    tabs = mapOf(JourneyCategory.WALK to TabResults(feed = JourneyFeed())),
  ),
)

@Preview(name = "Serveur injoignable", heightDp = 400, showBackground = true)
@Composable
private fun ResultsErrorPreview() = PreviewSheet(
  previewState.copy(
    tabs = mapOf(JourneyCategory.TRANSIT to TabResults(error = EscaleError.ServerUnreachable(statusCode = 503))),
  ),
)

@Preview(name = "Recherche en cours", heightDp = 400, showBackground = true)
@Composable
private fun ResultsLoadingPreview() = PreviewSheet(
  previewState.copy(tabs = mapOf(JourneyCategory.TRANSIT to TabResults(loading = true))),
)
