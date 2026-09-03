package io.github.mgdx.escale.ui.trip

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.format.Delay
import io.github.mgdx.escale.core.format.DelayQuality
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.Disruptions
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.StopVisit
import io.github.mgdx.escale.ui.common.ErrorMessage
import io.github.mgdx.escale.ui.detail.iconRes
import io.github.mgdx.escale.ui.detail.labelRes
import io.github.mgdx.escale.ui.map.StopIcon
import io.github.mgdx.escale.ui.results.DisruptionBanner
import io.github.mgdx.escale.ui.results.ForegroundEffect
import io.github.mgdx.escale.ui.results.delayColor
import io.github.mgdx.escale.ui.results.durationText
import io.github.mgdx.escale.ui.results.onRouteColor
import io.github.mgdx.escale.ui.results.rememberTimeFormatter
import io.github.mgdx.escale.ui.results.routeColor
import java.time.Instant

/**
 * Le détail d'une course (SPEC.md § 5.3) : « la desserte complète », c'est-à-dire tous les arrêts
 * avec leurs heures théoriques et réelles, les quais et les perturbations.
 *
 * C'est l'écran qu'on ouvre pour savoir « ce train s'arrête où ». Il se rejoint depuis un départ
 * (§ 5.4) et depuis l'écran de détail d'un trajet (§ 5.3) ; il ne connaît ni l'un ni l'autre.
 */
@Composable
fun TripScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val container = appContainer()
  val viewModel: TripViewModel = viewModel(factory = TripViewModel.factory(container))
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // SPEC.md § 7.4 : au retour au premier plan, et seulement si les horaires ont plus de 60
  // secondes. Le bouton « Rafraîchir » de la barre, lui, rafraîchit sans condition.
  ForegroundEffect(viewModel::onForeground)
  TripContent(state = state, onBack = onBack, onRefresh = viewModel::onRefresh, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripContent(
  state: TripUiState,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { TripTitle(state) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        actions = {
          IconButton(onClick = onRefresh) {
            Icon(
              painter = painterResource(R.drawable.ic_refresh),
              contentDescription = stringResource(R.string.trip_action_refresh),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      // Le rafraîchissement laisse la desserte en place : SPEC.md § 8 interdit de remplacer une
      // information déjà lue par une page blanche.
      if (state.refreshing) {
        val loading = stringResource(R.string.action_loading)
        LinearProgressIndicator(
          // Sans nom, la barre n'est qu'une animation : le lecteur d'écran passe devant sans rien
          // dire, et la liste change sous le doigt (SPEC.md § 9).
          modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading },
        )
      }
      state.error?.let { ErrorMessage(error = it, onRetry = onRefresh) }
      TripBody(state)
    }
  }
}

/** La ligne, puis sa direction en sous-titre : ce que l'usager lit sur le véhicule. */
@Composable
private fun TripTitle(state: TripUiState) {
  Column {
    Text(
      text = state.lineName.ifBlank { stringResource(R.string.trip_title) },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleLarge,
    )
    if (state.headsign.isNotBlank()) {
      Text(
        text = stringResource(R.string.trip_towards, state.headsign),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TripBody(state: TripUiState) {
  when {
    state.loading -> Loading()
    state.journey != null && state.calls.isEmpty() -> EmptyState()
    state.journey == null -> Unit
    else -> CallList(state)
  }
}

@Composable
private fun Loading() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(ScreenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Text(
      text = stringResource(R.string.trip_loading),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = ScreenPadding),
    )
  }
}

/**
 * L'état vide (SPEC.md § 8).
 *
 * Il ne se déclenche que si le serveur a répondu **et** n'a annoncé aucun arrêt : une desserte vide
 * avant la réponse n'est pas « aucun arrêt », c'est « on ne sait pas encore », et l'affirmer serait
 * faux.
 */
@Composable
private fun EmptyState() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(ScreenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_directions_transit),
      contentDescription = null,
      modifier = Modifier.size(EmptyIconSize),
    )
    Text(
      text = stringResource(R.string.trip_empty),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = ScreenPadding),
    )
    Text(
      text = stringResource(R.string.trip_empty_hint),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun CallList(state: TripUiState) {
  val calls = state.calls
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScreenPadding)) {
    item(key = HEADER_KEY) { TripHeader(state) }
    if (state.alerts.isNotEmpty()) {
      // Le bandeau des perturbations en vigueur, le même qu'en tête de carte de résultat
      // (SPEC.md § 5.2) : les dire autrement ici serait une occasion de divergence.
      item(key = ALERTS_KEY) {
        DisruptionBanner(
          alerts = state.alerts,
          at = state.loadedAt,
          modifier = Modifier.padding(horizontal = ScreenPadding, vertical = RowSpacing),
        )
      }
    }
    item(key = COUNT_KEY) {
      Text(
        text = pluralStringResource(R.plurals.trip_calls_count, calls.size, calls.size),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = RowSpacing),
      )
    }
    items(items = calls, key = { it.key() }) { call ->
      CallRow(
        call = call,
        realTime = state.leg?.realTime == true,
        alerts = state.alertsAt(call),
        at = state.loadedAt,
      )
      HorizontalDivider()
    }
  }
}

/** La ligne, sa direction, son transporteur, et ce que le véhicule accepte (SPEC.md § 5.3). */
@Composable
private fun TripHeader(state: TripUiState) {
  val leg = state.leg ?: return
  val icon = StopIcon.of(leg.mode)
  val modeLabel = stringResource(icon.label)
  Column(
    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = RowSpacing),
    verticalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
      Icon(
        painter = painterResource(icon.drawable),
        // Le pictogramme est muet : la pastille voisine porte déjà « Métro, ligne 4 » (§ 9).
        contentDescription = null,
        modifier = Modifier.size(ModeIconSize),
      )
      LineBadge(leg = leg, modeLabel = modeLabel)
    }
    leg.agencyName?.takeIf(String::isNotBlank)?.let {
      TripLine(icon = R.drawable.ic_business, text = stringResource(R.string.trip_agency, it))
    }
    leg.wheelchairAccessible?.let {
      TripLine(icon = it.iconRes(), text = stringResource(it.labelRes()))
    }
    leg.bikesAllowed?.let {
      TripLine(
        icon = R.drawable.ic_pedal_bike,
        text = stringResource(
          if (it) R.string.detail_bikes_allowed else R.string.detail_bikes_not_allowed,
        ),
      )
    }
  }
}

@Composable
private fun LineBadge(leg: JourneyLeg.Transit, modeLabel: String) {
  val label = leg.lineName.ifBlank { modeLabel }
  val description = stringResource(R.string.trip_line_description, modeLabel, label)
  val background = routeColor(leg.routeColor) ?: MaterialTheme.colorScheme.secondaryContainer
  val content = onRouteColor(
    textColor = leg.routeTextColor,
    background = leg.routeColor,
    fallback = MaterialTheme.colorScheme.onSecondaryContainer,
  )
  Text(
    text = label,
    style = MaterialTheme.typography.titleMedium,
    color = content,
    modifier = Modifier
      .clip(RoundedCornerShape(BadgeCorner))
      .background(background)
      .padding(horizontal = BadgePadding, vertical = BadgeVerticalPadding)
      .semantics { contentDescription = description },
  )
}

/**
 * Un arrêt de la desserte : son nom, ses heures, son quai (SPEC.md § 5.3).
 *
 * @param realTime vrai si la course est suivie en temps réel. **Sans lui, aucun écart n'est
 *   annoncé** : les deux heures sont alors égales par construction, et « à l'heure » serait un
 *   mensonge (SPEC.md § 5.2). C'est `Delay.between` qui applique la règle, dans `:core`.
 * @param alerts les perturbations **propres à cet arrêt**, déjà débarrassées de celles que le
 *   bandeau de la course annonce (`TripUiState.alertsAt`).
 * @param at l'instant auquel juger qu'une perturbation est en vigueur : l'heure du dernier
 *   chargement, comme pour le bandeau de tête, pour que l'affichage ne change pas au fil des
 *   secondes.
 */
@Composable
private fun CallRow(call: StopVisit, realTime: Boolean, alerts: List<Disruption>, at: Instant?) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Un arrêt s'annonce **d'un bloc** : son nom, ses heures, son quai et son état forment une
      // seule phrase. Sans cette fusion, une desserte de trente arrêts demande deux cents
      // balayages pour être parcourue (SPEC.md § 9). La ligne n'étant pas cliquable, aucun
      // `clickable` ne fusionne à notre place.
      .semantics(mergeDescendants = true) { }
      // Cible tactile d'au moins 48 dp, même sans appui : la liste reste lisible à 200 % (§ 9).
      .defaultMinSize(minHeight = RowMinHeight)
      .padding(horizontal = ScreenPadding, vertical = RowSpacing),
    verticalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Text(
      text = call.place.name.takeIf(String::isNotBlank) ?: stringResource(R.string.trip_call_unnamed),
      style = MaterialTheme.typography.titleSmall,
      // « Arrêt supprimé de la desserte » : le barré double le texte de la ligne d'état, il ne le
      // remplace pas (SPEC.md § 9).
      textDecoration = if (call.cancelled) TextDecoration.LineThrough else null,
    )
    CallTimes(call = call, realTime = realTime)
    call.place.track?.takeIf(String::isNotBlank)?.let {
      TripLine(icon = R.drawable.ic_signpost, text = stringResource(R.string.trip_track, it))
    }
    if (call.cancelled) {
      TripLine(
        icon = R.drawable.ic_cancel,
        text = stringResource(R.string.trip_call_cancelled),
        color = MaterialTheme.colorScheme.error,
      )
    }
    CallAlerts(alerts = alerts, at = at)
  }
}

/**
 * Les perturbations qui ne concernent que cet arrêt (SPEC.md § 5.3).
 *
 * Le bandeau réemployé est **celui de la course et des résultats**, sans variante : il dit le
 * nombre de perturbations en vigueur, la gravité **en toutes lettres** — SPEC.md § 9 interdit de la
 * confier à la seule couleur — et le titre de la plus grave. Un second vocabulaire visuel pour dire
 * la même chose serait une occasion de divergence.
 *
 * Le libellé qui le précède existe pour lever le seul doute que le bandeau laisse : lu à la suite
 * de la desserte, à l'œil comme à la synthèse vocale, il faut savoir que ce message-ci parle de cet
 * arrêt-ci et non de toute la course.
 *
 * Les descriptions sont celles des perturbations **en vigueur** seulement. Le texte est déjà du
 * texte simple : la réduction du HTML est faite à l'entrée, dans le mapping (`core.text.HtmlText`).
 */
@Composable
private fun CallAlerts(alerts: List<Disruption>, at: Instant?) {
  if (alerts.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
    Text(
      text = stringResource(R.string.trip_call_alerts),
      style = MaterialTheme.typography.labelLarge,
    )
    DisruptionBanner(alerts = alerts, at = at)
    Disruptions.inEffect(alerts, at).forEach { alert ->
      if (alert.descriptionText.isNotBlank()) {
        Text(text = alert.descriptionText, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

/**
 * Les heures d'un arrêt, puis l'écart à l'horaire s'il y en a un (SPEC.md § 5.3).
 *
 * Une borne nulle est **normale** aux deux terminus — on n'arrive pas à l'arrêt d'où l'on part —
 * et se traduit par une ligne en moins, jamais par un tiret ou un zéro.
 *
 * **L'écart n'est calculé qu'une fois, et sur la bonne heure.** Le schéma `Place` porte deux
 * couples d'heures, mais le modèle de domaine n'en retient qu'un : l'heure pertinente de l'arrêt et
 * son horaire théorique (`Place.time` et `Place.scheduledTime`, docs/architecture.md § 4). C'est le
 * départ partout sauf au terminus, où c'est l'arrivée. Comparer l'arrivée à l'horaire de départ
 * afficherait le temps de stationnement en gare comme un retard : deux minutes d'arrêt à
 * Ludwigslust deviendraient « 2 min de retard », sur une course parfaitement à l'heure.
 */
@Composable
private fun CallTimes(call: StopVisit, realTime: Boolean) {
  val formatTime = rememberTimeFormatter()
  val delay = Delay.between(call.place.time, call.place.scheduledTime, realTime)
  val color = delay?.let { delayColor(it.quality) } ?: MaterialTheme.colorScheme.onSurfaceVariant
  call.arrival?.let {
    TripLine(
      icon = R.drawable.ic_schedule,
      text = stringResource(R.string.trip_arrival, formatTime(it)),
      color = color,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
  call.departure?.let {
    TripLine(
      icon = R.drawable.ic_arrow_forward,
      text = stringResource(R.string.trip_departure, formatTime(it)),
      color = color,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
  DelayLines(delay = delay, scheduled = call.place.scheduledTime)
}

/**
 * L'écart à l'horaire, écrit en toutes lettres, et l'heure prévue à côté.
 *
 * Rien n'est affiché sans donnée temps réel : `Delay.between` rend alors `null`, et l'horaire
 * théorique reste tu puisqu'il est égal, par construction, à l'heure déjà affichée (SPEC.md § 5.2).
 *
 * **« À l'heure » s'écrit, lui aussi.** Les heures juste au-dessus sont teintes en vert par
 * `delayColor` : sans cette ligne, le seul signe qu'un passage est à l'heure serait une couleur, ce
 * que SPEC.md § 9 interdit. Les deux autres écrans qui affichent un écart l'écrivent déjà.
 */
@Composable
private fun DelayLines(delay: Delay?, scheduled: Instant) {
  if (delay == null) return
  if (delay.quality == DelayQuality.ON_TIME) {
    TripLine(
      icon = R.drawable.ic_check_circle,
      text = stringResource(R.string.results_delay_on_time),
      color = delayColor(delay.quality),
      style = MaterialTheme.typography.bodySmall,
    )
    return
  }
  val formatTime = rememberTimeFormatter()
  val amount = durationText(delay.formatted)
  TripLine(
    icon = null,
    text = stringResource(
      if (delay.quality == DelayQuality.EARLY) R.string.results_delay_early else R.string.results_delay_late,
      amount,
    ),
    color = delayColor(delay.quality),
    style = MaterialTheme.typography.bodySmall,
  )
  TripLine(
    icon = null,
    text = stringResource(R.string.trip_scheduled_time, formatTime(scheduled)),
    style = MaterialTheme.typography.bodySmall,
  )
}

/** Une ligne d'information : un pictogramme, un texte, et jamais une couleur seule (SPEC.md § 9). */
@Composable
private fun TripLine(
  @DrawableRes icon: Int?,
  text: String,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (icon != null) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(LineIconSize),
      )
    }
    Text(text = text, style = style, color = color)
  }
}

/**
 * Une clé de liste stable pour un arrêt de la desserte.
 *
 * L'identifiant du serveur désigne l'arrêt, **jamais le passage** : une ligne circulaire, une
 * navette qui fait demi-tour ou un train qui rebrousse repasse au même arrêt dans la même course,
 * et l'identifiant seul donnait alors deux clés égales — de quoi faire lever la liste paresseuse
 * plutôt que d'afficher la desserte. L'horaire théorique du passage les départage : il est toujours
 * renseigné, il ne bouge pas d'un rafraîchissement à l'autre — contrairement à l'heure réelle, qui
 * ferait perdre la position de défilement à chaque retard — et une course ne dessert pas deux fois
 * le même arrêt à la même seconde.
 */
internal fun StopVisit.key(): String = (place.stopId ?: place.name) + "|" + place.scheduledTime

private const val HEADER_KEY = "trip.header"
private const val ALERTS_KEY = "trip.alerts"
private const val COUNT_KEY = "trip.count"

private val ScreenPadding: Dp = 16.dp
private val RowSpacing: Dp = 8.dp
private val RowMinHeight: Dp = 48.dp
private val ModeIconSize: Dp = 24.dp
private val LineIconSize: Dp = 18.dp
private val EmptyIconSize: Dp = 48.dp
private val BadgeCorner: Dp = 8.dp
private val BadgePadding: Dp = 12.dp
private val BadgeVerticalPadding: Dp = 6.dp
