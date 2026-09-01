package io.github.mgdx.escale.ui.theme

import androidx.compose.ui.graphics.Color

// Palette de marque d'Escale (SPEC.md § 1.1) : teal avant l'escale, corail après, anneau sombre.
// Elle ne sert que de repli quand les couleurs dynamiques ne sont pas disponibles, c'est-à-dire
// avant Android 12 ou quand l'usager les a désactivées.

/** Accent de l'application. */
val BrandTeal = Color(0xFF1D9E75)

/** Anneau de l'icône : le teal sombre, assez contrasté pour porter du texte blanc. */
val BrandTealDark = Color(0xFF0F6E56)

/** Secondaire : le corail de l'après-escale. */
val BrandCoral = Color(0xFFD85A30)

/** Fond clair de l'icône, repris comme fond de l'application en thème clair. */
val BrandMint = Color(0xFFE1F5EE)

// --- Thème clair ------------------------------------------------------------------------------
internal val LightPrimary = BrandTealDark
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFA6F2D5)
internal val LightOnPrimaryContainer = Color(0xFF002018)
internal val LightSecondary = Color(0xFF9A4322)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFFFDBCF)
internal val LightOnSecondaryContainer = Color(0xFF370D00)
internal val LightTertiary = Color(0xFF3D6373)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFC1E8FB)
internal val LightOnTertiaryContainer = Color(0xFF001F29)
internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)
internal val LightBackground = BrandMint
internal val LightOnBackground = Color(0xFF171D1B)
internal val LightSurface = Color(0xFFF5FBF7)
internal val LightOnSurface = Color(0xFF171D1B)
internal val LightSurfaceVariant = Color(0xFFDBE5DF)
internal val LightOnSurfaceVariant = Color(0xFF3F4945)
internal val LightOutline = Color(0xFF6F7975)
internal val LightOutlineVariant = Color(0xFFBFC9C3)
internal val LightInverseSurface = Color(0xFF2B322F)
internal val LightInverseOnSurface = Color(0xFFECF2EE)
internal val LightInversePrimary = Color(0xFF8AD6B9)

// --- Thème sombre -----------------------------------------------------------------------------
internal val DarkPrimary = Color(0xFF8AD6B9)
internal val DarkOnPrimary = Color(0xFF00382A)
internal val DarkPrimaryContainer = Color(0xFF00513E)
internal val DarkOnPrimaryContainer = Color(0xFFA6F2D5)
internal val DarkSecondary = Color(0xFFFFB59B)
internal val DarkOnSecondary = Color(0xFF5A1B00)
internal val DarkSecondaryContainer = Color(0xFF7B2D0D)
internal val DarkOnSecondaryContainer = Color(0xFFFFDBCF)
internal val DarkTertiary = Color(0xFFA5CCDF)
internal val DarkOnTertiary = Color(0xFF073544)
internal val DarkTertiaryContainer = Color(0xFF244C5B)
internal val DarkOnTertiaryContainer = Color(0xFFC1E8FB)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkBackground = Color(0xFF0E1513)
internal val DarkOnBackground = Color(0xFFDDE5E0)
internal val DarkSurface = Color(0xFF0E1513)
internal val DarkOnSurface = Color(0xFFDDE5E0)
internal val DarkSurfaceVariant = Color(0xFF3F4945)
internal val DarkOnSurfaceVariant = Color(0xFFBFC9C3)
internal val DarkOutline = Color(0xFF89938F)
internal val DarkOutlineVariant = Color(0xFF3F4945)
internal val DarkInverseSurface = Color(0xFFDDE5E0)
internal val DarkInverseOnSurface = Color(0xFF2B322F)
internal val DarkInversePrimary = BrandTealDark
