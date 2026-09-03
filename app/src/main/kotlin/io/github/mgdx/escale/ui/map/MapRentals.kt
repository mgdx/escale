package io.github.mgdx.escale.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import io.github.mgdx.escale.core.geo.RentalMarkerKind
import io.github.mgdx.escale.core.model.RentalFormFactor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * Les stations et véhicules en libre-service sur la carte (SPEC.md § 5.7).
 *
 * Ce fichier est le jumeau de `MapStops.kt`, et volontairement : les neuf règles de fluidité y sont
 * tenues par les mêmes moyens, éprouvés par le lot des arrêts.
 *
 * **Règle 6** : des couches MapLibre alimentées par des sources GeoJSON, jamais des vues Android.
 * **Règle 5** : « franchir un seuil de zoom vers le bas ne déclenche aucune requête : on masque des
 * couches déjà chargées ». Chaque famille a sa source et sa couche, bornée par le `minzoom` de son
 * palier — 13 pour les stations, 15 pour les véhicules isolés. Redescendre de 16 à 14 éteint la
 * couche des véhicules sans un octet de réseau, et « on ajoute, on ne remplace pas » découle de la
 * construction même des couches : celle des stations reste allumée au zoom 16.
 *
 * **Quatre sources et non deux**, à la différence des arrêts : le regroupement d'une source GeoJSON
 * s'applique à toutes ses entités, sans filtre possible. Mélanger stations et véhicules dans une
 * même source regroupante ferait apparaître, dès le zoom 13, des pastilles comptant des véhicules
 * qui ne doivent pas encore se voir. Chaque famille a donc son couple « ordinaire / regroupante ».
 */

/**
 * Les couleurs du libre-service, prises au thème Material et jamais codées en dur.
 *
 * Elles diffèrent de celles des arrêts, mais **ce n'est pas la couleur qui distingue les familles**
 * (SPEC.md § 9) : c'est la forme de la pastille et le pictogramme. La teinte n'est qu'un accent.
 */
@Immutable
data class MapRentalColors(
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
 * Les dessins de marqueur de libre-service, un par type de véhicule.
 *
 * Le pictogramme dit **quel véhicule**, la forme de la pastille dit **station ou véhicule isolé**,
 * et le libellé de la station dit son nom : trois porteurs d'information, dont aucun n'est la
 * couleur (SPEC.md § 9). Les icônes sont celles que le projet possède déjà
 * (docs/architecture.md § 11.2) ; aucune n'est redessinée.
 */
enum class RentalIcon(@get:DrawableRes val drawable: Int, @get:StringRes val label: Int) {
  BICYCLE(R.drawable.ic_pedal_bike, R.string.map_rental_form_bicycle),
  SCOOTER(R.drawable.ic_electric_scooter, R.string.map_rental_form_scooter),
  MOPED(R.drawable.ic_moped, R.string.map_rental_form_moped),
  CAR(R.drawable.ic_directions_car, R.string.map_rental_form_car),

  /** Type inconnu ou non publié : le pictogramme générique du libre-service. */
  OTHER(R.drawable.ic_bike_scooter, R.string.map_rental_form_other),
  ;

  /** L'identifiant sous lequel le dessin est posé sur la feuille de style. */
  fun imageId(kind: RentalMarkerKind): String = "$RENTALS_PREFIX-icon-${kind.name.lowercase()}-${name.lowercase()}"

  companion object {
    /** Le dessin d'un type de véhicule. Tout ce que l'application ne sait pas nommer devient [OTHER]. */
    fun of(formFactor: RentalFormFactor?): RentalIcon = when (formFactor) {
      RentalFormFactor.BICYCLE, RentalFormFactor.CARGO_BICYCLE -> BICYCLE
      RentalFormFactor.SCOOTER_STANDING, RentalFormFactor.SCOOTER_SEATED -> SCOOTER
      RentalFormFactor.MOPED -> MOPED
      RentalFormFactor.CAR -> CAR
      RentalFormFactor.OTHER, null -> OTHER
    }
  }
}

/** La source ordinaire d'une famille, celle qui dessine chaque point un à un. */
fun rentalSource(kind: RentalMarkerKind): String = "$RENTALS_PREFIX-${kind.name.lowercase()}"

/** La source regroupante d'une famille, au-delà de 200 points (règle 6). */
fun rentalClusteredSource(kind: RentalMarkerKind): String = "${rentalSource(kind)}-clustered"

/**
 * Pose les quatre sources de libre-service et leurs couches sur [this].
 *
 * Appelée une fois par feuille de style chargée, comme les arrêts : une nouvelle feuille remplace
 * toutes les couches, il n'y a donc jamais rien à empiler ni à démonter (règle 8). Les marqueurs se
 * glissent **sous** les libellés de rue du fond de carte quand celui-ci en a.
 */
fun Style.installRentalLayers(colors: MapRentalColors, icons: Map<RentalIconKey, Bitmap>) {
  for (kind in RentalMarkerKind.entries) {
    addSource(GeoJsonSource(rentalSource(kind)))
    addSource(
      GeoJsonSource(
        rentalClusteredSource(kind),
        GeoJsonOptions()
          .withCluster(true)
          .withClusterRadius(RENTAL_CLUSTER_RADIUS)
          .withClusterMaxZoom(RENTAL_CLUSTER_MAX_ZOOM),
      ),
    )
  }
  icons.forEach { (key, bitmap) -> addImage(key.icon.imageId(key.kind), bitmap) }

  // Les pastilles de regroupement d'abord, pour qu'un marqueur détaillé dessiné par-dessus gagne
  // l'appui du doigt. Puis les stations, puis les véhicules isolés, qui sont les plus fins.
  for (kind in RentalMarkerKind.entries) {
    addLayerUnderLabels(rentalClusterCircleLayer(kind, colors))
    addLayerUnderLabels(rentalClusterCountLayer(kind, colors))
  }
  for (kind in RentalMarkerKind.entries) {
    addLayerUnderLabels(rentalLayer(rentalSource(kind), kind, colors))
    addLayerUnderLabels(rentalLayer(rentalClusteredSource(kind), kind, colors))
  }
}

/**
 * Les identifiants des couches auxquelles un appui peut répondre (SPEC.md § 5.7, interaction).
 *
 * L'ordre compte : les pastilles de regroupement sont interrogées en dernier, pour qu'un marqueur
 * dessiné par-dessus elles gagne l'appui.
 */
val RENTAL_TAPPABLE_LAYERS: List<String> = buildList {
  for (kind in RentalMarkerKind.entries) {
    add(rentalLayerId(rentalSource(kind), kind))
    add(rentalLayerId(rentalClusteredSource(kind), kind))
  }
  for (kind in RentalMarkerKind.entries) add(rentalClusterCircleLayerId(kind))
}

/**
 * Une couche de libre-service, pour une source et une famille.
 *
 * Le `minzoom` de la couche est celui du palier de la famille : c'est lui, et rien d'autre, qui
 * fait apparaître les stations au zoom 13 et les véhicules isolés au zoom 15, et qui les fait
 * disparaître en dézoomant sans la moindre requête (règle 5).
 */
private fun rentalLayer(source: String, kind: RentalMarkerKind, colors: MapRentalColors): SymbolLayer =
  SymbolLayer(rentalLayerId(source, kind), source).apply {
    setMinZoom(kind.tier.minZoom.toFloat())
    setFilter(Expression.not(Expression.has(CLUSTER_COUNT_PROPERTY)))
    withProperties(
      PropertyFactory.iconImage(Expression.get(MapGeoJson.PROPERTY_ICON)),
      PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
      // Laissé à `false` comme pour les arrêts : c'est MapLibre qui écarte les marqueurs qui se
      // chevauchent, et c'est ce qui garde la carte lisible à plusieurs centaines de points.
      PropertyFactory.iconAllowOverlap(false),
      // Une station porte son nom ; un véhicule isolé n'en a pas, et l'entité pose alors une
      // chaîne vide, que MapLibre n'écrit pas.
      PropertyFactory.textField(Expression.get(MapGeoJson.PROPERTY_LABEL)),
      PropertyFactory.textFont(arrayOf(RENTAL_LABEL_FONT)),
      PropertyFactory.textSize(RENTAL_LABEL_SIZE),
      PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
      PropertyFactory.textOffset(arrayOf(0f, RENTAL_LABEL_OFFSET)),
      PropertyFactory.textMaxWidth(RENTAL_LABEL_MAX_WIDTH),
      // Le nom cède la place s'il ne tient pas ; le pictogramme, lui, reste.
      PropertyFactory.textOptional(true),
      PropertyFactory.textColor(colors.label.toArgb()),
      PropertyFactory.textHaloColor(colors.labelHalo.toArgb()),
      PropertyFactory.textHaloWidth(RENTAL_LABEL_HALO_WIDTH),
    )
  }

/**
 * La pastille d'un groupe de points de libre-service.
 *
 * Elle porte le `minzoom` de sa famille au même titre que la couche détaillée : sans lui, un groupe
 * de véhicules isolés apparaîtrait dès le zoom 13, là où aucun véhicule ne doit se voir.
 */
private fun rentalClusterCircleLayer(kind: RentalMarkerKind, colors: MapRentalColors): CircleLayer =
  CircleLayer(rentalClusterCircleLayerId(kind), rentalClusteredSource(kind)).apply {
    setMinZoom(kind.tier.minZoom.toFloat())
    setFilter(Expression.has(CLUSTER_COUNT_PROPERTY))
    withProperties(
      PropertyFactory.circleColor(colors.cluster.toArgb()),
      PropertyFactory.circleStrokeWidth(RENTAL_CLUSTER_STROKE_WIDTH),
      PropertyFactory.circleStrokeColor(colors.onCluster.toArgb()),
      PropertyFactory.circleRadius(
        Expression.step(
          Expression.get(CLUSTER_COUNT_PROPERTY),
          Expression.literal(RENTAL_CLUSTER_RADIUS_SMALL),
          Expression.stop(RENTAL_CLUSTER_MEDIUM_COUNT, RENTAL_CLUSTER_RADIUS_MEDIUM),
          Expression.stop(RENTAL_CLUSTER_LARGE_COUNT, RENTAL_CLUSTER_RADIUS_LARGE),
        ),
      ),
    )
  }

/**
 * Le nombre de points regroupés, écrit dans la pastille.
 *
 * SPEC.md § 9 : aucune information portée par la seule couleur ni par la seule taille — un groupe
 * annonce son compte en chiffres.
 */
private fun rentalClusterCountLayer(kind: RentalMarkerKind, colors: MapRentalColors): SymbolLayer =
  SymbolLayer(rentalClusterCountLayerId(kind), rentalClusteredSource(kind)).apply {
    setMinZoom(kind.tier.minZoom.toFloat())
    setFilter(Expression.has(CLUSTER_COUNT_PROPERTY))
    withProperties(
      PropertyFactory.textField(Expression.toString(Expression.get(RENTAL_CLUSTER_COUNT_ABBREVIATED))),
      PropertyFactory.textFont(arrayOf(RENTAL_LABEL_FONT)),
      PropertyFactory.textSize(RENTAL_CLUSTER_TEXT_SIZE),
      PropertyFactory.textColor(colors.onCluster.toArgb()),
      PropertyFactory.textAllowOverlap(true),
      PropertyFactory.textIgnorePlacement(true),
    )
  }

/** Un dessin de marqueur : un type de véhicule sur la pastille de sa famille. */
data class RentalIconKey(val kind: RentalMarkerKind, val icon: RentalIcon)

/**
 * Les dessins des marqueurs de libre-service, prêts à poser sur la feuille de style.
 *
 * Chaque dessin est composé **une seule fois**, à l'installation de la feuille, comme celui des
 * arrêts : la pastille, son liseré et le pictogramme teinté sont aplatis en une image. Rien de tout
 * cela ne se rejoue pendant un déplacement (règle 7).
 *
 * **C'est ici que se joue la distinction sans couleur de SPEC.md § 9** : la pastille d'un arrêt est
 * un disque, celle d'une station un carré arrondi, celle d'un véhicule isolé un losange plus petit.
 * Les trois se reconnaissent en noir et blanc.
 */
@Composable
fun rememberRentalIcons(colors: MapRentalColors): Map<RentalIconKey, Bitmap> {
  val context = LocalContext.current
  val density = LocalDensity.current
  val stationSize = with(density) { RentalStationMarkerSize.roundToPx() }
  val vehicleSize = with(density) { RentalVehicleMarkerSize.roundToPx() }
  val stroke = with(density) { RentalMarkerStroke.toPx() }
  return remember(context, colors, stationSize, vehicleSize, stroke) {
    RentalMarkerKind.entries
      .flatMap { kind -> RentalIcon.entries.map { icon -> RentalIconKey(kind, icon) } }
      .associateWith { key ->
        context.rentalMarkerBitmap(
          key = key,
          colors = colors,
          size = if (key.kind == RentalMarkerKind.STATION) stationSize else vehicleSize,
          stroke = stroke,
        )
      }
  }
}

private fun Context.rentalMarkerBitmap(key: RentalIconKey, colors: MapRentalColors, size: Int, stroke: Float): Bitmap {
  val bitmap = createBitmap(size, size)
  val canvas = Canvas(bitmap)
  val plate = key.kind.platePath(size.toFloat(), stroke)
  canvas.drawPath(
    plate,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.plate.toArgb()
      style = Paint.Style.FILL
    },
  )
  canvas.drawPath(
    plate,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.plateStroke.toArgb()
      style = Paint.Style.STROKE
      strokeWidth = stroke
    },
  )
  val glyph = checkNotNull(ResourcesCompat.getDrawable(resources, key.icon.drawable, theme)).mutate()
  glyph.setTint(colors.onPlate.toArgb())
  val ratio = if (key.kind == RentalMarkerKind.STATION) STATION_GLYPH_INSET_RATIO else VEHICLE_GLYPH_INSET_RATIO
  val inset = (size * ratio).toInt()
  glyph.setBounds(inset, inset, size - inset, size - inset)
  glyph.draw(canvas)
  return bitmap
}

/**
 * La forme de la pastille : un carré arrondi pour une station, un losange pour un véhicule isolé.
 *
 * Ni l'une ni l'autre n'est un disque, qui reste réservé aux arrêts de transport : les trois
 * familles se distinguent au premier coup d'œil, et sans distinguer les couleurs (SPEC.md § 9).
 */
private fun RentalMarkerKind.platePath(size: Float, stroke: Float): Path {
  val inset = stroke
  val path = Path()
  when (this) {
    RentalMarkerKind.STATION -> path.addRoundRect(
      RectF(inset, inset, size - inset, size - inset),
      size * PLATE_CORNER_RATIO,
      size * PLATE_CORNER_RATIO,
      Path.Direction.CW,
    )

    RentalMarkerKind.VEHICLE -> {
      val center = size / 2f
      path.moveTo(center, inset)
      path.lineTo(size - inset, center)
      path.lineTo(center, size - inset)
      path.lineTo(inset, center)
      path.close()
    }
  }
  return path
}

private fun rentalLayerId(source: String, kind: RentalMarkerKind): String = "$source-${kind.name.lowercase()}"

private fun rentalClusterCircleLayerId(kind: RentalMarkerKind): String = "${rentalClusteredSource(kind)}-cluster"

private fun rentalClusterCountLayerId(kind: RentalMarkerKind): String = "${rentalClusteredSource(kind)}-cluster-count"

/** Le préfixe de tous les identifiants de libre-service : il les rend reconnaissables d'un coup d'œil. */
private const val RENTALS_PREFIX = "escale-rentals"

private const val RENTAL_CLUSTER_COUNT_ABBREVIATED = "point_count_abbreviated"

/** La seule fonte que le serveur MOTIS sert en glyphes, et celle qu'emploient les feuilles. */
private const val RENTAL_LABEL_FONT = "Noto Sans Bold"

private const val RENTAL_LABEL_SIZE = 11f
private const val RENTAL_LABEL_OFFSET = 1.1f
private const val RENTAL_LABEL_MAX_WIDTH = 7f
private const val RENTAL_LABEL_HALO_WIDTH = 1.4f

/** Rayon de regroupement, en pixels de tuile : le défaut de MapLibre, qui vaut un demi-doigt. */
private const val RENTAL_CLUSTER_RADIUS = 60

/** Au-delà de ce zoom, chaque point reprend son dessin : regrouper à la rue n'aide plus. */
private const val RENTAL_CLUSTER_MAX_ZOOM = 16

private const val RENTAL_CLUSTER_RADIUS_SMALL = 14f
private const val RENTAL_CLUSTER_RADIUS_MEDIUM = 18f
private const val RENTAL_CLUSTER_RADIUS_LARGE = 24f
private const val RENTAL_CLUSTER_MEDIUM_COUNT = 25
private const val RENTAL_CLUSTER_LARGE_COUNT = 100
private const val RENTAL_CLUSTER_STROKE_WIDTH = 2f
private const val RENTAL_CLUSTER_TEXT_SIZE = 12f

/** Arrondi du carré d'une station : assez pour être doux, pas assez pour ressembler à un disque. */
private const val PLATE_CORNER_RATIO = 0.22f

/** Part du dessin laissée à la pastille autour du pictogramme. */
private const val STATION_GLYPH_INSET_RATIO = 0.24f

/** Le losange rogne les angles : son pictogramme doit être davantage rentré pour y tenir. */
private const val VEHICLE_GLYPH_INSET_RATIO = 0.28f

/** Une station : un peu plus grande qu'un arrêt, elle porte un nom et une infobulle riche. */
private val RentalStationMarkerSize: Dp = 24.dp

/** Un véhicule isolé : le plus petit des trois, il y en a des centaines au zoom 15. */
private val RentalVehicleMarkerSize: Dp = 20.dp

private val RentalMarkerStroke: Dp = 1.5.dp
