package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.model.DisruptionCause
import io.github.mgdx.escale.core.model.DisruptionEffect
import io.github.mgdx.escale.core.model.DisruptionSeverity
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalPropulsionType
import io.github.mgdx.escale.core.model.RentalReturnConstraint
import io.github.mgdx.escale.core.model.StepDirection
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.model.WheelchairAccess

// Traduction des énumérations de l'API en énumérations de domaine.
//
// Une valeur inconnue ne fait jamais échouer la lecture : elle se replie sur la valeur neutre du
// domaine. L'API MOTIS ajoute des valeurs sans préavis, et une réponse partiellement comprise vaut
// mieux qu'un écran d'erreur.

/**
 * Modes que l'API émet encore mais que le domaine ne reprend pas : le serveur les a remplacés,
 * on applique la même substitution que lui (docs/architecture.md § 4).
 */
private val DEPRECATED_MODES = mapOf(
  "METRO" to TransitMode.SUBWAY,
  "AREAL_LIFT" to TransitMode.AERIAL_LIFT,
  "CABLE_CAR" to TransitMode.AERIAL_LIFT,
  "REGIONAL_FAST_RAIL" to TransitMode.REGIONAL_RAIL,
)

/** Un mode inconnu, ou un mode de mise au point `DEBUG_*`, devient [TransitMode.OTHER]. */
internal fun transitModeOf(raw: String?): TransitMode =
  enumOrNull<TransitMode>(raw) ?: DEPRECATED_MODES[raw] ?: TransitMode.OTHER

/** Nul quand le transporteur ne publie pas l'information, ce qui ne veut pas dire « inaccessible ». */
internal fun wheelchairAccessOf(raw: String?): WheelchairAccess? = enumOrNull<WheelchairAccess>(raw)

internal fun rentalFormFactorOf(raw: String?): RentalFormFactor? = enumOrNull<RentalFormFactor>(raw)

internal fun rentalPropulsionTypeOf(raw: String?): RentalPropulsionType? = enumOrNull<RentalPropulsionType>(raw)

internal fun rentalReturnConstraintOf(raw: String?): RentalReturnConstraint? = enumOrNull<RentalReturnConstraint>(raw)

internal fun disruptionSeverityOf(raw: String?): DisruptionSeverity =
  enumOrNull<DisruptionSeverity>(raw) ?: DisruptionSeverity.UNKNOWN_SEVERITY

internal fun disruptionCauseOf(raw: String?): DisruptionCause =
  enumOrNull<DisruptionCause>(raw) ?: DisruptionCause.UNKNOWN_CAUSE

internal fun disruptionEffectOf(raw: String?): DisruptionEffect =
  enumOrNull<DisruptionEffect>(raw) ?: DisruptionEffect.UNKNOWN_EFFECT

/** Une manœuvre inconnue devient « continuer » : c'est l'instruction la moins trompeuse. */
internal fun stepDirectionOf(raw: String?): StepDirection = enumOrNull<StepDirection>(raw) ?: StepDirection.CONTINUE

/**
 * Cherche la valeur par son nom, sans lever d'exception : `enumValueOf` en lèverait une pour toute
 * valeur ajoutée par une version plus récente du serveur.
 */
private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
  raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
