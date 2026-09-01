package io.github.mgdx.escale.core.format

import kotlin.math.roundToLong

/** Unité de distance retenue par [FormattedDistance]. */
enum class DistanceUnit {
  METERS,
  KILOMETERS,
}

/**
 * Une distance arrondie et son unité, prête à être injectée dans un libellé de `strings.xml`.
 *
 * Comme [FormattedDuration], ce type ne rend pas de chaîne : `:app` compose « 450 m » ou « 2,3 km »
 * avec les ressources de la langue courante.
 */
data class FormattedDistance(
  val value: Double,
  val unit: DistanceUnit,
  /** Nombre de décimales à afficher. Zéro pour les mètres et les longues distances. */
  val decimals: Int,
) {
  companion object {
    private const val METERS_IN_KILOMETER = 1000.0
    private const val ROUNDING_STEP_METERS = 10.0
    private const val PRECISE_ROUNDING_LIMIT_METERS = 100.0
    private const val DECIMAL_KILOMETER_LIMIT = 10.0

    /**
     * Arrondit [meters] selon la précision utile à un piéton :
     * - moins de 100 m : au mètre près, la personne compte ses pas ;
     * - de 100 m à 1 km : à la dizaine de mètres, l'unité n'a plus de sens ;
     * - de 1 km à 10 km : au dixième de kilomètre ;
     * - au-delà : au kilomètre.
     */
    fun of(meters: Double): FormattedDistance {
      val safeMeters = if (meters.isFinite() && meters > 0.0) meters else 0.0
      return when {
        safeMeters < PRECISE_ROUNDING_LIMIT_METERS ->
          FormattedDistance(safeMeters.roundToLong().toDouble(), DistanceUnit.METERS, decimals = 0)

        safeMeters < METERS_IN_KILOMETER -> {
          val rounded = (safeMeters / ROUNDING_STEP_METERS).roundToLong() * ROUNDING_STEP_METERS
          FormattedDistance(rounded, DistanceUnit.METERS, decimals = 0)
        }

        safeMeters < DECIMAL_KILOMETER_LIMIT * METERS_IN_KILOMETER -> {
          val kilometers = (safeMeters / METERS_IN_KILOMETER * ROUNDING_STEP_METERS).roundToLong() /
            ROUNDING_STEP_METERS
          FormattedDistance(kilometers, DistanceUnit.KILOMETERS, decimals = 1)
        }

        else -> FormattedDistance(
          (safeMeters / METERS_IN_KILOMETER).roundToLong().toDouble(),
          DistanceUnit.KILOMETERS,
          decimals = 0,
        )
      }
    }
  }
}
