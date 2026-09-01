package io.github.mgdx.escale.core.model

/**
 * Système de libre-service emprunté sur une portion, décalque du schéma `Rental` de l'OpenAPI
 * MOTIS. Marqué « expérimental » côté serveur : ses champs peuvent changer sans changement de
 * version d'API.
 */
data class RentalInfo(
  /** Identifiant du système GBFS. */
  val systemId: String,
  /** Nom public du système, à afficher (« Vélib' », « Nextbike »). Nul si le flux ne le donne pas. */
  val systemName: String?,
  val providerId: String?,
  /** Couleur de marque de l'exploitant, au format `#RRGGBB`. Nulle ou vide si non publiée. */
  val color: String?,
  /** Site du système, pour l'attribution. */
  val url: String?,
  /** Station de prise. Nulle pour un véhicule en free-floating. */
  val fromStationName: String?,
  /** Station de retour. Nulle pour un véhicule en free-floating. */
  val toStationName: String?,
  /** Lien profond vers l'application de l'exploitant. Ouvert en intent externe, jamais en WebView. */
  val rentalUriAndroid: String?,
  val formFactor: RentalFormFactor?,
  val propulsionType: RentalPropulsionType?,
  /** Contrainte de retour, à afficher en clair à l'usager (SPEC.md § 5.3). */
  val returnConstraint: RentalReturnConstraint?,
)
