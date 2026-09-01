package io.github.mgdx.escale.ui.theme

import androidx.compose.material3.Typography

/**
 * Typographie de l'application.
 *
 * Volontairement celle de Material 3, sans police embarquée : SPEC.md § 2 vise un APK léger, et la
 * police système suit les réglages d'accessibilité de l'appareil, jusqu'à 200 % d'agrandissement
 * (SPEC.md § 9).
 */
val EscaleTypography = Typography()
