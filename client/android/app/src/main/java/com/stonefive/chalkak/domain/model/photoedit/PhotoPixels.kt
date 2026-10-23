package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 자동 보정 분석에 쓰는 사진 픽셀. (x, y)의 색은 `argb[y * width + x]`이고, 한 값에 A·R·G·B가 8비트씩 들어 있다.
 * data 레이어가 EXIF 회전 정보까지 반영해, 화면에 보이는 방향 그대로의 픽셀을 넘겨야 한다.
 */
class PhotoPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height)
    }

    /**
     * [edit]대로 회전·자르기한 사진을 긴 변 [maxLongEdge] 이하로 줄여 만든다. 분석용이라 보간 없이
     * 결과 픽셀마다 원본에서 가장 가까운 픽셀 하나를 고른다.
     */
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
