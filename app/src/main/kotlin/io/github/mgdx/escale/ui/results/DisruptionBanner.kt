package io.github.mgdx.escale.ui.results

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.Disruption
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.Disruptions
import java.time.Instant

/**
 * Le bandeau des perturbations **en vigueur** d'un trajet (SPEC.md § 5.2 et § 5.3).
 *
 * Il est partagé par la liste de résultats et l'écran de détail : les deux disent la même chose du
 * même trajet, et le dire à deux endroits différemment serait une occasion de divergence.
 *
 * Ce qu'il affiche, dans cet ordre : le nombre de perturbations en vigueur, **la gravité écrite en
 * toutes lettres** — SPEC.md § 9 interdit de la confier à la seule couleur — et le titre de la
 * plus grave. Les perturbations simplement annoncées pour plus tard ne sont pas mélangées aux
 * autres : elles ont leur propre ligne, sans alarme.
 *
 * @param alerts les perturbations du trajet ou de la portion, doublons compris : le tri revient à
 *   `Disruptions`, dans `:core`.
 * @param at l'instant auquel juger qu'une perturbation est en vigueur — l'heure du dernier
 *   chargement, et non « maintenant », pour que le bandeau ne change pas au fil des secondes.
 *   `null` quand elle est inconnue : tout est alors montré (voir `Disruptions.inEffect`).
 */
@Composable
internal fun DisruptionBanner(alerts: List<Disruption>, at: Instant?, modifier: Modifier = Modifier) {
  val inEffect = Disruptions.inEffect(alerts, at)
  val upcoming = Disruptions.upcoming(alerts, at)
  if (inEffect.isEmpty() && upcoming.isEmpty()) return
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = bannerColor(Disruptions.worstSeverity(inEffect)),
    contentColor = bannerContentColor(Disruptions.worstSeverity(inEffect)),
    shape = RoundedCornerShape(BannerCorner),
  ) {
    Column(
      modifier = Modifier.padding(BannerPadding),
      verticalArrangement = Arrangement.spacedBy(BannerSpacing),
    ) {
      if (inEffect.isNotEmpty()) InEffectLines(inEffect)
      if (upcoming.isNotEmpty()) {
        BannerLine(
          icon = R.drawable.ic_schedule,
          text = pluralStringResource(R.plurals.results_disruptions_upcoming, upcoming.size, upcoming.size),
        )
      }
    }
  }
}

@Composable
private fun InEffectLines(inEffect: List<Disruption>) {
  val severity = Disruptions.worstSeverity(inEffect)
  BannerLine(
    icon = R.drawable.ic_warning,
    text = pluralStringResource(R.plurals.results_disruptions_in_effect, inEffect.size, inEffect.size),
  )
  if (severity != null) {
    Text(
      text = stringResource(R.string.results_alert_severity, stringResource(severity.labelRes())),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
  // Le titre montré est celui de la perturbation la plus grave : c'est celle qui décide de
  // l'apparence du bandeau, en dire une autre serait déroutant.
  val worst = inEffect.firstOrNull { it.severity == severity } ?: inEffect.first()
  val header = worst.headerText.takeIf(String::isNotBlank) ?: return
  Text(text = header, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun BannerLine(icon: Int, text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(BannerSpacing),
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      modifier = Modifier.size(BannerIconSize),
    )
    Text(text = text, style = MaterialTheme.typography.labelLarge)
  }
}

/** La gravité d'une perturbation, dite en toutes lettres : jamais une couleur seule (SPEC.md § 9). */
@StringRes
internal fun DisruptionSeverity.labelRes(): Int = when (this) {
  DisruptionSeverity.INFO -> R.string.results_alert_severity_info
  DisruptionSeverity.WARNING -> R.string.results_alert_severity_warning
  DisruptionSeverity.SEVERE -> R.string.results_alert_severity_severe
  DisruptionSeverity.UNKNOWN_SEVERITY -> R.string.results_alert_severity_unknown
}

/**
 * Le fond du bandeau. Il **double** l'information, il ne la porte pas : la gravité est écrite
 * juste à côté, et une perturbation annoncée pour plus tard reste sur une surface neutre.
 */
@Composable
private fun bannerColor(severity: DisruptionSeverity?): Color = when (severity) {
  DisruptionSeverity.SEVERE -> MaterialTheme.colorScheme.errorContainer
  DisruptionSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
  else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun bannerContentColor(severity: DisruptionSeverity?): Color = when (severity) {
  DisruptionSeverity.SEVERE -> MaterialTheme.colorScheme.onErrorContainer
  DisruptionSeverity.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
  else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val BannerCorner: Dp = 12.dp
private val BannerPadding: Dp = 12.dp
private val BannerSpacing: Dp = 8.dp
private val BannerIconSize: Dp = 18.dp
