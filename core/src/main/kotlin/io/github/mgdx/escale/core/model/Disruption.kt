package io.github.mgdx.escale.core.model

import java.time.Instant

/** Gravité d'une perturbation, décalque du schéma `AlertSeverityLevel` de l'OpenAPI MOTIS. */
enum class DisruptionSeverity {
  UNKNOWN_SEVERITY,
  INFO,
  WARNING,
  SEVERE,
}

/** Cause d'une perturbation, décalque du schéma `AlertCause`. */
enum class DisruptionCause {
  UNKNOWN_CAUSE,
  OTHER_CAUSE,
  TECHNICAL_PROBLEM,
  STRIKE,
  DEMONSTRATION,
  ACCIDENT,
  HOLIDAY,
  WEATHER,
  MAINTENANCE,
  CONSTRUCTION,
  POLICE_ACTIVITY,
  MEDICAL_EMERGENCY,
  SPECIAL_EVENT,
}

/** Effet d'une perturbation sur le service, décalque du schéma `AlertEffect`. */
enum class DisruptionEffect {
  NO_SERVICE,
  REDUCED_SERVICE,
  SIGNIFICANT_DELAYS,
  DETOUR,
  ADDITIONAL_SERVICE,
  MODIFIED_SERVICE,
  OTHER_EFFECT,
  UNKNOWN_EFFECT,
  STOP_MOVED,
  NO_EFFECT,
  ACCESSIBILITY_ISSUE,
}

/**
 * Intervalle de validité d'une perturbation, décalque du schéma `TimeRange`.
 *
 * Une borne nulle signifie « depuis toujours » ou « jusqu'à nouvel ordre » : l'API autorise
 * explicitement l'un ou l'autre, jamais les deux.
 */
data class TimeWindow(val start: Instant?, val end: Instant?)

/**
 * Une perturbation du réseau, décalque du schéma `Alert` de l'OpenAPI MOTIS.
 *
 * Les champs de synthèse vocale (`ttsHeaderText`, `ttsDescriptionText`) et l'illustration ne sont
 * pas repris : l'application lit le texte simple, que TalkBack sait déjà énoncer.
 *
 * **[headerText] et [descriptionText] sont du texte, jamais du balisage.** Certains réseaux — celui
 * d'Île-de-France notamment — publient leurs messages en HTML ; c'est le mapping de `:data` qui les
 * réduit, à l'entrée, avec `io.github.mgdx.escale.core.text.HtmlText`. Un écran affiche donc ces
 * deux champs tels quels : il n'a rien à en retirer, et surtout rien à en interpréter (SPEC.md § 2,
 * aucune `WebView`).
 */
data class Disruption(
  /** Titre, mis en évidence à l'affichage. Texte simple. */
  val headerText: String,
  /** Corps du message, replié par défaut. Texte simple. */
  val descriptionText: String,
  val severity: DisruptionSeverity = DisruptionSeverity.UNKNOWN_SEVERITY,
  val cause: DisruptionCause = DisruptionCause.UNKNOWN_CAUSE,
  val effect: DisruptionEffect = DisruptionEffect.UNKNOWN_EFFECT,
  /**
   * Périodes pendant lesquelles le service est effectivement perturbé (`impactPeriod`), et non
   * celles pendant lesquelles le message doit être montré (`communicationPeriod`).
   */
  val periods: List<TimeWindow> = emptyList(),
  /** Lien vers l'information détaillée du transporteur. Ouvert en intent externe, jamais en WebView. */
  val url: String? = null,
)
