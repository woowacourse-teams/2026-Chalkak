package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 원본 픽셀 좌표와 회전·자르기 결과 좌표 사이의 변환. 자르기 중심을 결과 가운데로 옮긴 뒤 시계 방향으로 돌린다. */
class PhotoEditTransform(
    edit: PhotoEdit,
    sourceWidth: Int,
    sourceHeight: Int,
) {
    val outputWidth: Int
    val outputHeight: Int

    private val rotation = PhotoRotation.of(edit.rotationDegrees)
    private val cropCenterX = edit.crop.centerX * sourceWidth
    private val cropCenterY = edit.crop.centerY * sourceHeight

    init {
        val isTurned = edit.quarterTurns.mod(2) == 1
        val turnedWidth = if (isTurned) sourceHeight else sourceWidth
        val turnedHeight = if (isTurned) sourceWidth else sourceHeight
        outputWidth = (edit.crop.width * turnedWidth).roundToInt().coerceIn(1, turnedWidth)
        outputHeight = (edit.crop.height * turnedHeight).roundToInt().coerceIn(1, turnedHeight)
    }

    /** 원본 → 결과 변환. `android.graphics.Matrix.setValues`와 같은 순서의 3×3 행렬이다. */
    fun matrixValues(): FloatArray {
        val cos = rotation.cos
        val sin = rotation.sin
        return floatArrayOf(
            cos,
            -sin,
            outputWidth / 2f - cos * cropCenterX + sin * cropCenterY,
            sin,
            cos,
            outputHeight / 2f - sin * cropCenterX - cos * cropCenterY,
            0f,
            0f,
            1f,
        )
    }

    fun sourceX(
        outputX: Float,
        outputY: Float,
    ): Float = cropCenterX + rotation.cos * (outputX - outputWidth / 2f) + rotation.sin * (outputY - outputHeight / 2f)

    fun sourceY(
        outputX: Float,
        outputY: Float,
    ): Float = cropCenterY - rotation.sin * (outputX - outputWidth / 2f) + rotation.cos * (outputY - outputHeight / 2f)
}

/** 90° 단위 회전에서 생기는 아주 작은 cos·sin 오차는 0으로 맞춰 픽셀 경계가 어긋나지 않게 한다. */
class PhotoRotation private constructor(
    val cos: Float,
    val sin: Float,
) {
    companion object {
        private const val SNAP_EPSILON = 1e-6

        fun of(degrees: Float): PhotoRotation {
            val radians = Math.toRadians(degrees.toDouble())
            return PhotoRotation(cos = snap(cos(radians)), sin = snap(sin(radians)))
        }

        private fun snap(value: Double): Float = if (abs(value) < SNAP_EPSILON) 0f else value.toFloat()
    }
}
