package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.TransitMode

// Les seuils du tableau de SPEC.md § 5.7, nommés une fois pour toutes. Ils sont déclarés hors de
// l'énumération parce qu'une entrée d'énumération ne peut pas lire son propre objet compagnon.
private const val WORLD_ZOOM = 0.0
private const val MAJOR_STATIONS_ZOOM = 11.0
private const val ALL_STOPS_ZOOM = 13.0
private const val POINTS_OF_INTEREST_ZOOM = 15.0
private const val STREET_DETAIL_ZOOM = 17.0

/**
 * Les paliers de densité de la carte (SPEC.md § 5.7).
 *
 * « La carte se densifie progressivement. Rien ne s'affiche tant que l'échelle ne le justifie
 * pas » : chaque palier conserve les couches des paliers inférieurs, on ajoute, on ne remplace pas.
 *
 * Les paliers sont ordonnés du moins dense au plus dense, si bien que `tier >= MAJOR_STATIONS` se
 * lit tel quel, sans comparer des nombres à la main.
 */
enum class ZoomTier(val minZoom: Double) {

  /** Sous le zoom 11 : rien d'autre que le fond de carte, et **aucune requête**. */
  BASE_MAP_ONLY(WORLD_ZOOM),

  /** 11 → 13 : gares et stations de métro. */
  MAJOR_STATIONS(MAJOR_STATIONS_ZOOM),

  /** 13 → 15 : tous les arrêts, et les stations de véhicules en libre-service. */
  ALL_STOPS(ALL_STOPS_ZOOM),

  /** ≥ 15 : véhicules isolés, et les repères d'orientation déjà présents dans les tuiles. */
  POINTS_OF_INTEREST(POINTS_OF_INTEREST_ZOOM),

  /** ≥ 17 : entrées de stations, mobilier, libellés détaillés. */
  STREET_DETAIL(STREET_DETAIL_ZOOM),
  ;

  /**
   * Faut-il demander des arrêts au serveur à ce palier ?
   *
   * Faux sous le zoom 11 : SPEC.md § 7.9 interdit toute requête de carte à cette échelle, où mille
   * points seraient de toute façon illisibles.
   */
  val requestsStops: Boolean
    get() = this >= MAJOR_STATIONS

  /**
   * Les modes à demander à `/api/v6/map/stops` à ce palier.
   *
   * Vide quand aucune requête n'est due. Un ensemble vide voudrait dire « tous les modes » côté
   * API : c'est [requestsStops] qui décide d'appeler, jamais la taille de cet ensemble.
   */
  val stopModes: Set<TransitMode>
    get() = when (this) {
      BASE_MAP_ONLY -> emptySet()
      MAJOR_STATIONS -> TransitMode.HEAVY_RAIL_MODES
      ALL_STOPS, POINTS_OF_INTEREST, STREET_DETAIL -> ALL_TRANSIT_MODES
    }

  companion object {
    /** SPEC.md § 5.7 : « < 11 : aucune requête n'est émise ». */
    const val MIN_STOPS_ZOOM = MAJOR_STATIONS_ZOOM

    /**
     * À partir du zoom 13, les modes de surface s'ajoutent aux modes ferrés lourds
     * (SPEC.md § 5.7). `TRANSIT` est une valeur agrégée qui demanderait tout, et `OTHER` n'existe
     * que côté application : ni l'une ni l'autre n'a sa place dans une requête.
     */
    private val ALL_TRANSIT_MODES: Set<TransitMode> = TransitMode.HEAVY_RAIL_MODES + setOf(
      TransitMode.TRAM,
      TransitMode.BUS,
      TransitMode.COACH,
      TransitMode.FERRY,
      TransitMode.NIGHT_RAIL,
      TransitMode.REGIONAL_RAIL,
      TransitMode.FUNICULAR,
      TransitMode.AERIAL_LIFT,
      TransitMode.AIRPLANE,
    )

    /** Le palier correspondant à un niveau de zoom MapLibre. */
    fun forZoom(zoom: Double): ZoomTier = entries.last { zoom >= it.minZoom }
  }
}
