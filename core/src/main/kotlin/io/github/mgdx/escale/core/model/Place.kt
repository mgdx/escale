package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Extrémité d'une portion de trajet, décalque du schéma `Place` de l'OpenAPI MOTIS.
 *
 * L'API porte deux couples d'heures (`arrival` / `departure` et leurs jumeaux `scheduled*`). Une
 * extrémité de portion n'en utilise qu'une à la fois : le mapping retient l'heure pertinente dans
 * [time] et son horaire théorique dans [scheduledTime]. Un arrêt intermédiaire, lui, garde les
 * deux et se représente par un [StopVisit].
 */
data class Place(
  val name: String,
  val coordinates: LatLon,
  /** Identifiant d'arrêt. Nul pour une adresse ou une coordonnée brute. */
  val stopId: String?,
  /** Quai ou voie, mis à jour en temps réel quand le serveur le sait. Nul sinon. */
  val track: String?,
  /** Heure prévue par la base horaire. */
  val scheduledTime: Instant,
  /** Heure effective, égale à [scheduledTime] en l'absence de donnée temps réel. */
  val time: Instant,
  /** Niveau OpenStreetMap, utile en gare souterraine. Nul si la donnée manque. */
  val level: Double? = null,
)
