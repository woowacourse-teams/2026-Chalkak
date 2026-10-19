package com.stonefive.chalkak.domain.model

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** EXIF 방향을 반영한 사진의 ARGB 픽셀. 행 우선이다. 만든 뒤에는 배열을 바꾸지 않는다. */
class PhotoPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height)
    }

    /**
     * [edit]의 회전·자르기를 적용한 결과를 긴 변 [maxLongEdge] 이하로 가장 가까운 픽셀만 골라 만든다.
     * 인코더와 같은 변환(자르기 중심을 결과 가운데로 옮긴 뒤 시계 방향 회전)을 거꾸로 따라간다.
     */
    fun sampled(
        edit: PhotoEdit,
        maxLongEdge: Int,
    ): PhotoPixels {
        val isTurned = edit.quarterTurns.mod(2) == 1
        val outputWidth = edit.crop.width * (if (isTurned) height else width)
        val outputHeight = edit.crop.height * (if (isTurned) width else height)
        val scale = minOf(1f, maxLongEdge / max(outputWidth, outputHeight))
        val sampleWidth = (outputWidth * scale).roundToInt().coerceAtLeast(1)
        val sampleHeight = (outputHeight * scale).roundToInt().coerceAtLeast(1)
        val radians = Math.toRadians(edit.rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val centerX = edit.crop.centerX * width
        val centerY = edit.crop.centerY * height

        val sampled = IntArray(sampleWidth * sampleHeight) { index ->
            val dx = ((index % sampleWidth) + 0.5f) / sampleWidth * outputWidth - outputWidth / 2f
            val dy = ((index / sampleWidth) + 0.5f) / sampleHeight * outputHeight - outputHeight / 2f
            val x = floor(centerX + cos * dx + sin * dy).toInt().coerceIn(0, width - 1)
            val y = floor(centerY - sin * dx + cos * dy).toInt().coerceIn(0, height - 1)
            argb[y * width + x]
        }
        return PhotoPixels(sampleWidth, sampleHeight, sampled)
    }
}
