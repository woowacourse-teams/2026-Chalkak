package com.stonefive.chalkak.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class ChalkakShapes(
    val small: CornerBasedShape,
    val button: CornerBasedShape,
    val input: CornerBasedShape,
    val photoCard: CornerBasedShape,
    val large: CornerBasedShape,
    val sheet: CornerBasedShape,
    val pill: CornerBasedShape,
)

val DefaultChalkakShapes = ChalkakShapes(
    small = RoundedCornerShape(7.dp),
    button = RoundedCornerShape(12.dp),
    input = RoundedCornerShape(12.dp),
    photoCard = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    sheet = RoundedCornerShape(28.dp),
    pill = CircleShape,
)

val ChalkakMaterialShapes = Shapes(
    extraSmall = DefaultChalkakShapes.small,
    small = DefaultChalkakShapes.small,
    medium = DefaultChalkakShapes.button,
    large = DefaultChalkakShapes.large,
    extraLarge = DefaultChalkakShapes.sheet,
)
