package com.stonefive.chalkak.feature.photoedit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.stonefive.chalkak.domain.model.PhotoAdjustments
import com.stonefive.chalkak.domain.model.PhotoEdit
import kotlin.math.hypot

/** 인코더와 같은 계수로 미리보기 색을 맞춘다. */
fun PhotoEdit.toColorFilter(): ColorFilter? = colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/** 인코더와 같은 방식으로 결과 영역 위에 비네트를 덧그린다. 이 노드의 크기가 결과 이미지 영역이어야 한다. */
fun Modifier.vignette(adjustments: PhotoAdjustments): Modifier {
    if (adjustments.vignette == 0f) return this
    val edgeColor = if (adjustments.vignette > 0f) Color.Black else Color.White
    return drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.radialGradient(
                PhotoAdjustments.VIGNETTE_INNER_RADIUS to edgeColor.copy(alpha = 0f),
                1f to edgeColor.copy(alpha = adjustments.vignetteAlpha),
                center = center,
                radius = hypot(size.width, size.height) / 2f,
            ),
        )
    }
}
