package io.github.mgdx.escale.core.model

/** Manœuvre d'un itinéraire piéton, cycliste ou routier, décalque du schéma `Direction`. */
enum class StepDirection {
  DEPART,
  HARD_LEFT,
  LEFT,
  SLIGHTLY_LEFT,
  CONTINUE,
  SLIGHTLY_RIGHT,
  RIGHT,
  HARD_RIGHT,
  CIRCLE_CLOCKWISE,
  CIRCLE_COUNTERCLOCKWISE,
  STAIRS,
  ELEVATOR,
  UTURN_LEFT,
  UTURN_RIGHT,
}

/**
 * Une instruction pas-à-pas, décalque du schéma `StepInstruction` de l'OpenAPI MOTIS.
 *
 * N'est peuplée que si la requête a demandé `detailedLegs=true`, c'est-à-dire uniquement à
 * l'ouverture de l'écran de détail (SPEC.md § 7.6).
 *
 * Les champs `exit`, `stayOn` et `area` de l'API sont documentés « Not implemented! » côté MOTIS :
 * ils ne sont pas repris ici tant qu'ils ne portent rien.
 */
data class TravelStep(
  val direction: StepDirection,
  /** Nom de la rue empruntée. Vide quand la voie n'en a pas. */
  val streetName: String,
  val distanceMeters: Double,
  /** Géométrie du segment, déjà décodée. */
  val geometry: List<LatLon> = emptyList(),
  /** Niveau OpenStreetMap au début du segment : un changement de niveau signale un escalier. */
  val fromLevel: Double? = null,
  val toLevel: Double? = null,
  /** Dénivelé positif du segment, en mètres. Nul si le serveur n'a pas de modèle de terrain. */
  val elevationUpMeters: Int? = null,
  /** Dénivelé négatif du segment, en mètres. */
  val elevationDownMeters: Int? = null,
  /** Voie à péage. */
  val toll: Boolean = false,
  /** Restriction d'accès OpenStreetMap, marquée « expérimentale » côté MOTIS. */
  val accessRestriction: String? = null,
)
