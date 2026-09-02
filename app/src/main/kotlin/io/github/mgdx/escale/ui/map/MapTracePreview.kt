package io.github.mgdx.escale.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.geo.TraceKind
import io.github.mgdx.escale.core.geo.TraceMarkerKind
import io.github.mgdx.escale.core.geo.TraceStroke
import io.github.mgdx.escale.ui.theme.EscaleTheme

/*
 * Aperçus du tracé, pour le relire sans appareil.
 *
 * Le tracé lui-même est dessiné par MapLibre, à partir de sources GeoJSON (SPEC.md § 5.7,
 * règle 6) : aucun aperçu Compose ne peut le montrer tel quel. Ce fichier montre donc ce qui se
 * vérifie à l'œil et qui décide de sa lisibilité — la palette de repli par mode, les trois formes
 * de trait, et les trois marqueurs — en clair et en sombre.
 *
 * **Le `Canvas` ci-dessous n'existe que dans ces aperçus.** Rien de tel n'est jamais posé sur la
 * carte : ce serait précisément la vue superposée que la règle 6 interdit.
 */

@Preview(name = "Tracé du trajet, thème clair", showBackground = true, widthDp = 320)
@Preview(
  name = "Tracé du trajet, thème sombre",
  showBackground = true,
  widthDp = 320,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun JourneyTracePreview() {
  EscaleTheme(dynamicColor = false) {
    Surface {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Les trois formes de trait : c'est par elles, et non par la couleur, que la marche se
        // distingue d'un trajet en véhicule (SPEC.md § 9).
        TraceStroke.entries.forEach { stroke ->
          StrokeSample(stroke = stroke, color = TracePalette.colorOf(TraceKind.WALK).asColor())
        }
        // La palette de repli, quand le réseau ne publie pas de `routeColor`.
        TraceKind.entries.forEach { kind ->
          StrokeSample(
            stroke = TraceStroke.SOLID,
            color = TracePalette.colorOf(kind).asColor(),
            label = kind.name,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          MarkerSample(TraceMarkerKind.ORIGIN, MaterialTheme.colorScheme.primary)
          MarkerSample(TraceMarkerKind.TRANSFER, MaterialTheme.colorScheme.onSurface)
          MarkerSample(TraceMarkerKind.DESTINATION, MaterialTheme.colorScheme.tertiary)
        }
      }
    }
  }
}

@Composable
private fun StrokeSample(stroke: TraceStroke, color: Color, label: String = stroke.name) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Canvas(modifier = Modifier.width(120.dp).height(12.dp)) {
      drawLine(
        color = color,
        start = Offset(x = 0f, y = size.height / 2),
        end = Offset(x = size.width, y = size.height / 2),
        strokeWidth = LINE_WIDTH_PX,
        cap = if (stroke == TraceStroke.DOTTED) StrokeCap.Round else StrokeCap.Butt,
        pathEffect = stroke.previewDash(),
      )
    }
    Text(text = label, style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
private fun MarkerSample(kind: TraceMarkerKind, tint: Color) {
  Icon(
    painter = painterResource(kind.previewIcon()),
    contentDescription = null,
    tint = tint,
    modifier = Modifier.size(28.dp),
  )
}

/** Les mêmes motifs que ceux passés à `line-dasharray`, en multiples de la largeur du trait. */
private fun TraceStroke.previewDash(): PathEffect? {
  val pattern = when (this) {
    TraceStroke.SOLID -> return null
    TraceStroke.DOTTED -> floatArrayOf(DOT_LENGTH, LINE_WIDTH_PX * DOT_GAP_RATIO)
    TraceStroke.APPROXIMATE -> floatArrayOf(LINE_WIDTH_PX * DASH_RATIO, LINE_WIDTH_PX * DASH_RATIO)
  }
  return PathEffect.dashPathEffect(pattern)
}

private fun TraceMarkerKind.previewIcon(): Int = when (this) {
  TraceMarkerKind.ORIGIN -> R.drawable.ic_trip_origin
  TraceMarkerKind.TRANSFER -> R.drawable.ic_transfer_within_a_station
  TraceMarkerKind.DESTINATION -> R.drawable.ic_place
}

private fun String.asColor(): Color = Color(checkNotNull(HexColor.parse(this)))

private const val LINE_WIDTH_PX = 12f

/** Un point est un trait de longueur nulle, arrondi par le `StrokeCap` : la largeur fait le rond. */
private const val DOT_LENGTH = 0.1f
private const val DOT_GAP_RATIO = 1.6f
private const val DASH_RATIO = 2f
