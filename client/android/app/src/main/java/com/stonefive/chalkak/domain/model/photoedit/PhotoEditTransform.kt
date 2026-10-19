package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 회전·자르기를 할 때 원본 사진(source)의 픽셀이 결과 사진(output)의 어디로 가는지 계산한다.
 * 두 좌표 모두 왼쪽 위가 (0, 0)인 픽셀 좌표다.
 *
 * 업로드 인코더는 원본 → 결과 방향([sourceToOutputMatrix])으로 그리고, 자동 보정 분석([PhotoPixels.sampled])은
 * 결과의 각 픽셀이 원본 어디에서 왔는지([sourceX], [sourceY]) 거꾸로 찾는다. 둘이 이 클래스 하나를 같이 써야
 * 분석한 영역과 업로드되는 영역이 같아진다.
 *
 * 예: 4000×3000 사진, quarterTurns = 1, crop = `CropWindow(0.5f, 0.75f, 0.5f, 1f)`(돌린 사진의 왼쪽 절반)
 * → 결과는 1500×4000이고, 결과 가운데 (750, 2000)은 원본 (2000, 2250)에서, 결과 왼쪽 위 (0, 0)은 원본 (0, 3000)에서 온다.
 */
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

    /**
     * 원본 좌표를 결과 좌표로 옮기는 3×3 행렬. `android.graphics.Matrix.setValues`에 그대로 넣을 수 있는 순서다.
     * 자르기 중심을 (0, 0)으로 옮기고 → 시계 방향으로 돌리고 → 결과 사진 가운데로 옮긴다.
     */
    fun sourceToOutputMatrix(): FloatArray {
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

    /** 결과 좌표 (outputX, outputY)가 원본에서 어느 x에서 왔는지. [sourceToOutputMatrix]의 역변환이다. */
    fun sourceX(
        outputX: Float,
        outputY: Float,
    ): Float = cropCenterX + rotation.cos * (outputX - outputWidth / 2f) + rotation.sin * (outputY - outputHeight / 2f)

    fun sourceY(
        outputX: Float,
        outputY: Float,
    ): Float = cropCenterY - rotation.sin * (outputX - outputWidth / 2f) + rotation.cos * (outputY - outputHeight / 2f)
}

/**
 * 회전 각의 cos·sin. 90°를 계산하면 cos가 0이 아니라 6e-17 같은 값이 나와 픽셀 경계에서 한 칸 어긋날 수 있어서,
 * 아주 작은 값은 0으로 맞춘다.
 */
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
