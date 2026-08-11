package com.stonefive.chalkak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val ChalkakLightColorScheme = lightColorScheme(
    primary = LightChalkakColors.actionPrimary,
    onPrimary = LightChalkakColors.onActionPrimary,
    primaryContainer = LightChalkakColors.surfaceElevated,
    onPrimaryContainer = LightChalkakColors.textPrimary,
    inversePrimary = LightChalkakColors.bottomBar,
    secondary = LightChalkakColors.bottomBar,
    onSecondary = LightChalkakColors.onActionPrimary,
    secondaryContainer = LightChalkakColors.background,
    onSecondaryContainer = LightChalkakColors.textPrimary,
    tertiary = LightChalkakColors.bottomBar,
    onTertiary = LightChalkakColors.onActionPrimary,
    tertiaryContainer = LightChalkakColors.background,
    onTertiaryContainer = LightChalkakColors.textPrimary,
    background = LightChalkakColors.background,
    onBackground = LightChalkakColors.textPrimary,
    surface = LightChalkakColors.surface,
    onSurface = LightChalkakColors.textPrimary,
    surfaceVariant = LightChalkakColors.surfaceElevated,
    onSurfaceVariant = LightChalkakColors.textSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = LightChalkakColors.actionPrimary,
    inverseOnSurface = LightChalkakColors.background,
    error = LightChalkakColors.error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = LightChalkakColors.border,
    outlineVariant = LightChalkakColors.border,
    scrim = LightChalkakColors.scrim,
    surfaceDim = LightChalkakColors.background,
    surfaceBright = LightChalkakColors.surfaceElevated,
    surfaceContainerLowest = LightChalkakColors.surfaceElevated,
    surfaceContainerLow = LightChalkakColors.background,
    surfaceContainer = LightChalkakColors.background,
    surfaceContainerHigh = LightChalkakColors.background,
    surfaceContainerHighest = LightChalkakColors.surfaceElevated,
)

private val LocalChalkakColors = staticCompositionLocalOf { LightChalkakColors }
private val LocalChalkakTypography = staticCompositionLocalOf { DefaultChalkakTypography }
private val LocalChalkakShapes = staticCompositionLocalOf { DefaultChalkakShapes }
private val LocalChalkakSpacing = staticCompositionLocalOf { DefaultChalkakSpacing }

@Composable
fun ChalkakTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalChalkakColors provides LightChalkakColors,
        LocalChalkakTypography provides DefaultChalkakTypography,
        LocalChalkakShapes provides DefaultChalkakShapes,
        LocalChalkakSpacing provides DefaultChalkakSpacing,
    ) {
        MaterialTheme(
            colorScheme = ChalkakLightColorScheme,
            typography = ChalkakMaterialTypography,
            shapes = ChalkakMaterialShapes,
            content = content,
        )
    }
}

object ChalkakTheme {
    val colors: ChalkakColors
        @Composable
        @ReadOnlyComposable
        get() = LocalChalkakColors.current

    val typography: ChalkakTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalChalkakTypography.current

    val shapes: ChalkakShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalChalkakShapes.current

    val spacing: ChalkakSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalChalkakSpacing.current
}
