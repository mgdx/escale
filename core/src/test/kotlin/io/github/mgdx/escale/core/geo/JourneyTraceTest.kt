package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.RentalInfo
import io.github.mgdx.escale.core.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le tracé d'un trajet, éprouvé sans Android (SPEC.md § 10).
 *
 * Ce que ces cas vérifient est exactement ce que le rendu ne dira pas : quelle portion est
 * pointillée, quelle couleur l'emporte, où tombent les marqueurs, et quelle emprise cadrer.
 */
class JourneyTraceTest {

  private val nord = LatLon(48.8800, 2.3550)
  private val sud = LatLon(48.8400, 2.3500)
  private val est = LatLon(48.8600, 2.4000)

  // --- Portions et traits -------------------------------------------------------------------

  @Test
  fun `aucun trajet sélectionné, aucun tracé`() {
    assertEquals(JourneyTrace.EMPTY, journeyTrace(null))
    assertTrue(journeyTrace(null).isEmpty)
  }

  @Test
  fun `une portion à pied se distingue par un pointillé, pas par sa couleur`() {
    val trace = journeyTrace(journey(walk(geometry = listOf(sud, nord))))
    val segment = trace.segments.single()
    assertEquals(TraceStroke.DOTTED, segment.stroke)
    assertEquals(TraceKind.WALK, segment.kind)
    // Aucune couleur de réseau sur une portion à pied : `:app` prend celle de son mode.
    assertNull(segment.color)
  }

  @Test
  fun `une portion en véhicule se trace en trait plein`() {
    val trace = journeyTrace(journey(transit(geometry = listOf(sud, nord))))
    assertEquals(TraceStroke.SOLID, trace.segments.single().stroke)
  }

  @Test
  fun `sans géométrie, la portion est reliée en ligne droite et le dit`() {
    // C'est le cas de la liste de résultats, qui demande `detailedLegs=false` (SPEC.md § 7.6).
    val trace = journeyTrace(journey(transit(geometry = emptyList())))
    val segment = trace.segments.single()
    assertEquals(TraceStroke.APPROXIMATE, segment.stroke)
    assertEquals(listOf(sud, nord), segment.points)
  }

  @Test
  fun `une portion réduite à un point ne produit aucun trait`() {
    val trace = journeyTrace(journey(walk(from = sud, to = sud, geometry = emptyList())))
    assertTrue(trace.segments.isEmpty())
  }

  @Test
  fun `la géométrie du serveur est reprise telle quelle, jamais redécodée ni simplifiée`() {
    val points = listOf(sud, est, nord)
    assertEquals(points, journeyTrace(journey(transit(geometry = points))).segments.single().points)
  }

  // --- Couleurs, SPEC.md § 5.2 et § 5.3 ------------------------------------------------------

  @Test
  fun `la couleur de la ligne l'emporte sur celle du mode`() {
    val segment = journeyTrace(journey(transit(color = "003399", textColor = "ffffff"))).segments.single()
    assertEquals("#003399", segment.color)
    assertEquals("#FFFFFF", segment.textColor)
  }

  @Test
  fun `une couleur de texte publiée mais illisible est écartée`() {
    // Le blanc que le réseau annonce sur ce vert ne donne que 2,4:1, sous le 4,5:1 de SPEC.md § 9 :
    // le libellé passe au noir, qui y atteint 7:1. Un libellé illisible vaut un libellé absent.
    val segment = journeyTrace(journey(transit(color = "4dbd38", textColor = "ffffff"))).segments.single()
    assertEquals("#4DBD38", segment.color)
    assertEquals("#000000", segment.textColor)
  }

  @Test
  fun `sans couleur de texte, celle qui contraste est calculée`() {
    // Jaune vif : c'est du texte noir qu'il faut, pas du blanc par habitude (SPEC.md § 9).
    assertEquals("#000000", journeyTrace(journey(transit(color = "#FFCD00"))).segments.single().textColor)
    // Bleu nuit : l'inverse.
    assertEquals("#FFFFFF", journeyTrace(journey(transit(color = "#003399"))).segments.single().textColor)
  }

  @Test
  fun `une couleur illisible retombe sur la couleur du mode`() {
    val segment = journeyTrace(journey(transit(color = "bleu"))).segments.single()
    assertNull(segment.color)
    assertNull(segment.textColor)
  }

  @Test
  fun `un véhicule partagé porte la couleur et le nom de son exploitant`() {
    val segment = journeyTrace(journey(rental(system = "Vélib'", color = "#1D9E75"))).segments.single()
    assertEquals(TraceKind.RENTAL, segment.kind)
    assertEquals("#1D9E75", segment.color)
    assertEquals("Vélib'", segment.label)
  }

  @Test
  fun `le libellé du tracé est le numéro de ligne, à défaut son nom`() {
    assertEquals("4", journeyTrace(journey(transit(shortName = "4", line = "Métro 4"))).segments.single().label)
    assertEquals("Métro 4", journeyTrace(journey(transit(line = "Métro 4"))).segments.single().label)
  }

  @Test
  fun `chaque mode a sa famille de tracé`() {
    assertEquals(TraceKind.SUBWAY, kindOf(TransitMode.SUBWAY))
    assertEquals(TraceKind.TRAM, kindOf(TransitMode.TRAM))
    assertEquals(TraceKind.BUS, kindOf(TransitMode.BUS))
    assertEquals(TraceKind.BUS, kindOf(TransitMode.COACH))
    assertEquals(TraceKind.FERRY, kindOf(TransitMode.FERRY))
    assertEquals(TraceKind.RAIL, kindOf(TransitMode.SUBURBAN))
    assertEquals(TraceKind.RAIL, kindOf(TransitMode.HIGHSPEED_RAIL))
    assertEquals(TraceKind.TRANSIT, kindOf(TransitMode.OTHER))
    // Les modes hors périmètre v1 se rangent avec la voiture, sans famille dédiée.
    assertEquals(TraceKind.CAR, kindOf(TransitMode.RIDE_SHARING))
  }

  // --- Marqueurs, SPEC.md § 5.3 --------------------------------------------------------------

  @Test
  fun `un trajet d'une seule portion porte un départ et une arrivée, pas de correspondance`() {
    val markers = journeyTrace(journey(walk(geometry = listOf(sud, nord)))).markers
    assertEquals(listOf(TraceMarkerKind.ORIGIN, TraceMarkerKind.DESTINATION), markers.map { it.kind })
    assertEquals(sud, markers.first().point)
    assertEquals(nord, markers.last().point)
  }

  @Test
  fun `chaque jointure de portions pose un marqueur de correspondance nommé`() {
    val trace = journeyTrace(
      journey(
        walk(from = sud, to = est, endName = "Gare de Lyon"),
        transit(from = est, to = nord, name = "Gare de Lyon"),
      ),
    )
    val transfers = trace.markers.filter { it.kind == TraceMarkerKind.TRANSFER }
    assertEquals(1, transfers.size)
    assertEquals(est, transfers.single().point)
    // Le nom de l'arrêt est porté par le marqueur : la carte reste lisible sans distinguer les
    // couleurs (SPEC.md § 9).
    assertEquals("Gare de Lyon", transfers.single().label)
  }

  @Test
  fun `les marques de position de MOTIS ne s'affichent pas comme des noms de lieux`() {
    val trace = journeyTrace(journey(walk(geometry = listOf(sud, nord), name = "START", endName = "END")))
    assertTrue(trace.markers.all { it.label.isEmpty() })
  }

  // --- Emprise, SPEC.md § 5.7 ----------------------------------------------------------------

  @Test
  fun `l'emprise couvre le tracé et ses extrémités`() {
    val trace = journeyTrace(
      journey(
        walk(from = sud, to = est, geometry = listOf(sud, est)),
        transit(from = est, to = nord, geometry = emptyList()),
      ),
    )
    val bounds = checkNotNull(trace.bounds)
    assertEquals(sud.lat, bounds.min.lat, 0.0)
    assertEquals(nord.lat, bounds.max.lat, 0.0)
    assertEquals(sud.lon, bounds.min.lon, 0.0)
    assertEquals(est.lon, bounds.max.lon, 0.0)
  }

  @Test
  fun `un trajet de quelques mètres donne une emprise qui se cadre par son centre`() {
    val depart = LatLon(48.8566, 2.3522)
    val arrivee = LatLon(48.85662, 2.35222)
    val bounds = checkNotNull(journeyTrace(journey(walk(from = depart, to = arrivee))).bounds)
    assertTrue(bounds.isPointLike())
  }

  // --- Une vraie géométrie, capturée depuis api.transitous.org -------------------------------

  @Test
  fun `une polyligne réelle décodée en précision 6 se trace autour de Berlin`() {
    // Extrait de data/src/test/resources/fixtures/plan_walk_detailed.json : le tracé d'une portion
    // à pied réellement renvoyée par le serveur, décodée par le décodeur du projet.
    val points = PolylineDecoder.decode(BERLIN_WALK_POLYLINE, PolylineDecoder.PRECISION_V6)
    val trace = journeyTrace(journey(walk(from = points.first(), to = points.last(), geometry = points)))
    val bounds = checkNotNull(trace.bounds)
    assertEquals(TraceStroke.DOTTED, trace.segments.single().stroke)
    assertEquals(points.size, trace.segments.single().points.size)
    assertEquals(52.52, bounds.min.lat, 0.02)
    assertEquals(13.37, bounds.min.lon, 0.02)
  }

  // --- Fabriques ------------------------------------------------------------------------------

  private fun kindOf(mode: TransitMode) = journeyTrace(journey(transit(mode = mode))).segments.single().kind

  private fun place(point: LatLon, name: String) = Place(
    name = name,
    coordinates = point,
    stopId = null,
    track = null,
    scheduledTime = INSTANT,
    time = INSTANT,
  )

  // --- Géométrie réelle ou reconstituée (SPEC.md § 7, règle 6) ------------------------------

  @Test
  fun `un trajet sans polyligne n est pas tracé`() {
    // Ce que rend la liste de résultats, qui demande `detailedLegs=false` : des extrémités, et
    // rien entre elles. La carte n'en tirerait qu'une ligne droite.
    assertFalse(journey(walk(geometry = emptyList())).isTraced)
  }

  @Test
  fun `un trajet dont une portion porte sa polyligne est tracé`() {
    assertTrue(journey(walk(geometry = emptyList()), transit(geometry = listOf(sud, est))).isTraced)
  }

  @Test
  fun `un trajet vide n est pas tracé`() {
    assertFalse(journey().isTraced)
  }

  private fun walk(
    from: LatLon = sud,
    to: LatLon = nord,
    geometry: List<LatLon> = emptyList(),
    name: String = "Départ",
    endName: String = "Arrivée",
  ) = JourneyLeg.Walk(
    startTime = INSTANT,
    endTime = INSTANT,
    scheduledStartTime = INSTANT,
    scheduledEndTime = INSTANT,
    duration = Duration.ofMinutes(5),
    from = place(from, name),
    to = place(to, endName),
    geometry = geometry,
  )

  @Suppress("LongParameterList")
  private fun transit(
    from: LatLon = sud,
    to: LatLon = nord,
    geometry: List<LatLon> = emptyList(),
    mode: TransitMode = TransitMode.BUS,
    color: String? = null,
    textColor: String? = null,
    line: String = "",
    shortName: String? = null,
    name: String = "Départ",
  ) = JourneyLeg.Transit(
    startTime = INSTANT,
    endTime = INSTANT,
    scheduledStartTime = INSTANT,
    scheduledEndTime = INSTANT,
    duration = Duration.ofMinutes(10),
    from = place(from, name),
    to = place(to, "Arrivée"),
    geometry = geometry,
    mode = mode,
    lineName = line,
    routeShortName = shortName,
    routeColor = color,
    routeTextColor = textColor,
  )

  private fun rental(system: String, color: String) = JourneyLeg.Rental(
    startTime = INSTANT,
    endTime = INSTANT,
    scheduledStartTime = INSTANT,
    scheduledEndTime = INSTANT,
    duration = Duration.ofMinutes(8),
    from = place(sud, "Station"),
    to = place(nord, "Station"),
    rental = RentalInfo(
      systemId = "velib",
      systemName = system,
      providerId = null,
      color = color,
      url = null,
      fromStationName = null,
      toStationName = null,
      rentalUriAndroid = null,
      formFactor = null,
      propulsionType = null,
      returnConstraint = null,
    ),
  )

  private fun journey(vararg legs: JourneyLeg) = Journey(
    id = null,
    startTime = INSTANT,
    endTime = INSTANT,
    scheduledStartTime = INSTANT,
    scheduledEndTime = INSTANT,
    duration = Duration.ofMinutes(20),
    transfers = 0,
    legs = legs.toList(),
  )

  private companion object {
    val INSTANT: Instant = Instant.parse("2026-09-01T08:00:00Z")

    const val BERLIN_WALK_POLYLINE =
      "ka{dcBwb_oXES??qHpE??i@o@??qBvA??OeO??xEwC??vCmB??pEoC??pJaG??pH{ENhO??tBuA??v@d@??fFaD??" +
        "p@oB??rCB??|@m@??fGyD??jAs@??~@m@??^U??fC_B??bF_D??nMgI??dOwI??^|A??d@U??\\O??rAs@??tBkA" +
        "??ZQ??x@c@??_AsH??p@A??dq@K??b@A??}BaOeAaKu@_MUcLIcJCaO??s@B???`@???zF??mBF??@eC"
  }
}
