package io.github.mgdx.escale.core.model

/**
 * Onglet de l'écran de résultats (SPEC.md § 5.2). Une valeur, une requête `plan`.
 *
 * Il n'y a délibérément pas de catégorie « libre-service » : un vélo partagé relève de [BIKE], un
 * rabattement vers une gare relève de [TRANSIT].
 */
enum class JourneyCategory {
  TRANSIT,
  CAR,
  BIKE,
  WALK,
}
