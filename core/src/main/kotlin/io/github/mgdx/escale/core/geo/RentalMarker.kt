package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.RentalAvailability
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPointKind

/**
 * Les deux familles de marqueurs de libre-service, et le palier de zoom de chacune.
 *
 * Le tableau de SPEC.md § 5.7 les sépare : les **stations** apparaissent au zoom 13, les
 * **véhicules isolés** (free-floating) seulement au zoom 15. C'est ce palier, porté jusque dans le
 * `minzoom` de la couche MapLibre, qui tient la règle 5 : redescendre sous 15 masque les véhicules
 * sans émettre la moindre requête.
 */
enum class RentalMarkerKind(val tier: ZoomTier) {
  /** « 13 → 15 : stations de véhicules en libre-service ». */
  STATION(ZoomTier.ALL_STOPS),

  /** « ≥ 15 : véhicules en libre-service isolés (free-floating) ». */
  VEHICLE(ZoomTier.POINTS_OF_INTEREST),
  ;

  companion object {
    /**
     * La famille de marqueur d'un point, **lue sur sa nature** et non devinée.
     *
     * [RentalPointKind] vient du schéma d'API dont le point provient, `RentalStation` ou
     * `RentalVehicle` ; c'est la seule source qui ne mente pas. Les deux énumérations restent
     * distinctes parce qu'elles ne disent pas la même chose : l'une est une provenance de donnée,
     * que l'écran de détail lit aussi, l'autre un choix de dessin et de palier de zoom qui ne
     * regarde que la carte.
     */
    fun of(kind: RentalPointKind): RentalMarkerKind = when (kind) {
      RentalPointKind.STATION -> STATION
      RentalPointKind.FREE_FLOATING -> VEHICLE
    }
  }
}

/**
 * Ce qu'il faut savoir d'un point de libre-service pour le dessiner et pour remplir son infobulle,
 * et rien d'autre (SPEC.md § 5.7 et § 5.3).
 *
 * Le calcul est ici, en Kotlin pur, pour être vérifiable en JVM : `:app` n'a plus qu'à le
 * sérialiser en GeoJSON et à laisser MapLibre lire les propriétés.
 *
 * @param kind station ou véhicule isolé. Il porte à la fois le palier d'apparition et la **forme**
 *   du marqueur : SPEC.md § 9 interdit qu'une information tienne à la seule couleur, et les trois
 *   familles de la carte — arrêt, station, véhicule — se distinguent par leur dessin et par leur
 *   libellé avant toute teinte.
 * @param docksAvailable places libres au retour, tous types de véhicules confondus. Zéro pour un
 *   véhicule isolé, qui ne se rend nulle part en particulier.
 */
data class RentalMarker(
  val id: String,
  val name: String,
  val point: LatLon,
  val kind: RentalMarkerKind,
  val formFactor: RentalFormFactor?,
  val vehiclesAvailable: Int,
  val docksAvailable: Int,
  val isRenting: Boolean,
  val isReturning: Boolean,
  val rentalUriAndroid: String?,
) {
  /** Le palier à partir duquel le marqueur doit se voir. */
  val tier: ZoomTier get() = kind.tier
}

/**
 * Faut-il interroger `/api/v1/rentals` à ce palier ?
 *
 * Faux sous le zoom 13 : le tableau de SPEC.md § 5.7 ne fait apparaître les stations qu'à partir de
 * là, et SPEC.md § 7.9 interdit de charger ce qui ne s'affichera pas. C'est un seuil **plus haut**
 * que celui des arrêts, qui commencent au zoom 11 : les deux ne se confondent pas.
 */
val ZoomTier.requestsRentals: Boolean
  get() = this >= RentalMarkerKind.STATION.tier

/** Les marqueurs à poser pour une réponse du serveur, dans l'ordre où elle est arrivée. */
fun rentalMarkers(availabilities: List<RentalAvailability>): List<RentalMarker> = availabilities.map { availability ->
  RentalMarker(
    id = availability.stationId,
    name = availability.name,
    point = availability.coordinates,
    kind = RentalMarkerKind.of(availability.kind),
    formFactor = principalFormFactor(availability.formFactors),
    vehiclesAvailable = availability.numVehiclesAvailable,
    docksAvailable = availability.vehicleDocksAvailable.values.sum(),
    isRenting = availability.isRenting,
    isReturning = availability.isReturning,
    rentalUriAndroid = availability.rentalUriAndroid?.takeIf(String::isNotBlank),
  )
}

/**
 * Le type de véhicule qui donne son dessin au marqueur, quand la station en accepte plusieurs.
 *
 * L'ordre est celui de l'encombrement croissant, pas celui que le serveur a renvoyé, qui n'est pas
 * garanti : une station qui accepte des vélos et des trottinettes se dessine en vélo. Rend `null`
 * quand le flux ne publie aucun type, cas où l'appelant retombe sur un pictogramme générique.
 */
fun principalFormFactor(formFactors: List<RentalFormFactor>): RentalFormFactor? =
  FORM_FACTOR_PRIORITY.firstOrNull { it in formFactors } ?: formFactors.firstOrNull()

/**
 * SPEC.md § 5.7, règle 6 : « regroupement (`cluster`) au-delà de 200 points visibles ».
 *
 * Le seuil s'applique **par famille** : deux cents stations et deux cents véhicules ne se
 * regroupent pas ensemble, puisqu'ils ne se voient pas au même palier de zoom.
 */
fun shouldClusterRentals(count: Int): Boolean = count > MapLoadRules.CLUSTER_THRESHOLD

/** Du plus léger au plus encombrant, pour choisir le dessin d'une station multi-véhicules. */
private val FORM_FACTOR_PRIORITY = listOf(
  RentalFormFactor.BICYCLE,
  RentalFormFactor.CARGO_BICYCLE,
  RentalFormFactor.SCOOTER_STANDING,
  RentalFormFactor.SCOOTER_SEATED,
  RentalFormFactor.MOPED,
  RentalFormFactor.CAR,
  RentalFormFactor.OTHER,
)
