package io.github.mgdx.escale.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.geo.TraceKind
import io.github.mgdx.escale.core.geo.TraceMarkerKind
import io.github.mgdx.escale.core.geo.TraceStroke
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * Le tracé du trajet sur la carte : deux sources GeoJSON et les couches qui les lisent
 * (SPEC.md § 5.3 et § 5.7).
 *
 * **Règle 6, littéralement** : tout ce qui se voit ici est une couche MapLibre alimentée par une
 * source GeoJSON. Aucune vue Android, aucun `Canvas` Compose par-dessus la carte : le rendu
 * paraîtrait correct et la fluidité s'écroulerait dès qu'on déplace la carte.
 *
 * **Règle 8** : les couches sont posées une fois par feuille de style chargée, et jamais deux fois
 * — une nouvelle feuille remplace toutes les couches, il n'y a donc rien à empiler. Changer de
 * trajet ne touche pas aux couches : cela remplace le contenu des deux sources, en une opération
 * chacune (règle 7). Désélectionner un trajet y pose une collection vide, ce qui efface le tracé
 * sans rien démonter.
 */

/** Les couleurs du tracé qui viennent du thème, et non de la donnée du réseau. */
@Immutable
data class MapTraceColors(
  /** Liseré posé sous les tracés : c'est lui qui les détache du fond de carte. */
  val casing: Color,
  /** Texte des libellés, quand le réseau ne publie pas de couleur de ligne. */
  val label: Color,
  /** Halo des libellés : la couleur de la feuille, pour rester lisible sur n'importe quel fond. */
  val labelHalo: Color,
  val origin: Color,
  val transfer: Color,
  val destination: Color,
)

/**
 * Les couleurs de repli, par famille de mode, quand le réseau ne publie pas de `routeColor`.
 *
 * Ce sont des teintes moyennes, choisies pour tenir sur les deux feuilles de style, la claire comme
 * la sombre. Elles ne portent jamais seules l'information (SPEC.md § 9) : le trait de la marche est
 * pointillé, chaque ligne est écrite en toutes lettres le long de son tracé, et chaque marqueur
 * porte le nom de son lieu.
 */
object TracePalette {

  fun colorOf(kind: TraceKind): String = when (kind) {
    TraceKind.WALK -> WALK
    TraceKind.BIKE -> BIKE
    TraceKind.CAR -> CAR
    TraceKind.RENTAL -> RENTAL
    TraceKind.RAIL -> RAIL
    TraceKind.SUBWAY -> SUBWAY
    TraceKind.TRAM -> TRAM
    TraceKind.BUS -> BUS
    TraceKind.FERRY -> FERRY
    TraceKind.TRANSIT -> TRANSIT
  }

  private const val WALK = "#5F6C7B"
  private const val BIKE = "#2E7D32"
  private const val CAR = "#455A64"
  private const val RENTAL = "#00897B"
  private const val RAIL = "#3F51B5"
  private const val SUBWAY = "#7B1FA2"
  private const val TRAM = "#C62828"
  private const val BUS = "#1565C0"
  private const val FERRY = "#00838F"
  private const val TRANSIT = "#546E7A"
}

/**
 * Pose les sources et les couches du tracé sur [this].
 *
 * L'ordre compte : le liseré d'abord, puis les traits, puis les libellés de ligne, puis les
 * marqueurs. Les traits se glissent **sous** les libellés de rue du fond de carte quand celui-ci en
 * a, pour ne pas masquer les noms de rues ; les marqueurs, eux, restent au-dessus de tout.
 */
fun Style.installJourneyTraceLayers(colors: MapTraceColors, markerIcons: Map<TraceMarkerKind, Bitmap>) {
  addSource(GeoJsonSource(JOURNEY_LINES_SOURCE))
  addSource(GeoJsonSource(JOURNEY_MARKERS_SOURCE))
  markerIcons.forEach { (kind, bitmap) -> addImage(kind.imageId(), bitmap) }

  addLayerUnderLabels(
    LineLayer(JOURNEY_CASING_LAYER, JOURNEY_LINES_SOURCE).withProperties(
      PropertyFactory.lineColor(colors.casing.toArgb()),
      PropertyFactory.lineWidth(CASING_WIDTH),
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      PropertyFactory.lineOpacity(CASING_OPACITY),
    ),
  )
  // Un trait par forme : `line-dasharray` ne se pilote pas par une propriété de l'entité, il faut
  // donc une couche par motif, chacune filtrée sur la forme que la portion a demandée.
  TraceStroke.entries.forEach { stroke -> addLayerUnderLabels(strokeLayer(stroke)) }
  addLayer(lineLabelLayer(colors))
  TraceMarkerKind.entries.forEach { kind -> addLayer(markerLayer(kind, colors)) }
}

/** Le trait d'une forme de portion : plein pour un véhicule, pointillé pour la marche. */
private fun strokeLayer(stroke: TraceStroke): LineLayer = LineLayer(stroke.layerId(), JOURNEY_LINES_SOURCE).apply {
  setFilter(Expression.eq(Expression.get(MapGeoJson.PROPERTY_STROKE), Expression.literal(stroke.name)))
  withProperties(
    PropertyFactory.lineColor(Expression.get(MapGeoJson.PROPERTY_COLOR)),
    PropertyFactory.lineWidth(LINE_WIDTH),
    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    PropertyFactory.lineCap(
      if (stroke == TraceStroke.APPROXIMATE) Property.LINE_CAP_BUTT else Property.LINE_CAP_ROUND,
    ),
  )
  stroke.dashArray()?.let { setProperties(PropertyFactory.lineDasharray(it)) }
}

/**
 * Le nom de la ligne, écrit le long de son tracé.
 *
 * C'est ce qui rend la carte compréhensible sans distinguer les couleurs (SPEC.md § 9) : la teinte
 * dit « c'est la même ligne », le texte dit « c'est la ligne 4 ». Le libellé reprend les couleurs
 * du réseau quand il les publie — `routeTextColor` sur un halo de `routeColor` — et celles du thème
 * sinon.
 */
private fun lineLabelLayer(colors: MapTraceColors): SymbolLayer =
  SymbolLayer(JOURNEY_LINE_LABEL_LAYER, JOURNEY_LINES_SOURCE).withProperties(
    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
    PropertyFactory.symbolSpacing(LABEL_SPACING),
    PropertyFactory.textField(Expression.get(MapGeoJson.PROPERTY_LABEL)),
    PropertyFactory.textFont(arrayOf(LABEL_FONT)),
    PropertyFactory.textSize(LABEL_SIZE),
    PropertyFactory.textPadding(LABEL_PADDING),
    PropertyFactory.textColor(
      Expression.coalesce(
        Expression.get(MapGeoJson.PROPERTY_TEXT_COLOR),
        Expression.color(colors.label.toArgb()),
      ),
    ),
    PropertyFactory.textHaloColor(Expression.get(MapGeoJson.PROPERTY_COLOR)),
    PropertyFactory.textHaloWidth(LABEL_HALO_WIDTH),
  )

/**
 * Un marqueur, et le nom du lieu qu'il désigne.
 *
 * Une couche par nature de marqueur : chacune a son dessin, son ancrage et la place de son
 * libellé. Trois **formes** distinctes, et non trois teintes : le départ est un anneau, la
 * correspondance une flèche de changement, l'arrivée une épingle (SPEC.md § 9).
 */
private fun markerLayer(kind: TraceMarkerKind, colors: MapTraceColors): SymbolLayer =
  SymbolLayer(kind.layerId(), JOURNEY_MARKERS_SOURCE).apply {
    setFilter(Expression.eq(Expression.get(MapGeoJson.PROPERTY_KIND), Expression.literal(kind.name)))
    withProperties(
      PropertyFactory.iconImage(kind.imageId()),
      PropertyFactory.iconAnchor(kind.iconAnchor()),
      // Un marqueur de trajet ne se laisse pas effacer par un libellé du fond de carte.
      PropertyFactory.iconAllowOverlap(true),
      PropertyFactory.iconIgnorePlacement(true),
      PropertyFactory.textField(Expression.get(MapGeoJson.PROPERTY_LABEL)),
      PropertyFactory.textFont(arrayOf(LABEL_FONT)),
      PropertyFactory.textSize(LABEL_SIZE),
      PropertyFactory.textAnchor(kind.textAnchor()),
      PropertyFactory.textOffset(kind.textOffset()),
      PropertyFactory.textMaxWidth(LABEL_MAX_WIDTH),
      // Le libellé cède la place s'il ne tient pas ; le marqueur, lui, reste.
      PropertyFactory.textOptional(true),
      PropertyFactory.textColor(colors.label.toArgb()),
      PropertyFactory.textHaloColor(colors.labelHalo.toArgb()),
      PropertyFactory.textHaloWidth(LABEL_HALO_WIDTH),
    )
  }

/**
 * Ajoute une couche sous les libellés de rue du fond de carte, ou au-dessus de tout à défaut.
 *
 * La feuille neutre d'un serveur sans tuiles n'a qu'un fond : il n'y a alors rien sous quoi se
 * glisser, et le tracé se pose simplement par-dessus (SPEC.md § 5.7).
 */
internal fun Style.addLayerUnderLabels(layer: Layer) {
  if (getLayer(STREET_LABELS_LAYER) != null) addLayerBelow(layer, STREET_LABELS_LAYER) else addLayer(layer)
}

/**
 * Les dessins des marqueurs, prêts à poser sur la feuille de style.
 *
 * Les icônes sont celles du projet, reprises telles quelles : le jeu Material Symbols en compte
 * déjà trois qui disent exactement cela, et docs/architecture.md § 11.2 demande de réutiliser une
 * icône présente plutôt que d'en redessiner une.
 */
@Composable
fun rememberTraceMarkerIcons(colors: MapTraceColors): Map<TraceMarkerKind, Bitmap> {
  val context = LocalContext.current
  val size = with(LocalDensity.current) { MarkerIconSize.roundToPx() }
  return remember(context, colors, size) {
    TraceMarkerKind.entries.associateWith { kind ->
      context.tintedBitmap(kind.iconRes(), kind.tint(colors), size)
    }
  }
}

private fun Context.tintedBitmap(@DrawableRes icon: Int, tint: Color, size: Int): Bitmap {
  val drawable = checkNotNull(ResourcesCompat.getDrawable(resources, icon, theme)).mutate()
  drawable.setTint(tint.toArgb())
  return drawable.toBitmap(width = size, height = size)
}

@DrawableRes
private fun TraceMarkerKind.iconRes(): Int = when (this) {
  TraceMarkerKind.ORIGIN -> R.drawable.ic_trip_origin
  TraceMarkerKind.TRANSFER -> R.drawable.ic_transfer_within_a_station
  TraceMarkerKind.DESTINATION -> R.drawable.ic_place
}

private fun TraceMarkerKind.tint(colors: MapTraceColors): Color = when (this) {
  TraceMarkerKind.ORIGIN -> colors.origin
  TraceMarkerKind.TRANSFER -> colors.transfer
  TraceMarkerKind.DESTINATION -> colors.destination
}

/** L'épingle d'arrivée pointe le lieu par sa pointe ; les deux autres dessins sont centrés. */
private fun TraceMarkerKind.iconAnchor(): String = when (this) {
  TraceMarkerKind.DESTINATION -> Property.ICON_ANCHOR_BOTTOM
  else -> Property.ICON_ANCHOR_CENTER
}

private fun TraceMarkerKind.textAnchor(): String = when (this) {
  TraceMarkerKind.DESTINATION -> Property.TEXT_ANCHOR_TOP
  else -> Property.TEXT_ANCHOR_LEFT
}

private fun TraceMarkerKind.textOffset(): Array<Float> = when (this) {
  TraceMarkerKind.DESTINATION -> arrayOf(0f, LABEL_OFFSET)
  else -> arrayOf(LABEL_OFFSET, 0f)
}

private fun TraceMarkerKind.imageId(): String = "$JOURNEY_PREFIX-marker-${name.lowercase()}"

private fun TraceMarkerKind.layerId(): String = "$JOURNEY_PREFIX-marker-layer-${name.lowercase()}"

private fun TraceStroke.layerId(): String = "$JOURNEY_PREFIX-line-${name.lowercase()}"

/**
 * Le motif du trait, ou `null` pour un trait plein.
 *
 * Le pointillé serré de la marche est la convention cartographique usuelle ; le tiret long dit que
 * le tracé est une reconstitution en ligne droite, faute de géométrie renvoyée par le serveur.
 * Les longueurs sont en multiples de la largeur du trait.
 */
private fun TraceStroke.dashArray(): Array<Float>? = when (this) {
  TraceStroke.SOLID -> null
  TraceStroke.DOTTED -> arrayOf(0f, DOT_GAP)
  TraceStroke.APPROXIMATE -> arrayOf(DASH_LENGTH, DASH_GAP)
}

/** Le préfixe de tous les identifiants du tracé : il les rend reconnaissables d'un coup d'œil. */
private const val JOURNEY_PREFIX = "escale-journey"

/** La source des portions : une `LineString` par portion. */
const val JOURNEY_LINES_SOURCE = "$JOURNEY_PREFIX-lines"

/** La source des marqueurs : départ, correspondances, arrivée. */
const val JOURNEY_MARKERS_SOURCE = "$JOURNEY_PREFIX-markers"

private const val JOURNEY_CASING_LAYER = "$JOURNEY_PREFIX-casing"
private const val JOURNEY_LINE_LABEL_LAYER = "$JOURNEY_PREFIX-line-label"

/** La couche de libellés de rue des feuilles de `res/raw`, sous laquelle les traits se glissent. */
internal const val STREET_LABELS_LAYER = "street-labels"

/** La seule fonte que le serveur MOTIS sert en glyphes, et celle qu'emploient les feuilles. */
private const val LABEL_FONT = "Noto Sans Bold"

private const val LINE_WIDTH = 5f
private const val CASING_WIDTH = 9f
private const val CASING_OPACITY = 0.9f
private const val DOT_GAP = 1.6f
private const val DASH_LENGTH = 2f
private const val DASH_GAP = 2f
private const val LABEL_SIZE = 12f
private const val LABEL_SPACING = 220f
private const val LABEL_PADDING = 4f
private const val LABEL_HALO_WIDTH = 1.6f
private const val LABEL_MAX_WIDTH = 8f
private const val LABEL_OFFSET = 1f

/** Un marqueur de carte : plus grand qu'une icône d'interface, il doit se voir à bout de bras. */
private val MarkerIconSize: Dp = 28.dp
