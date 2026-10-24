package com.example.shaylock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * ShayLock is deliberately dark-only. The black gradient and graphite surfaces are part of the
 * product identity, so there is no dynamic-colour or light-theme path.
 */
private val InkColorScheme = darkColorScheme(
    primary = InkAccent,
    onPrimary = InkOnAccent,
    primaryContainer = InkAccentContainer,
    onPrimaryContainer = InkAccentSoft,
    inversePrimary = InkAccentStrong,

    secondary = InkAccentSoft,
    onSecondary = InkOnAccent,
    secondaryContainer = InkSurfaceHigh,
    onSecondaryContainer = InkTextPrimary,

    tertiary = InkSuccess,
    onTertiary = InkOnAccent,
    tertiaryContainer = InkSuccessContainer,
    onTertiaryContainer = InkOnSuccessContainer,

    error = InkDanger,
    onError = InkOnAccent,
    errorContainer = InkDangerContainer,
    onErrorContainer = InkOnDangerContainer,

    background = InkTop,
    onBackground = InkTextPrimary,
    surface = InkSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = InkTextSecondary,
    surfaceTint = InkAccent,

    outline = InkHairline,
    outlineVariant = InkHairlineSoft,
    inverseSurface = InkTextPrimary,
    inverseOnSurface = InkTop,
    scrim = InkScrim,
)

private val ShayLockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ShayLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InkColorScheme,
        typography = Typography,
        shapes = ShayLockShapes,
        content = content,
    )
}
