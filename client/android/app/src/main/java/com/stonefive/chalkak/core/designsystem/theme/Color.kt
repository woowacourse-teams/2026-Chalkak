package com.stonefive.chalkak.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val ChalkakBackground = Color(0xFFF7F6F3)
val ChalkakBottomBar = Color(0xFF8C8479)
val ChalkakAction = Color(0xFF2B2724)
val ChalkakStroke = Color(0x26888888)
val ChalkakWhite = Color(0xFFFFFFFF)

@Immutable
data class ChalkakColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val bottomBar: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnImage: Color,
    val border: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val scrim: Color,
    val error: Color,
)

internal val LightChalkakColors = ChalkakColors(
    background = ChalkakBackground,
    surface = ChalkakBackground,
    surfaceElevated = ChalkakWhite,
    bottomBar = ChalkakBottomBar,
    textPrimary = ChalkakAction,
    textSecondary = ChalkakAction.copy(alpha = 0.68f),
    textMuted = ChalkakAction.copy(alpha = 0.48f),
    textOnImage = ChalkakWhite,
    border = ChalkakStroke,
    actionPrimary = ChalkakAction,
    onActionPrimary = ChalkakWhite,
    iconPrimary = ChalkakAction,
    iconSecondary = ChalkakAction.copy(alpha = 0.62f),
    scrim = Color.Black.copy(alpha = 0.4f),
    error = Color(0xFFBA1A1A),
)
