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
  /**
   * Les perturbations qui concernent **ce point-là**, et non la portion entière.
   *
   * Le schéma `Place` porte son propre tableau `alerts` : `/api/v6/trip` s'en sert pour dire qu'un
   * arrêt précis de la desserte n'est pas desservi, qu'un quai a changé, qu'un ascenseur est en
   * panne. Rangées avec celles de la portion, ces perturbations se noieraient dans le bandeau de la
   * course — et un « arrêt non desservi » qui se noie, c'est un usager qui reste sur le quai.
   *
   * La valeur par défaut n'existe que pour la compatibilité de source des appelants qui construisent
   * un [Place] à la main — tests et aperçus. Le mapping de `:data`, lui, la renseigne toujours,
   * exactement comme `RentalAvailability.kind`.
   */
  val alerts: List<Disruption> = emptyList(),
)
