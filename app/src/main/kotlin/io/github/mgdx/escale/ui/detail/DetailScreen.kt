package io.github.mgdx.escale.ui.detail

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.format.JourneyShareLine
import io.github.mgdx.escale.core.format.JourneyTimeline
import io.github.mgdx.escale.core.format.TimeStatus
import io.github.mgdx.escale.core.format.TimelineSegment
import io.github.mgdx.escale.core.format.shareLinesOf
import io.github.mgdx.escale.core.format.transitLineLabel
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.results.DisruptionBanner
import io.github.mgdx.escale.ui.results.ForegroundEffect
import io.github.mgdx.escale.ui.results.durationText
import io.github.mgdx.escale.ui.results.modeIcon
import io.github.mgdx.escale.ui.results.modeLabel
import io.github.mgdx.escale.ui.results.onRouteColor
import io.github.mgdx.escale.ui.results.rememberTimeFormatter
import io.github.mgdx.escale.ui.results.routeColor
import java.time.Instant

/**
 * L'écran de détail d'un trajet (SPEC.md § 5.3) : une liste verticale de portions, chacune
 * dépliable.
 *
 * Le **tracé du trajet sur la carte** relève du lot voisin : cet écran ne dessine rien dans
 * `ui/map`. Il se contente de republier le trajet détaillé — celui qui porte enfin la géométrie —
 * dans `SelectedJourneyStore`, où le lot « tracé » le lit déjà.
 *
 * @param onBack sortie de l'écran. Elle ne libère rien : le trajet reste mis en évidence et tracé
 *   sur la carte au retour dans la liste (SPEC.md § 5.1).
 * @param onTripSelected ouvre l'écran « détail de la course » (`/api/v6/trip`, SPEC.md § 5.3).
 *   Nul tant qu'aucun appelant ne le branche : la commande n'apparaît alors pas dans les portions,
 *   un bouton qui ne mène nulle part étant pire que pas de bouton du tout.
 */
@Composable
fun DetailScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onTripSelected: ((tripId: String) -> Unit)? = null,
) {
  val container = appContainer()
  val viewModel: DetailViewModel = viewModel(factory = DetailViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Le retour système passe par le même chemin que la flèche de la barre : un seul endroit libère
  // le trajet choisi.
  BackHandler(onBack = onBack)
  // SPEC.md § 7.4 : au retour au premier plan, et seulement si les horaires affichés ont plus de
  // 60 secondes. Le bouton « Actualiser » de la barre, lui, rafraîchit sans condition.
  ForegroundEffect(viewModel::onForeground)
  // Plus rien à montrer — l'itinéraire redemandé au retour d'une mort du processus a été refusé,
  // et il n'y a pas de repli : l'écran se referme au lieu d'afficher une page blanche.
  LaunchedEffect(state.closed) { if (state.closed) onBack() }
  DetailContent(
    state = state,
    actions = rememberDetailActions(viewModel, onTripSelected),
    onBack = onBack,
    modifier = modifier,
  )
}

/** Les gestes de l'écran, regroupés pour ne pas les recréer à chaque recomposition. */
@Composable
private fun rememberDetailActions(
  viewModel: DetailViewModel,
  onTripSelected: ((tripId: String) -> Unit)?,
): DetailActions = remember(viewModel, onTripSelected) {
  DetailActions(
    onRefresh = viewModel::onRefresh,
    onRentalRefresh = viewModel::onRentalRefresh,
    onLegToggled = viewModel::onLegToggled,
    onStopsToggled = viewModel::onStopsToggled,
    onStepsToggled = viewModel::onStepsToggled,
    onTripSelected = onTripSelected,
    onToggleFavorite = viewModel::onToggleFavorite,
    onMessageShown = viewModel::onMessageShown,
  )
}

/**
 * Les gestes de l'écran de détail, et **les points d'accroche des jalons suivants**.
 *
 * Les deux derniers sont nuls tant que l'écran qu'ils appellent n'existe pas, et l'interface
 * n'affiche alors pas la commande correspondante : SPEC.md § 5.3 les prévoit, mais un bouton qui
 * ne fait rien est pire que pas de bouton du tout.
 */
internal data class DetailActions(
  val onRefresh: () -> Unit,
  /**
   * Le bouton de rafraîchissement de la disponibilité d'une portion en libre-service
   * (SPEC.md § 5.3), désignée par sa position dans le trajet.
   */
  val onRentalRefresh: (Int) -> Unit,
  val onLegToggled: (Int) -> Unit,
  val onStopsToggled: (Int) -> Unit,
  val onStepsToggled: (Int) -> Unit,
  /**
   * **Jalon 9** — appui sur la ligne d'une portion en transport en commun, vers l'écran « détail
   * de la course » (`/api/v6/trip`, SPEC.md § 5.3). Le lot qui écrira cet écran branche ici son
   * `navController.navigate(...)`, avec le `tripId` que porte déjà `JourneyLeg.Transit`.
   */
  val onTripSelected: ((tripId: String) -> Unit)? = null,
  /**
   * L'étoile de la barre supérieure (SPEC.md § 5.3 et § 5.5) : elle **bascule**, ajoutant le
   * couple départ / arrivée aux favoris ou l'en retirant. Reste facultative pour que les aperçus
   * composent l'écran sans dépôt — un bouton qui ne fait rien serait pire que pas de bouton du tout
   * (voir [onTripSelected]).
   */
  val onToggleFavorite: (() -> Unit)? = null,
  /** Le message affiché après un ajout a été montré : l'écran le dit, pour qu'il soit oublié. */
  val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailContent(
  state: DetailUiState,
  actions: DetailActions,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val journey = state.journey
  val snackbarHostState = remember { SnackbarHostState() }
  val messageText = state.message?.let { stringResource(it.textRes()) }
  LaunchedEffect(state.message) {
    if (messageText != null) {
      snackbarHostState.showSnackbar(messageText)
      actions.onMessageShown()
    }
  }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.detail_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        actions = {
          if (journey != null) DetailBarActions(journey = journey, state = state, actions = actions)
        },
      )
    },
  ) { innerPadding ->
    if (journey == null) {
      DetailPlaceholder(state = state, actions = actions, padding = innerPadding)
      return@Scaffold
    }
    DetailList(journey = journey, state = state, actions = actions, padding = innerPadding)
  }
}

/**
 * Ce que montre l'écran tant qu'il n'a pas de trajet.
 *
 * Le cas n'existe qu'au retour d'une mort du processus, où le trajet est reconstruit à partir de
 * son seul identifiant : l'attente est annoncée plutôt que laissée en page blanche, et un échec
 * passager garde son bouton « Réessayer » (SPEC.md § 8). Un identifiant périmé, lui, ne parvient
 * pas jusqu'ici : l'écran se referme.
 */
@Composable
private fun DetailPlaceholder(state: DetailUiState, actions: DetailActions, padding: PaddingValues) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(padding)
      .padding(ScreenPadding),
    contentAlignment = Alignment.Center,
  ) {
    if (state.error != null) {
      ErrorMessage(error = state.error, onRetry = actions.onRefresh)
    } else {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
      ) {
        CircularProgressIndicator()
        Text(
          text = stringResource(R.string.detail_loading_details),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** Rafraîchir, partager, mettre en favori (SPEC.md § 5.3). */
@Composable
private fun RowScope.DetailBarActions(journey: Journey, state: DetailUiState, actions: DetailActions) {
  val share = rememberShareAction(journey)
  val favorite = actions.onToggleFavorite
  if (favorite != null) {
    // L'étoile **porte l'état réel du trajet** : pleine s'il est déjà en favori, en contour sinon,
    // et son libellé dit ce que l'appui va faire. Deux dessins et deux libellés, jamais une nuance
    // de couleur : rien ne doit reposer sur la seule couleur (SPEC.md § 9).
    val saved = state.favoriteId != null
    IconButton(onClick = favorite) {
      Icon(
        painter = painterResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
        contentDescription = stringResource(
          if (saved) R.string.detail_action_favorite_remove else R.string.detail_action_favorite_add,
        ),
      )
    }
  }
  IconButton(onClick = actions.onRefresh) {
    Icon(
      painter = painterResource(R.drawable.ic_refresh),
      contentDescription = stringResource(R.string.detail_action_refresh),
    )
  }
  IconButton(onClick = share) {
    Icon(
      painter = painterResource(R.drawable.ic_share),
      contentDescription = stringResource(R.string.detail_action_share),
    )
  }
}

@Composable
private fun DetailList(journey: Journey, state: DetailUiState, actions: DetailActions, padding: PaddingValues) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(padding),
    contentPadding = PaddingValues(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(ListSpacing),
  ) {
    item(key = SUMMARY_KEY) { DetailSummary(journey = journey, state = state) }
    if (journey.alerts.isNotEmpty()) {
      // Le bandeau des perturbations en vigueur, le même qu'en tête de carte de résultat
      // (SPEC.md § 5.2). Le détail de chacune reste dans la portion qui la porte.
      item(key = ALERTS_KEY) { DisruptionBanner(alerts = journey.alerts, at = state.refreshedAt) }
    }
    if (state.error != null) {
      item(key = ERROR_KEY) { ErrorMessage(error = state.error, onRetry = actions.onRefresh) }
    }
    item(key = ORIGIN_KEY) {
      EndpointRow(
        labelRes = R.string.detail_origin,
        icon = R.drawable.ic_trip_origin,
        name = placeLabel(journey.legs.firstOrNull()?.from?.name.orEmpty(), R.string.detail_origin),
        time = journey.startTime,
      )
    }
    itemsIndexed(items = journey.legs, key = { index, _ -> index }) { index, leg ->
      LegCard(index = index, leg = leg, state = state, actions = actions)
    }
    item(key = DESTINATION_KEY) {
      EndpointRow(
        labelRes = R.string.detail_destination,
        icon = R.drawable.ic_place,
        name = placeLabel(journey.legs.lastOrNull()?.to?.name.orEmpty(), R.string.detail_destination),
        time = journey.endTime,
      )
    }
  }
}

/**
 * Le résumé du trajet : heures, durée, correspondances, frise.
 *
 * **Il s'affiche avant même que le détail soit arrivé** (SPEC.md § 5.3) : le trajet sommaire venu
 * de la liste de résultats porte déjà tout cela, et une page vide pendant une requête serait un
 * recul par rapport à l'écran d'où l'on vient.
 */
@Composable
private fun DetailSummary(journey: Journey, state: DetailUiState) {
  val formatTime = rememberTimeFormatter()
  val status = TimeStatus.of(journey)
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(CardPadding),
      verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
      val departure = formatTime(journey.startTime)
      val arrival = formatTime(journey.endTime)
      val description = stringResource(R.string.results_time_range_description, departure, arrival)
      Text(
        text = stringResource(R.string.results_time_range, departure, arrival),
        style = MaterialTheme.typography.titleLarge,
        // Comme sur la carte de résultat : des heures barrées quand une portion est supprimée,
        // doublées de la mention en toutes lettres de `StatusLines` (SPEC.md § 9).
        textDecoration = if (status.cancelled) TextDecoration.LineThrough else null,
        modifier = Modifier.semantics { contentDescription = description },
      )
      val transfers = transfersText(journey.transfers)
      Text(
        text = stringResource(R.string.results_summary, durationText(journey.duration), transfers),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      DetailTimeline(journey)
      Text(
        text = stringResource(R.string.detail_timeline_description, legendOf(journey)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      StatusLines(status)
      LoadingLine(state = state, formatTime = formatTime)
    }
  }
}

/** L'attente du détail, et la fraîcheur du temps réel une fois qu'il est là (SPEC.md § 5.3). */
@Composable
private fun LoadingLine(state: DetailUiState, formatTime: (Instant) -> String) {
  when {
    state.loading -> Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
      CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = ProgressStroke)
      Text(
        text = stringResource(R.string.detail_loading_details),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    state.refreshedAt != null -> DetailRow(
      icon = R.drawable.ic_schedule,
      text = stringResource(R.string.detail_refreshed_at, formatTime(state.refreshedAt)),
    )
  }
}

/**
 * La frise des portions, à l'échelle de leur durée (SPEC.md § 5.2), reprise ici pour que l'écran
 * de détail dise la même chose que la carte de résultat d'où l'on vient.
 *
 * Elle est muette pour les lecteurs d'écran : la même information est juste en dessous, en toutes
 * lettres, et la répéter ferait perdre du temps à qui écoute.
 */
@Composable
private fun DetailTimeline(journey: Journey) {
  val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TimelineHeight * scale)
      .clearAndSetSemantics { },
    horizontalArrangement = Arrangement.spacedBy(TimelineGap),
  ) {
    JourneyTimeline.of(journey).forEach { segment -> TimelineSegmentBox(segment) }
  }
}

@Composable
private fun RowScope.TimelineSegmentBox(segment: TimelineSegment) {
  val leg = segment.leg
  val background = when {
    leg.cancelled -> MaterialTheme.colorScheme.errorContainer
    leg is JourneyLeg.Transit -> routeColor(leg.routeColor) ?: MaterialTheme.colorScheme.primaryContainer
    leg is JourneyLeg.Rental -> routeColor(leg.rental?.color) ?: MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
  }
  val content = when {
    leg.cancelled -> MaterialTheme.colorScheme.onErrorContainer

    leg is JourneyLeg.Transit ->
      onRouteColor(leg.routeTextColor, leg.routeColor, MaterialTheme.colorScheme.onPrimaryContainer)

    leg is JourneyLeg.Rental ->
      onRouteColor(null, leg.rental?.color, MaterialTheme.colorScheme.onTertiaryContainer)

    else -> MaterialTheme.colorScheme.onSecondaryContainer
  }
  Box(
    modifier = Modifier
      .weight(segment.weight)
      .fillMaxHeight()
      .clip(RoundedCornerShape(SegmentCorner))
      .background(background),
    contentAlignment = Alignment.Center,
  ) {
    if (segment.weight >= MIN_WEIGHT_FOR_ICON) {
      Icon(
        painter = painterResource(if (leg.cancelled) R.drawable.ic_cancel else leg.modeIcon()),
        contentDescription = null,
        modifier = Modifier.size(SegmentIconSize),
        tint = content,
      )
    }
  }
}

/** Le résumé textuel de la frise, qui double les pictogrammes de mode (SPEC.md § 9). */
@Composable
private fun legendOf(journey: Journey): String {
  val separator = stringResource(R.string.results_leg_separator)
  return journey.legs.map { leg ->
    val mode = stringResource(leg.modeLabel())
    val detail = (leg as? JourneyLeg.Transit)?.let(::transitLineLabel) ?: durationText(leg.duration)
    stringResource(R.string.results_leg_detail, mode, detail)
  }.joinToString(separator)
}

/** Le point de départ ou d'arrivée du trajet, en tête et en pied de liste. */
@Composable
private fun EndpointRow(labelRes: Int, icon: Int, name: String, time: Instant) {
  val formatTime = rememberTimeFormatter()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // « Départ, Gare de Lyon, 8 h 12 » en une phrase plutôt qu'en trois arrêts de balayage : la
      // nature du point, son nom et son heure ne se comprennent qu'ensemble (SPEC.md § 9).
      .semantics(mergeDescendants = true) { }
      .padding(horizontal = CardPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = stringResource(labelRes),
      modifier = Modifier.size(RowIconSize),
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(text = name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Text(text = formatTime(time), style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
internal fun transfersText(transfers: Int): String = if (transfers <= 0) {
  stringResource(R.string.results_no_transfer)
} else {
  pluralStringResource(R.plurals.results_transfers, transfers, transfers)
}

/**
 * Le texte de partage (SPEC.md § 5.3), **composé de chaînes traduites** : le plan des lignes vient
 * de `:core`, chaque ligne est un libellé de `strings_detail.xml`, et rien n'est concaténé en dur.
 *
 * Le texte part par une intention externe, jamais par une WebView (SPEC.md § 2). Un appareil sans
 * application capable de le recevoir ne doit pas faire tomber l'écran.
 */
@Composable
private fun rememberShareAction(journey: Journey): () -> Unit {
  val context = LocalContext.current
  val title = stringResource(R.string.detail_action_share)
  val text = shareText(journey)
  return remember(context, title, text) {
    {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
      }
      runCatching { context.startActivity(Intent.createChooser(intent, title)) }
      Unit
    }
  }
}

@Composable
private fun shareText(journey: Journey): String {
  val formatTime = rememberTimeFormatter()
  return shareLinesOf(journey).map { shareLine(it, formatTime) }.joinToString(LINE_BREAK)
}

@Composable
private fun shareLine(line: JourneyShareLine, formatTime: (Instant) -> String): String = when (line) {
  is JourneyShareLine.Summary -> stringResource(
    R.string.detail_share_summary,
    formatTime(line.start),
    formatTime(line.end),
    durationText(line.duration),
    transfersText(line.transfers),
  )

  is JourneyShareLine.Endpoint -> stringResource(
    if (line.isOrigin) R.string.detail_share_origin else R.string.detail_share_destination,
    placeLabel(line.name, if (line.isOrigin) R.string.detail_origin else R.string.detail_destination),
    formatTime(line.time),
  )

  is JourneyShareLine.Transit -> stringResource(
    R.string.detail_share_transit,
    transitHeading(line),
    placeLabel(line.leg.from.name, R.string.detail_place_unnamed),
    formatTime(line.leg.startTime),
    placeLabel(line.leg.to.name, R.string.detail_place_unnamed),
    formatTime(line.leg.endTime),
  )

  is JourneyShareLine.Street -> stringResource(
    R.string.detail_share_street,
    stringResource(line.leg.modeLabel()),
    durationText(line.leg.duration),
    placeLabel(line.leg.to.name, R.string.detail_place_unnamed),
  )
}

/** « Métro 1 direction La Défense », composé de trois libellés et d'aucun texte codé en dur. */
@Composable
private fun transitHeading(line: JourneyShareLine.Transit): String {
  val mode = stringResource(line.leg.modeLabel())
  val label = line.label
  val named = if (label == null) mode else stringResource(R.string.results_leg_detail, mode, label)
  val headsign = line.leg.headsign?.takeIf(String::isNotBlank) ?: return named
  return stringResource(R.string.detail_share_transit_direction, named, headsign)
}

private const val SUMMARY_KEY = "resume"
private const val ALERTS_KEY = "perturbations"
private const val ERROR_KEY = "erreur"
private const val ORIGIN_KEY = "depart"
private const val DESTINATION_KEY = "arrivee"
private const val LINE_BREAK = "\n"

/** En deçà, le pictogramme d'un segment de frise n'est plus qu'une tache. */
private const val MIN_WEIGHT_FOR_ICON = 0.12f

/** SPEC.md § 9 demande la lisibilité jusqu'à 200 % : au-delà, la frise cesse de grandir. */
private const val MAX_FONT_SCALE = 2f

internal val ScreenPadding: Dp = 16.dp
internal val CardPadding: Dp = 16.dp
internal val CardSpacing: Dp = 8.dp
internal val ListSpacing: Dp = 8.dp
internal val RowSpacing: Dp = 8.dp
internal val RowIconSize: Dp = 20.dp
private val TimelineHeight: Dp = 24.dp
private val TimelineGap: Dp = 2.dp
private val SegmentCorner: Dp = 4.dp
private val SegmentIconSize: Dp = 16.dp
private val ProgressSize: Dp = 18.dp
private val ProgressStroke: Dp = 2.dp
