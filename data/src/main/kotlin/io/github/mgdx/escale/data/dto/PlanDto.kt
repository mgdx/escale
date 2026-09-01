package io.github.mgdx.escale.data.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `GET /api/v6/plan`.
 *
 * Ces DTO ne sortent jamais de `:data` (docs/architecture.md § 1 et § 2) : c'est ce découplage qui
 * absorbe les changements de l'API MOTIS, fréquents sur les champs marqués « Experimental ».
 *
 * Tous les champs sont facultatifs et tous les types énumérés sont lus en `String` : un serveur
 * plus récent qui ajoute une valeur d'énumération ne doit pas faire échouer la lecture de la
 * réponse entière. La traduction en type de domaine se fait dans `:data.mapper`, avec un repli
 * documenté pour toute valeur inconnue.
 *
 * Les champs `requestParameters` et `debugOutput` de la réponse ne sont volontairement pas lus :
 * `requestParameters` renvoie la requête de l'usager, donc ses coordonnées, et SPEC.md § 11
 * interdit qu'elles circulent au-delà du strict nécessaire.
 */
@Serializable
internal data class PlanResponseDto(
  /** Trajets dépendant des horaires. */
  val itineraries: List<PlanItineraryDto> = emptyList(),
  /** Trajets sans horaire : marche, vélo, voiture, libre-service. */
  val direct: List<PlanItineraryDto> = emptyList(),
  val previousPageCursor: String? = null,
  val nextPageCursor: String? = null,
)

/** Décalque du schéma `Itinerary`. */
@Serializable
internal data class PlanItineraryDto(
  /** Durée du trajet en secondes. */
  val duration: Long = 0,
  val startTime: String? = null,
  val endTime: String? = null,
  val transfers: Int = 0,
  /** Identifiant opaque, marqué « Experimental » : son format peut changer sans préavis. */
  val id: String? = null,
  val legs: List<PlanLegDto> = emptyList(),
)
