package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

data class FittedPhotoCardShape(
    private val cardShape: Shape,
    private val cardAspectRatio: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cardWidth = maxOf(size.width, size.height * cardAspectRatio)
        val cardHeight = maxOf(size.height, size.width / cardAspectRatio)
        val outline = cardShape.createOutline(Size(cardWidth, cardHeight), layoutDirection, density)
        if (outline !is Outline.Rounded) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val corners = outline.roundRect
        val left = (size.width - cardWidth) / 2f
        val top = (size.height - cardHeight) / 2f
        return Outline.Rounded(
            RoundRect(
                rect = Rect(left, top, left + cardWidth, top + cardHeight),
                topLeft = corners.topLeftCornerRadius,
                topRight = corners.topRightCornerRadius,
                bottomRight = corners.bottomRightCornerRadius,
                bottomLeft = corners.bottomLeftCornerRadius,
            ),
        )
    }
}
