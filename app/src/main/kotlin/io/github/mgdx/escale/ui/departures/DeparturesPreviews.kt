package io.github.mgdx.escale.ui.departures

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.mgdx.escale.core.model.DepartureFeed
import io.github.mgdx.escale.core.model.DepartureModeFilter
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.ui.theme.EscaleTheme
import java.time.Instant

/*
 * Les aperçus de l'écran des prochains départs.
 *
 * Ils existent d'abord pour SPEC.md § 9 : « compatible avec l'agrandissement des polices jusqu'à
 * 200 % sans troncature ». Le nom de l'arrêt et le résumé des lignes vivent dans la barre de titre,
 * dont Material fige la hauteur à 64 dp ; sans un aperçu à `fontScale = 2f`, rien ne dit qu'ils y
 * tiennent. Les états vide et d'erreur y sont aussi, comme le demande docs/architecture.md § 10.
 *
 * Aucune donnée d'usager ici : un arrêt public, des lignes publiques, des heures fixes.
 */

private val ORIGIN: Instant = Instant.parse("2026-03-03T08:00:00Z")

private fun at(minutes: Long): Instant = ORIGIN.plusSeconds(minutes * 60)

private fun departure(
  id: String,
  mode: TransitMode,
  line: String,
  headsign: String,
  minutes: Long,
  delayMinutes: Long = 0,
  track: String? = null,
  cancelled: Boolean = false,
  color: String? = null,
  textColor: String? = null,
  alerts: List<Disruption> = emptyList(),
) = StopTimeEntry(
  tripId = id,
  mode = mode,
  lineName = line,
  headsign = headsign,
  agencyName = "RATP",
  track = track,
  scheduledTime = at(minutes),
  time = at(minutes + delayMinutes),
  realTime = true,
  cancelled = cancelled,
  routeColor = color,
  routeTextColor = textColor,
  alerts = alerts,
)

private val previewState = DeparturesUiState(
  // Un nom long exprès : c'est lui qui révèle la troncature de la barre de titre à 200 %.
  stopName = "Charles de Gaulle — Étoile",
  lines = listOf(
    StopLine("1", "1", "", TransitMode.SUBWAY, "RATP", "#FFCD00", "#000000"),
    StopLine("6", "6", "", TransitMode.SUBWAY, "RATP", "#6ECA97", "#000000"),
    StopLine("A", "RER A", "", TransitMode.SUBURBAN, "RATP", "#E3051C", "#FFFFFF"),
    StopLine("22", "22", "", TransitMode.BUS, "RATP"),
  ),
  filters = listOf(DepartureModeFilter.SUBWAY, DepartureModeFilter.BUS),
  feed = DepartureFeed(
    entries = listOf(
      departure("a", TransitMode.SUBWAY, "1", "Château de Vincennes", 2, color = "#FFCD00", textColor = "#000000"),
      departure("b", TransitMode.SUBURBAN, "RER A", "Marne-la-Vallée — Chessy", 5, delayMinutes = 4, track = "2"),
      departure("c", TransitMode.BUS, "22", "Porte de Saint-Cloud", 7, cancelled = true),
      departure(
        id = "d",
        mode = TransitMode.SUBWAY,
        line = "6",
        headsign = "Nation",
        minutes = 9,
        color = "#6ECA97",
        alerts = listOf(
          Disruption(
            headerText = "Travaux sur la ligne 6",
            descriptionText = "Trafic interrompu entre Trocadéro et Bir-Hakeim.",
            severity = DisruptionSeverity.WARNING,
          ),
        ),
      ),
    ),
    previousPageCursor = "avant",
    nextPageCursor = "apres",
  ),
  loadedAt = ORIGIN,
)

@Composable
private fun PreviewDepartures(state: DeparturesUiState) {
  EscaleTheme(dynamicColor = false) {
    DeparturesContent(
      state = state,
      onBack = {},
      onRefresh = {},
      onFilterSelected = {},
      onPage = {},
      onOpenTrip = {},
    )
  }
}

@Preview(name = "Départs, thème clair", heightDp = 800, showBackground = true)
@Preview(
  name = "Départs, thème sombre",
  heightDp = 800,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DeparturesListPreview() = PreviewDepartures(previewState)

/** Le cas qui compte pour SPEC.md § 9 : nom d'arrêt long, quatre lignes, texte doublé. */
@Preview(name = "Départs, texte à 200 %", heightDp = 1400, fontScale = 2f, showBackground = true)
@Composable
private fun DeparturesLargeTextPreview() = PreviewDepartures(previewState)

@Preview(name = "Départs, aucun passage", heightDp = 500, showBackground = true)
@Preview(name = "Départs, aucun passage à 200 %", heightDp = 800, fontScale = 2f, showBackground = true)
@Composable
private fun DeparturesEmptyPreview() = PreviewDepartures(
  previewState.copy(feed = DepartureFeed(), filters = emptyList()),
)

@Preview(name = "Départs, serveur injoignable", heightDp = 500, showBackground = true)
@Composable
private fun DeparturesErrorPreview() = PreviewDepartures(
  previewState.copy(feed = null, error = EscaleError.ServerUnreachable(statusCode = 503)),
)

@Preview(name = "Départs, chargement", heightDp = 400, showBackground = true)
@Composable
private fun DeparturesLoadingPreview() = PreviewDepartures(
  previewState.copy(feed = null, loading = true, filters = emptyList()),
)
