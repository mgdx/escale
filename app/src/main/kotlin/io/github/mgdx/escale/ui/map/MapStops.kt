package io.github.mgdx.escale.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.core.graphics.createBitmap
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.geo.ZoomTier
import io.github.mgdx.escale.core.model.TransitMode
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * Les arrêts sur la carte : deux sources GeoJSON et les couches qui les lisent (SPEC.md § 5.7).
 *
 * **Règle 6, littéralement** : « les marqueurs sont dessinés comme couches MapLibre alimentées par
 * une source GeoJSON, avec regroupement au-delà de 200 points visibles — jamais comme des vues
 * Android superposées à la carte ». D'où **deux** sources posées une fois pour toutes : l'une
 * ordinaire, l'autre regroupante. Une seule porte des entités à la fois, l'autre reçoit une
 * collection vide. C'est ce qui permet d'allumer et d'éteindre le regroupement sans jamais ajouter
 * ni retirer une couche (règle 8), ce qu'une bascule d'option de source imposerait.
 *
 * **Règle 5** : « franchir un seuil de zoom vers le bas ne déclenche aucune requête : on masque des
 * couches déjà chargées ». Chaque source porte une couche par palier, filtrée sur la propriété
 * `tier` de l'entité et bornée par le `minzoom` **de la couche**. Repasser sous le zoom 13 éteint
 * la couche des arrêts de surface, sans un octet de réseau. Et comme les paliers s'empilent — la
 * couche du zoom 11 reste allumée au zoom 15 —, « on ajoute, on ne remplace pas » est tenu par la
 * construction même des couches.
 */

/** Les couleurs des arrêts, prises au thème Material et jamais codées en dur. */
@Immutable
data class MapStopColors(
  /** Pastille sous le pictogramme : c'est elle qui détache le marqueur du fond de carte. */
  val plate: Color,
  val onPlate: Color,
  val plateStroke: Color,
  val label: Color,
  val labelHalo: Color,
  val cluster: Color,
  val onCluster: Color,
)

/**
 * Les dessins de marqueur d'arrêt, un par famille de mode.
 *
 * Une **forme** par famille, jamais une teinte seule : SPEC.md § 9 interdit qu'une information
 * tienne à la couleur, et le nom de l'arrêt reste écrit à côté du pictogramme. Les icônes sont
 * celles que le projet possède déjà (docs/architecture.md § 11.2) ; aucune n'est redessinée.
 */
enum class StopIcon(@get:DrawableRes val drawable: Int, @get:StringRes val label: Int) {
  RAIL(R.drawable.ic_train, R.string.map_stop_mode_rail),
  REGIONAL_RAIL(R.drawable.ic_directions_railway, R.string.map_stop_mode_regional_rail),
  SUBWAY(R.drawable.ic_subway, R.string.map_stop_mode_subway),
  TRAM(R.drawable.ic_tram, R.string.map_stop_mode_tram),
  BUS(R.drawable.ic_directions_bus, R.string.map_stop_mode_bus),
  COACH(R.drawable.ic_airport_shuttle, R.string.map_stop_mode_coach),
  FERRY(R.drawable.ic_directions_boat, R.string.map_stop_mode_ferry),
  AERIAL_LIFT(R.drawable.ic_cable_car, R.string.map_stop_mode_aerial_lift),
  FUNICULAR(R.drawable.ic_funicular, R.string.map_stop_mode_funicular),
  AIRPLANE(R.drawable.ic_flight, R.string.map_stop_mode_airplane),
  TRANSIT(R.drawable.ic_directions_transit, R.string.map_stop_mode_transit),
  ;

  /** L'identifiant sous lequel le dessin est posé sur la feuille de style. */
  val imageId: String get() = "$STOPS_PREFIX-icon-${name.lowercase()}"

  companion object {
    /** Le dessin d'un mode. Tout ce que l'application ne sait pas nommer devient [TRANSIT]. */
    fun of(mode: TransitMode): StopIcon = when (mode) {
      TransitMode.HIGHSPEED_RAIL, TransitMode.LONG_DISTANCE, TransitMode.NIGHT_RAIL, TransitMode.RAIL -> RAIL
      TransitMode.REGIONAL_RAIL, TransitMode.SUBURBAN -> REGIONAL_RAIL
      TransitMode.SUBWAY -> SUBWAY
      TransitMode.TRAM -> TRAM
      TransitMode.BUS -> BUS
      TransitMode.COACH -> COACH
      TransitMode.FERRY -> FERRY
      TransitMode.AERIAL_LIFT -> AERIAL_LIFT
      TransitMode.FUNICULAR -> FUNICULAR
      TransitMode.AIRPLANE -> AIRPLANE
      else -> TRANSIT
    }
  }
}

/** Les deux seuls paliers qui portent des arrêts (tableau de SPEC.md § 5.7). */
private val STOP_TIERS = listOf(ZoomTier.MAJOR_STATIONS, ZoomTier.ALL_STOPS)

/**
 * Pose les deux sources d'arrêts et leurs couches sur [this].
 *
 * Appelée une fois par feuille de style chargée, comme le tracé de trajet : une nouvelle feuille
 * remplace toutes les couches, il n'y a donc jamais rien à empiler ni à démonter (règle 8). Les
 * arrêts se glissent **sous** les libellés de rue du fond de carte quand celui-ci en a, et restent
 * sous le tracé du trajet, qui est posé avant eux.
 */
fun Style.installStopLayers(colors: MapStopColors, icons: Map<StopIcon, Bitmap>) {
  addSource(GeoJsonSource(STOPS_SOURCE))
  addSource(
    GeoJsonSource(
      STOPS_CLUSTERED_SOURCE,
      GeoJsonOptions()
        .withCluster(true)
        .withClusterRadius(CLUSTER_RADIUS)
        .withClusterMaxZoom(CLUSTER_MAX_ZOOM),
    ),
  )
  icons.forEach { (icon, bitmap) -> addImage(icon.imageId, bitmap) }

  // La source regroupante d'abord : ses pastilles chiffrées passent derrière les arrêts détaillés.
  addLayer(clusterCircleLayer(colors))
  addLayer(clusterCountLayer(colors))
  for (source in listOf(STOPS_SOURCE, STOPS_CLUSTERED_SOURCE)) {
    for (tier in STOP_TIERS) addLayer(stopLayer(source, tier, colors))
  }
}

/**
 * Les identifiants des couches auxquelles un appui peut répondre (SPEC.md § 5.7, interaction).
 *
 * L'ordre compte : une pastille de regroupement est interrogée en dernier, pour qu'un arrêt
 * dessiné par-dessus elle gagne l'appui.
 */
val STOP_TAPPABLE_LAYERS: Array<String> = buildList {
  for (source in listOf(STOPS_SOURCE, STOPS_CLUSTERED_SOURCE)) {
    for (tier in STOP_TIERS) add(stopLayerId(source, tier))
  }
  add(CLUSTER_CIRCLE_LAYER)
}.toTypedArray()

/** Vrai si l'entité touchée est une pastille de regroupement et non un arrêt. */
const val CLUSTER_COUNT_PROPERTY = "point_count"

/**
 * Une couche d'arrêts, pour une source et un palier.
 *
 * Le `minzoom` de la couche est celui du palier : c'est lui, et rien d'autre, qui fait apparaître
 * les gares au zoom 11 et le reste au zoom 13, et qui les fait disparaître en dézoomant sans la
 * moindre requête (règle 5).
 */
private fun stopLayer(source: String, tier: ZoomTier, colors: MapStopColors): SymbolLayer =
  SymbolLayer(stopLayerId(source, tier), source).apply {
    setMinZoom(tier.minZoom.toFloat())
    setFilter(
      Expression.all(
        Expression.not(Expression.has(CLUSTER_COUNT_PROPERTY)),
        Expression.eq(Expression.get(MapGeoJson.PROPERTY_TIER), Expression.literal(tier.name)),
      ),
    )
    withProperties(
      PropertyFactory.iconImage(Expression.get(MapGeoJson.PROPERTY_ICON)),
      PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
      // Volontairement laissés à `false` : c'est MapLibre qui écarte les marqueurs qui se
      // chevauchent, et c'est ce qui garde la carte lisible et fluide à plusieurs centaines de
      // points (SPEC.md § 5.7, objectifs mesurables).
      PropertyFactory.iconAllowOverlap(false),
      PropertyFactory.textField(Expression.get(MapGeoJson.PROPERTY_LABEL)),
      PropertyFactory.textFont(arrayOf(STOP_LABEL_FONT)),
      PropertyFactory.textSize(LABEL_SIZE),
      PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
      PropertyFactory.textOffset(arrayOf(0f, LABEL_OFFSET)),
      PropertyFactory.textMaxWidth(LABEL_MAX_WIDTH),
      // Le nom cède la place s'il ne tient pas ; le pictogramme, lui, reste.
      PropertyFactory.textOptional(true),
      PropertyFactory.textColor(colors.label.toArgb()),
      PropertyFactory.textHaloColor(colors.labelHalo.toArgb()),
      PropertyFactory.textHaloWidth(LABEL_HALO_WIDTH),
    )
  }

/** La pastille d'un groupe d'arrêts, dont le rayon croît avec le nombre de points regroupés. */
private fun clusterCircleLayer(colors: MapStopColors): CircleLayer =
  CircleLayer(CLUSTER_CIRCLE_LAYER, STOPS_CLUSTERED_SOURCE).apply {
    setFilter(Expression.has(CLUSTER_COUNT_PROPERTY))
    withProperties(
      PropertyFactory.circleColor(colors.cluster.toArgb()),
      PropertyFactory.circleStrokeWidth(CLUSTER_STROKE_WIDTH),
      PropertyFactory.circleStrokeColor(colors.onCluster.toArgb()),
      PropertyFactory.circleRadius(
        Expression.step(
          Expression.get(CLUSTER_COUNT_PROPERTY),
          Expression.literal(CLUSTER_RADIUS_SMALL),
          Expression.stop(CLUSTER_MEDIUM_COUNT, CLUSTER_RADIUS_MEDIUM),
          Expression.stop(CLUSTER_LARGE_COUNT, CLUSTER_RADIUS_LARGE),
        ),
      ),
    )
  }

/**
 * Le nombre d'arrêts regroupés, écrit dans la pastille.
 *
 * SPEC.md § 9 : aucune information portée par la seule couleur ni par la seule taille — un groupe
 * annonce son compte en chiffres.
 */
private fun clusterCountLayer(colors: MapStopColors): SymbolLayer =
  SymbolLayer(CLUSTER_COUNT_LAYER, STOPS_CLUSTERED_SOURCE).apply {
    setFilter(Expression.has(CLUSTER_COUNT_PROPERTY))
    withProperties(
      PropertyFactory.textField(Expression.toString(Expression.get(CLUSTER_COUNT_ABBREVIATED))),
      PropertyFactory.textFont(arrayOf(STOP_LABEL_FONT)),
      PropertyFactory.textSize(CLUSTER_TEXT_SIZE),
      PropertyFactory.textColor(colors.onCluster.toArgb()),
      PropertyFactory.textAllowOverlap(true),
      PropertyFactory.textIgnorePlacement(true),
    )
  }

/**
 * Les dessins des marqueurs d'arrêt, prêts à poser sur la feuille de style.
 *
 * Chaque dessin est composé **une seule fois**, à l'installation de la feuille : la pastille, son
 * liseré et le pictogramme teinté sont aplatis en une image, ce qui évite une couche de cercles en
 * plus sous chaque couche de symboles. Rien de tout cela ne se rejoue pendant un déplacement.
 */
@Composable
fun rememberStopIcons(colors: MapStopColors): Map<StopIcon, Bitmap> {
  val context = LocalContext.current
  val size = with(LocalDensity.current) { StopMarkerSize.roundToPx() }
  val stroke = with(LocalDensity.current) { StopMarkerStroke.toPx() }
  return remember(context, colors, size, stroke) {
    StopIcon.entries.associateWith { icon -> context.stopMarkerBitmap(icon.drawable, colors, size, stroke) }
  }
}

private fun Context.stopMarkerBitmap(@DrawableRes icon: Int, colors: MapStopColors, size: Int, stroke: Float): Bitmap {
  val bitmap = createBitmap(size, size)
  val canvas = Canvas(bitmap)
  val center = size / 2f
  val radius = center - stroke
  canvas.drawCircle(
    center,
    center,
    radius,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.plate.toArgb()
      style = Paint.Style.FILL
    },
  )
  canvas.drawCircle(
    center,
    center,
    radius,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.plateStroke.toArgb()
      style = Paint.Style.STROKE
      strokeWidth = stroke
    },
  )
  val glyph = checkNotNull(ResourcesCompat.getDrawable(resources, icon, theme)).mutate()
  glyph.setTint(colors.onPlate.toArgb())
  val inset = (size * GLYPH_INSET_RATIO).toInt()
  glyph.setBounds(inset, inset, size - inset, size - inset)
  glyph.draw(canvas)
  return bitmap
}

private fun stopLayerId(source: String, tier: ZoomTier): String = "$source-${tier.name.lowercase()}"

/** Le préfixe de tous les identifiants d'arrêts : il les rend reconnaissables d'un coup d'œil. */
private const val STOPS_PREFIX = "escale-stops"

/** La source des arrêts dessinés un à un, en deçà du seuil de regroupement. */
const val STOPS_SOURCE = STOPS_PREFIX

/** La source des arrêts regroupés, au-delà de 200 points (règle 6). */
const val STOPS_CLUSTERED_SOURCE = "$STOPS_PREFIX-clustered"

private const val CLUSTER_CIRCLE_LAYER = "$STOPS_PREFIX-cluster"
private const val CLUSTER_COUNT_LAYER = "$STOPS_PREFIX-cluster-count"
private const val CLUSTER_COUNT_ABBREVIATED = "point_count_abbreviated"

/** La seule fonte que le serveur MOTIS sert en glyphes, et celle qu'emploient les feuilles. */
private const val STOP_LABEL_FONT = "Noto Sans Bold"

private const val LABEL_SIZE = 11f
private const val LABEL_OFFSET = 1.1f
private const val LABEL_MAX_WIDTH = 7f
private const val LABEL_HALO_WIDTH = 1.4f

/** Rayon de regroupement, en pixels de tuile : le défaut de MapLibre, qui vaut un demi-doigt. */
private const val CLUSTER_RADIUS = 60

/** Au-delà de ce zoom, chaque arrêt reprend son dessin : regrouper à la rue n'aide plus. */
private const val CLUSTER_MAX_ZOOM = 16

private const val CLUSTER_RADIUS_SMALL = 14f
private const val CLUSTER_RADIUS_MEDIUM = 18f
private const val CLUSTER_RADIUS_LARGE = 24f
private const val CLUSTER_MEDIUM_COUNT = 25
private const val CLUSTER_LARGE_COUNT = 100
private const val CLUSTER_STROKE_WIDTH = 2f
private const val CLUSTER_TEXT_SIZE = 12f

/** Part du dessin laissée à la pastille autour du pictogramme. */
private const val GLYPH_INSET_RATIO = 0.24f

/** Un marqueur d'arrêt : plus petit qu'un marqueur de trajet, il y en a des centaines. */
private val StopMarkerSize: Dp = 22.dp
private val StopMarkerStroke: Dp = 1.5.dp
