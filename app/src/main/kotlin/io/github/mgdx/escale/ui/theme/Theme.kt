package io.github.mgdx.escale.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
  primary = LightPrimary,
  onPrimary = LightOnPrimary,
  primaryContainer = LightPrimaryContainer,
  onPrimaryContainer = LightOnPrimaryContainer,
  secondary = LightSecondary,
  onSecondary = LightOnSecondary,
  secondaryContainer = LightSecondaryContainer,
  onSecondaryContainer = LightOnSecondaryContainer,
  tertiary = LightTertiary,
  onTertiary = LightOnTertiary,
  tertiaryContainer = LightTertiaryContainer,
  onTertiaryContainer = LightOnTertiaryContainer,
  error = LightError,
  onError = LightOnError,
  errorContainer = LightErrorContainer,
  onErrorContainer = LightOnErrorContainer,
  background = LightBackground,
  onBackground = LightOnBackground,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
  inverseSurface = LightInverseSurface,
  inverseOnSurface = LightInverseOnSurface,
  inversePrimary = LightInversePrimary,
)

private val DarkColors = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = DarkOnSecondary,
  secondaryContainer = DarkSecondaryContainer,
  onSecondaryContainer = DarkOnSecondaryContainer,
  tertiary = DarkTertiary,
  onTertiary = DarkOnTertiary,
  tertiaryContainer = DarkTertiaryContainer,
  onTertiaryContainer = DarkOnTertiaryContainer,
  error = DarkError,
  onError = DarkOnError,
  errorContainer = DarkErrorContainer,
  onErrorContainer = DarkOnErrorContainer,
  background = DarkBackground,
  onBackground = DarkOnBackground,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
  inverseSurface = DarkInverseSurface,
  inverseOnSurface = DarkInverseOnSurface,
  inversePrimary = DarkInversePrimary,
)

/**
 * Le thème effectivement appliqué : sombre, ou clair.
 *
 * **Ce n'est pas `isSystemInDarkTheme()`**, qui ne répond qu'à « l'appareil est-il en mode nuit ? ».
 * Tant que le réglage de SPEC.md § 5.6 vaut « comme le système », les deux se confondent ; dès
 * qu'il vaut « clair » ou « sombre », ils divergent. Or tout ce qui ne se peint pas avec une
 * couleur du `ColorScheme` — la feuille de style de la carte, les teintes d'écart à l'horaire —
 * doit choisir sa variante lui-même : interroger le système le ferait suivre l'appareil pendant
 * que le reste de l'écran suit le réglage, ce qui donnait une carte claire sous une interface
 * sombre.
 *
 * La valeur par défaut ne sert qu'aux aperçus Compose, qui ne passent pas tous par [EscaleTheme].
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Thème Material 3 de l'application (SPEC.md § 3).
 *
 * Les couleurs dynamiques sont préférées dès Android 12 : elles respectent le fond d'écran choisi
 * par l'usager. En deçà, on retombe sur la palette de marque de SPEC.md § 1.1, déclinée en clair
 * et en sombre.
 *
 * [darkTheme] est publié dans [LocalDarkTheme] : c'est par là, et non par le système, que le reste
 * de l'interface apprend quelle variante est à l'écran.
 */
@Composable
fun EscaleTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColors

    else -> LightColors
  }

  CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = EscaleTypography,
      content = content,
    )
  }
}
