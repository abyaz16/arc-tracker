package com.example.mytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing, radius and elevation. Three elevation levels, no more. */
object Space {
    val s1: Dp = 2.dp
    val s2: Dp = 4.dp
    val s3: Dp = 8.dp
    val s4: Dp = 12.dp
    val s5: Dp = 16.dp
    val s6: Dp = 20.dp
    val s7: Dp = 24.dp
    val s8: Dp = 32.dp
    val s9: Dp = 40.dp
    val gutter: Dp = 20.dp
}

object Radius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val full: Dp = 999.dp
}

object Elevation {
    val e0: Dp = 0.dp
    val e1: Dp = 2.dp
    val e2: Dp = 8.dp
}

val LocalCadence = staticCompositionLocalOf { LightColors }

/** Shorthand so screens read `C.accent` rather than `LocalCadence.current.accent`. */
val C: CadenceColors
    @Composable get() = LocalCadence.current

/**
 * Deliberately not using Material You dynamic colour: this app's palette is a
 * deliberate, contrast-checked system, and letting the wallpaper recolour a
 * chart would break the rule that a colour always means the same thing.
 */
@Composable
fun MyTrackerTheme(
    /** "system", "light" or "dark" — the user's stored choice. */
    mode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val cadence = if (darkTheme) DarkColors else LightColors

    // Material components (bottom sheets, ripples) still need a scheme to read from.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = cadence.accent,
            onPrimary = cadence.onAccent,
            background = cadence.canvas,
            onBackground = cadence.textPrimary,
            surface = cadence.surface,
            onSurface = cadence.textPrimary,
            surfaceVariant = cadence.surfaceAlt,
            outline = cadence.line,
            error = cadence.overdue,
        )
    } else {
        lightColorScheme(
            primary = cadence.accent,
            onPrimary = cadence.onAccent,
            background = cadence.canvas,
            onBackground = cadence.textPrimary,
            surface = cadence.surface,
            onSurface = cadence.textPrimary,
            surfaceVariant = cadence.surfaceAlt,
            outline = cadence.line,
            error = cadence.overdue,
        )
    }

    CompositionLocalProvider(LocalCadence provides cadence) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
