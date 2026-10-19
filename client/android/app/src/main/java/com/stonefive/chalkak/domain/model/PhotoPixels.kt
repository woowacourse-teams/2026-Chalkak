package com.stonefive.chalkak.domain.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** EXIF 방향을 반영한 사진의 ARGB 픽셀. 행 우선이다. */
class PhotoPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height)
    }

    /** [edit]의 회전·자르기를 적용한 결과를 긴 변 [maxLongEdge] 이하로 가장 가까운 픽셀만 골라 만든다. */
    fun sampled(
        edit: PhotoEdit,
        maxLongEdge: Int,
    ): PhotoPixels {
        val transform = PhotoEditTransform(edit, width, height)
        val outputWidth = transform.outputWidth
        val outputHeight = transform.outputHeight
        val scale = minOf(1f, maxLongEdge.toFloat() / max(outputWidth, outputHeight))
        val sampleWidth = (outputWidth * scale).roundToInt().coerceAtLeast(1)
        val sampleHeight = (outputHeight * scale).roundToInt().coerceAtLeast(1)

        val sampled = IntArray(sampleWidth * sampleHeight) { index ->
            val outputX = ((index % sampleWidth) + 0.5f) / sampleWidth * outputWidth
            val outputY = ((index / sampleWidth) + 0.5f) / sampleHeight * outputHeight
            val x = floor(transform.sourceX(outputX, outputY)).toInt().coerceIn(0, width - 1)
            val y = floor(transform.sourceY(outputX, outputY)).toInt().coerceIn(0, height - 1)
            argb[y * width + x]
        }
        return PhotoPixels(sampleWidth, sampleHeight, sampled)
    }
}
