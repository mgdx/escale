package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.TransitMode

/*
 * Le tracé d'un trajet sur la carte (SPEC.md § 5.3, « Carte du trajet »).
 *
 * Tout ce qui se calcule sans Android se calcule ici, en Kotlin pur et donc en JVM
 * (docs/architecture.md § 1) : quelle portion se dessine comment, de quelle couleur, sous quel
 * libellé, où se posent les marqueurs, et quelle emprise cadrer. `:app` n'a plus qu'à traduire ce
 * résultat en source GeoJSON et en couches MapLibre (SPEC.md § 5.7, règles 6 et 7).
 *
 * Rien n'est journalisé ici, et rien ne doit l'être : une géométrie de trajet dit d'où part
 * l'usager et où il va (SPEC.md § 8 et § 11).
 */

/**
 * La famille de mode d'une portion, qui décide de sa couleur de repli.
 *
 * Volontairement plus grossière que [TransitMode] : la carte n'a pas besoin de distinguer un train
 * régional d'un train de nuit, elle a besoin qu'un tracé de bus ne ressemble pas à un tracé de
 * métro. La couleur exacte est choisie par `:app`, qui seul connaît le thème.
 */
enum class TraceKind {
  WALK,
  BIKE,
  CAR,
  RENTAL,
  RAIL,
  SUBWAY,
  TRAM,
  BUS,
  FERRY,
  TRANSIT,
}

/**
 * Le trait d'une portion. **Aucune information n'est portée par la seule couleur** (SPEC.md § 9) :
 * c'est la forme du trait, et non sa teinte, qui distingue la marche du reste.
 */
enum class TraceStroke {
  /** Portion parcourue en véhicule : trait plein. */
  SOLID,

  /** Portion à pied : pointillé, la convention cartographique usuelle. */
  DOTTED,

  /**
   * Tracé reconstitué en ligne droite entre les deux extrémités de la portion.
   *
   * La liste de résultats demande `detailedLegs=false` (SPEC.md § 7.6) : le serveur renvoie alors
   * des polylignes **vides**. Plutôt qu'une carte nue sous une feuille de résultats, on relie les
   * extrémités — et on le dit, par un tiret long qui ne se confond ni avec un trait plein ni avec
   * un pointillé de marche. L'écran de détail, lui, demande le tracé réel.
   */
  APPROXIMATE,
}

/** Ce que marque un marqueur. Trois formes distinctes, jamais trois teintes (SPEC.md § 9). */
enum class TraceMarkerKind {
  ORIGIN,
  TRANSFER,
  DESTINATION,
}

/**
 * Une portion prête à dessiner.
 *
 * @param points au moins deux points, sans quoi la portion n'est pas produite.
 * @param color couleur de la ligne au format `#RRGGBB`, ou `null` quand le réseau n'en publie pas :
 *   `:app` retombe alors sur la couleur de [kind].
 * @param textColor couleur du libellé, déduite de `routeTextColor` ou calculée pour contraster avec
 *   [color]. `null` quand [color] l'est aussi.
 * @param label nom de la ligne, ou du système de libre-service. Vide quand il n'y en a pas.
 */
data class TraceSegment(
  val points: List<LatLon>,
  val kind: TraceKind,
  val stroke: TraceStroke,
  val color: String? = null,
  val textColor: String? = null,
  val label: String = "",
)

/** Un marqueur de départ, d'arrivée ou de correspondance, avec le nom du lieu qu'il désigne. */
data class TraceMarker(val point: LatLon, val kind: TraceMarkerKind, val label: String = "")

/**
 * Le trajet, prêt à poser sur la carte : ses portions, ses marqueurs, et son emprise.
 *
 * [bounds] est calculée par [boundingBoxOf] sur tous les points retenus, tracés **et** extrémités :
 * un trajet dont une seule portion porte une géométrie reste cadré en entier.
 */
data class JourneyTrace(
  val segments: List<TraceSegment> = emptyList(),
  val markers: List<TraceMarker> = emptyList(),
  val bounds: BoundingBox? = null,
) {
  val isEmpty: Boolean get() = segments.isEmpty() && markers.isEmpty()

  companion object {
    /** L'absence de tracé : ce que la carte montre quand aucun trajet n'est sélectionné. */
    val EMPTY = JourneyTrace()
  }
}

/**
 * Le tracé de [journey], ou [JourneyTrace.EMPTY] si aucun trajet n'est sélectionné.
 *
 * Fonction pure et sans effet de bord : `:app` l'exécute sur un répartiteur de calcul, hors du fil
 * principal (SPEC.md § 5.7, règle 7).
 */
fun journeyTrace(journey: Journey?): JourneyTrace {
  val legs = journey?.legs?.takeIf { it.isNotEmpty() } ?: return JourneyTrace.EMPTY
  val segments = legs.mapNotNull(::segmentOf)
  val markers = markersOf(legs)
  val points = segments.flatMap { it.points } + markers.map { it.point }
  return JourneyTrace(segments = segments, markers = markers, bounds = boundingBoxOf(points))
}

/** Une portion, ou `null` si elle ne tient pas debout : deux points identiques ne font pas un trait. */
private fun segmentOf(leg: JourneyLeg): TraceSegment? {
  val real = leg.geometry.size >= MIN_POINTS
  val points = if (real) leg.geometry else listOf(leg.from.coordinates, leg.to.coordinates)
  if (points.size < MIN_POINTS || points.all { it == points.first() }) return null
  val kind = leg.traceKind()
  val color = HexColor.normalize(leg.lineColor())
  return TraceSegment(
    points = points,
    kind = kind,
    stroke = when {
      !real -> TraceStroke.APPROXIMATE
      kind == TraceKind.WALK -> TraceStroke.DOTTED
      else -> TraceStroke.SOLID
    },
    color = color,
    // `routeTextColor` quand le réseau le publie **et qu'elle se lit** sur la couleur de ligne ;
    // sinon le noir ou le blanc, celui des deux qui contraste. Un libellé illisible vaut un libellé
    // absent (SPEC.md § 9). Le libellé du tracé est écrit petit : c'est le seuil du texte courant.
    textColor = HexColor.textOn(background = color, preferred = leg.lineTextColor()),
    label = leg.lineLabel(),
  )
}

/**
 * Les marqueurs : le départ, l'arrivée, et une correspondance à chaque jointure de portions.
 *
 * Deux portions qui se touchent ne donnent qu'un marqueur, et une jointure qui retombe sur le
 * départ ou sur l'arrivée n'en donne aucun : un trajet d'une seule portion porte deux marqueurs,
 * pas trois.
 */
private fun markersOf(legs: List<JourneyLeg>): List<TraceMarker> {
  val origin = legs.first().from
  val destination = legs.last().to
  val transfers = legs.dropLast(1)
    .map { it.to }
    .filter { it.coordinates != origin.coordinates && it.coordinates != destination.coordinates }
    .distinctBy { it.coordinates }
    .map { TraceMarker(it.coordinates, TraceMarkerKind.TRANSFER, it.name.placeLabel()) }
  return buildList {
    add(TraceMarker(origin.coordinates, TraceMarkerKind.ORIGIN, origin.name.placeLabel()))
    addAll(transfers)
    add(TraceMarker(destination.coordinates, TraceMarkerKind.DESTINATION, destination.name.placeLabel()))
  }
}

/**
 * Le nom d'un lieu, débarrassé des marques de position de MOTIS.
 *
 * Le serveur nomme `START` et `END` les extrémités saisies en coordonnées : ce sont des marques
 * internes, pas des noms de lieux, et les afficher sur la carte n'apprendrait rien à personne.
 */
private fun String.placeLabel(): String = if (this in PLACEHOLDER_NAMES) "" else trim()

private fun JourneyLeg.lineColor(): String? = when (this) {
  is JourneyLeg.Transit -> routeColor
  is JourneyLeg.Rental -> rental?.color
  else -> null
}

private fun JourneyLeg.lineTextColor(): String? = (this as? JourneyLeg.Transit)?.routeTextColor

/** Le libellé qui accompagne le tracé : le numéro de ligne s'il existe, son nom sinon. */
private fun JourneyLeg.lineLabel(): String = when (this) {
  is JourneyLeg.Transit -> (routeShortName?.trim().orEmpty().ifEmpty { lineName }).trim()
  is JourneyLeg.Rental -> rental?.systemName?.trim().orEmpty()
  else -> ""
}

// Une table de correspondance, pas un algorithme : detekt y compte une branche par mode, ce qui n'a
// pas de sens ici. La découper en sous-fonctions rendrait la table moins lisible, pas plus.
@Suppress("CyclomaticComplexMethod")
private fun JourneyLeg.traceKind(): TraceKind = when (this) {
  is JourneyLeg.Walk -> TraceKind.WALK

  is JourneyLeg.Bike -> TraceKind.BIKE

  is JourneyLeg.Car -> TraceKind.CAR

  is JourneyLeg.Rental -> TraceKind.RENTAL

  is JourneyLeg.Transit -> when (mode) {
    TransitMode.WALK -> TraceKind.WALK

    TransitMode.BIKE -> TraceKind.BIKE

    TransitMode.RENTAL -> TraceKind.RENTAL

    TransitMode.CAR, TransitMode.HGV, TransitMode.CAR_PARKING, TransitMode.CAR_DROPOFF,
    TransitMode.ODM, TransitMode.RIDE_SHARING, TransitMode.FLEX,
    -> TraceKind.CAR

    TransitMode.SUBWAY -> TraceKind.SUBWAY

    TransitMode.TRAM -> TraceKind.TRAM

    TransitMode.BUS, TransitMode.COACH -> TraceKind.BUS

    TransitMode.FERRY -> TraceKind.FERRY

    TransitMode.RAIL, TransitMode.HIGHSPEED_RAIL, TransitMode.LONG_DISTANCE,
    TransitMode.NIGHT_RAIL, TransitMode.REGIONAL_RAIL, TransitMode.SUBURBAN,
    -> TraceKind.RAIL

    TransitMode.TRANSIT, TransitMode.AIRPLANE, TransitMode.FUNICULAR,
    TransitMode.AERIAL_LIFT, TransitMode.OTHER,
    -> TraceKind.TRANSIT
  }
}

/** Un trait a besoin de deux points distincts. */
private const val MIN_POINTS = 2

/** Ce que MOTIS écrit à la place d'un nom quand l'extrémité a été saisie en coordonnées. */
private val PLACEHOLDER_NAMES = setOf("START", "END")
