package io.github.mgdx.escale.core.model

/**
 * Mode de déplacement, décalque du schéma `Mode` de l'OpenAPI MOTIS.
 *
 * Volontairement amputé de ce que le contrat (docs/architecture.md § 4) exclut :
 * - les valeurs de mise au point `DEBUG_BUS_ROUTE`, `DEBUG_RAILWAY_ROUTE`, `DEBUG_FERRY_ROUTE` ;
 * - les valeurs dépréciées `REGIONAL_FAST_RAIL`, `AREAL_LIFT`, `METRO`, `CABLE_CAR`, que le serveur
 *   n'émet plus et qu'il remplace respectivement par `REGIONAL_RAIL`, `AERIAL_LIFT`, `SUBWAY` et
 *   `AERIAL_LIFT`.
 *
 * Une valeur inconnue reçue de l'API se mappe sur [OTHER] plutôt que de faire échouer la réponse.
 */
enum class TransitMode {
  // --- Modes de rue -----------------------------------------------------------------------

  WALK,
  BIKE,

  /** Véhicule en libre-service. Marqué « expérimental » côté MOTIS. */
  RENTAL,
  CAR,

  /** Poids lourd. Le serveur ne le propose que pour les trajets directs. */
  HGV,
  CAR_PARKING,
  CAR_DROPOFF,

  // --- Modes à la fois de rue et de transport (hors périmètre v1, SPEC.md § 1.2) ------------

  /** Transport à la demande. Affiché s'il arrive, sans interface dédiée en v1. */
  ODM,

  /** Covoiturage. Affiché s'il arrive, sans interface dédiée en v1. */
  RIDE_SHARING,

  /** Transport flexible. Affiché s'il arrive, sans interface dédiée en v1. */
  FLEX,

  // --- Modes de transport en commun ---------------------------------------------------------

  /** Valeur agrégée acceptée en requête ; le serveur ne la renvoie jamais sur une portion. */
  TRANSIT,
  TRAM,
  SUBWAY,
  FERRY,
  AIRPLANE,

  /** Bus urbain, à distinguer de [COACH]. */
  BUS,

  /** Autocar longue distance, à distinguer de [BUS]. */
  COACH,

  /** Valeur agrégée acceptée en requête ; le serveur ne la renvoie jamais sur une portion. */
  RAIL,
  HIGHSPEED_RAIL,
  LONG_DISTANCE,
  NIGHT_RAIL,
  REGIONAL_RAIL,

  /** Train de banlieue : S-Bahn, RER, Elizabeth Line. */
  SUBURBAN,
  FUNICULAR,

  /** Transport par câble : télécabine, téléphérique, télésiège. */
  AERIAL_LIFT,

  /** Tout mode que l'application ne sait pas nommer, y compris une valeur d'API inconnue. */
  OTHER,
  ;

  /** Vrai pour les modes que le serveur peut renvoyer sur une portion de transport en commun. */
  val isTransit: Boolean
    get() = this in TRANSIT_MODES

  /** Vrai pour les modes parcourus par l'usager lui-même, sans horaire. */
  val isStreet: Boolean
    get() = this in STREET_MODES

  companion object {
    private val TRANSIT_MODES = setOf(
      TRANSIT, TRAM, SUBWAY, FERRY, AIRPLANE, BUS, COACH, RAIL, HIGHSPEED_RAIL,
      LONG_DISTANCE, NIGHT_RAIL, REGIONAL_RAIL, SUBURBAN, FUNICULAR, AERIAL_LIFT, OTHER,
    )
    private val STREET_MODES = setOf(WALK, BIKE, RENTAL, CAR, HGV, CAR_PARKING, CAR_DROPOFF)

    /**
     * Paliers de zoom de la carte (SPEC.md § 5.7) : les modes ferrés lourds apparaissent dès le
     * zoom 11, le reste à partir du zoom 13.
     */
    val HEAVY_RAIL_MODES = setOf(RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, SUBURBAN, SUBWAY)
  }
}
