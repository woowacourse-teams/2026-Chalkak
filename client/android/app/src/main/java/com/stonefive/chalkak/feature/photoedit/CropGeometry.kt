package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 자르기 영역 계산.
 *
 * 길이는 원본 긴 변을 1로 둔 단위를 쓴다. 결과 이미지 좌표계는 원본 중심을 원점으로 두고
 * [PhotoEdit.rotationDegrees]만큼 시계 방향으로 돌린 좌표계다. 자르기 영역은 이 좌표계에서 축에 정렬된
 * 사각형이므로, 원본 안에 있는지는 원본 좌표계로 되돌린 사각형의 경계 상자로 판정할 수 있다.
 */
class CropGeometry(
    imageWidth: Float,
    imageHeight: Float,
) {
    val width: Float
    val height: Float

    init {
        require(imageWidth > 0f && imageHeight > 0f) { "Image size must be positive" }
        val longEdge = max(imageWidth, imageHeight)
        width = imageWidth / longEdge
        height = imageHeight / longEdge
    }

    /** 90° 단위 회전을 적용한 원본의 가로 대 세로 비율. */
    fun turnedAspectRatio(quarterTurns: Int): Float = turnedWidth(quarterTurns) / turnedHeight(quarterTurns)

    /** 결과 이미지 좌표계에서 본 자르기 사각형. */
    fun outputRect(edit: PhotoEdit): CropRect {
        val rotation = Rotation.of(edit.rotationDegrees)
        val sourceX = edit.crop.centerX * width - width / 2f
        val sourceY = edit.crop.centerY * height - height / 2f
        return CropRect(
            centerX = rotation.cos * sourceX - rotation.sin * sourceY,
            centerY = rotation.sin * sourceX + rotation.cos * sourceY,
            width = edit.crop.width * turnedWidth(edit.quarterTurns),
            height = edit.crop.height * turnedHeight(edit.quarterTurns),
        )
    }

    fun isInside(edit: PhotoEdit): Boolean = fits(outputRect(edit), edit)

    /** 회전한 원본의 경계 상자가 뷰포트에 들어가는 배율(뷰포트 픽셀 / 단위). */
    fun fitScale(
        rotationDegrees: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Float {
        val rotation = Rotation.of(rotationDegrees)
        val boundsWidth = width * abs(rotation.cos) + height * abs(rotation.sin)
        val boundsHeight = width * abs(rotation.sin) + height * abs(rotation.cos)
        return min(viewportWidth / boundsWidth, viewportHeight / boundsHeight)
    }

    /** 원본을 벗어난 영역을 비율을 유지한 채 줄이고, 그래도 벗어나면 안쪽으로 옮긴다. */
    fun constrain(edit: PhotoEdit): CropWindow {
        val rotation = Rotation.of(edit.rotationDegrees)
        val rect = outputRect(edit)
        val halfExtentX = rect.halfExtentX(rotation)
        val halfExtentY = rect.halfExtentY(rotation)
        val scale = minOf(
            1f,
            if (halfExtentX > 0f) width / 2f / halfExtentX else 1f,
            if (halfExtentY > 0f) height / 2f / halfExtentY else 1f,
        )
        val scaled = rect.copy(width = rect.width * scale, height = rect.height * scale)
        val scaledHalfX = min(scaled.halfExtentX(rotation), width / 2f)
        val scaledHalfY = min(scaled.halfExtentY(rotation), height / 2f)
        val sourceCenterX = edit.crop.centerX * width
        val sourceCenterY = edit.crop.centerY * height
        return CropWindow(
            centerX = sourceCenterX.coerceIn(scaledHalfX, width - scaledHalfX) / width,
            centerY = sourceCenterY.coerceIn(scaledHalfY, height - scaledHalfY) / height,
            width = scaled.width / turnedWidth(edit.quarterTurns),
            height = scaled.height / turnedHeight(edit.quarterTurns),
        )
    }

    /** 현재 중심 근처에서 [aspectRatio]를 지키는 가장 큰 영역. `null`이면 원본 비율 전체를 쓴다. */
    fun largestCrop(
        edit: PhotoEdit,
        aspectRatio: Float?,
    ): CropWindow {
        val turnedWidth = turnedWidth(edit.quarterTurns)
        val turnedHeight = turnedHeight(edit.quarterTurns)
        val ratio = aspectRatio ?: (turnedWidth / turnedHeight)
        val oversizedWidth = OVERSIZED_LENGTH * max(1f, ratio)
        val oversizedHeight = oversizedWidth / ratio
        return constrain(
            edit.copy(
                crop = edit.crop.copy(
                    width = oversizedWidth / turnedWidth,
                    height = oversizedHeight / turnedHeight,
                ),
            ),
        )
    }

    /** 90° 회전. 결과 좌표계가 함께 돌아가므로 가로·세로만 바뀌고 원본 기준 중심은 그대로다. */
    fun rotateQuarter(edit: PhotoEdit): PhotoEdit {
        val rotated = edit.copy(
            quarterTurns = (edit.quarterTurns + 1).mod(QUARTER_TURNS),
            crop = edit.crop.copy(width = edit.crop.height, height = edit.crop.width),
        )
        return rotated.copy(crop = constrain(rotated))
    }

    /** 프레임 모양을 유지한 90° 회전. 사진만 돌리고 결과 사각형의 가로·세로는 그대로 두며, 원본을 벗어나면 비율을 유지한 채 줄인다. */
    fun rotateQuarterKeepingFrame(edit: PhotoEdit): PhotoEdit {
        val rect = outputRect(edit)
        val quarterTurns = (edit.quarterTurns + 1).mod(QUARTER_TURNS)
        val rotated = edit.copy(
            quarterTurns = quarterTurns,
            crop = edit.crop.copy(
                width = rect.width / turnedWidth(quarterTurns),
                height = rect.height / turnedHeight(quarterTurns),
            ),
        )
        return rotated.copy(crop = constrain(rotated))
    }

    /** 결과 좌표계 기준 이동. 원본 밖으로 나가면 경계에서 멈춘다. */
    fun move(
        edit: PhotoEdit,
        deltaX: Float,
        deltaY: Float,
    ): CropWindow {
        val rect = outputRect(edit)
        val moved = edit.copy(
            crop = cropWindowOf(
                rect.copy(centerX = rect.centerX + deltaX, centerY = rect.centerY + deltaY),
                edit,
            ),
        )
        return constrain(moved)
    }

    /**
     * 모서리를 끌어 크기를 바꾼다. 반대편 모서리는 고정된다.
     * 원본을 벗어나는 지점에서 멈추고, [lockedAspectRatio]가 있으면 그 비율을 지킨다.
     */
    fun resize(
        edit: PhotoEdit,
        corner: CropCorner,
        deltaX: Float,
        deltaY: Float,
        lockedAspectRatio: Float?,
    ): CropWindow {
        val rect = outputRect(edit)
        val anchorX = rect.centerX - corner.signX * rect.width / 2f
        val anchorY = rect.centerY - corner.signY * rect.height / 2f
        var targetWidth = rect.width + corner.signX * deltaX
        var targetHeight = rect.height + corner.signY * deltaY

        if (lockedAspectRatio != null) {
            val widthChange = abs(targetWidth - rect.width) / rect.width
            val heightChange = abs(targetHeight - rect.height) / rect.height
            if (widthChange >= heightChange) {
                targetHeight = targetWidth / lockedAspectRatio
            } else {
                targetWidth = targetHeight * lockedAspectRatio
            }
        }

        val minimumLength = MIN_CROP_FRACTION * min(width, height)
        if (lockedAspectRatio == null) {
            targetWidth = max(targetWidth, minimumLength)
            targetHeight = max(targetHeight, minimumLength)
        } else if (min(targetWidth, targetHeight) < minimumLength) {
            if (lockedAspectRatio >= 1f) {
                targetHeight = minimumLength
                targetWidth = minimumLength * lockedAspectRatio
            } else {
                targetWidth = minimumLength
                targetHeight = minimumLength / lockedAspectRatio
            }
        }

        fun anchoredRect(
            resizedWidth: Float,
            resizedHeight: Float,
        ) = CropRect(
            centerX = anchorX + corner.signX * resizedWidth / 2f,
            centerY = anchorY + corner.signY * resizedHeight / 2f,
            width = resizedWidth,
            height = resizedHeight,
        )

        fun lerp(
            from: Float,
            to: Float,
            progress: Float,
        ) = from + (to - from) * progress

        val resized = if (lockedAspectRatio != null) {
            largestFitting(edit) { progress ->
                anchoredRect(
                    lerp(rect.width, targetWidth, progress),
                    lerp(rect.height, targetHeight, progress),
                )
            }
        } else {
            // 비율이 자유로우면 한쪽 변이 경계에 닿아도 다른 변은 계속 늘어날 수 있게 축마다 따로 맞춘다.
            val widthFitted = largestFitting(edit) { progress ->
                anchoredRect(lerp(rect.width, targetWidth, progress), rect.height)
            }
            largestFitting(edit) { progress ->
                anchoredRect(widthFitted.width, lerp(rect.height, targetHeight, progress))
            }
        }
        return cropWindowOf(resized, edit)
    }

    private fun largestFitting(
        edit: PhotoEdit,
        rectAt: (Float) -> CropRect,
    ): CropRect {
        val target = rectAt(1f)
        if (fits(target, edit)) return target

        var low = 0f
        var high = 1f
        repeat(RESIZE_SEARCH_STEPS) {
            val middle = (low + high) / 2f
            if (fits(rectAt(middle), edit)) low = middle else high = middle
        }
        return rectAt(low)
    }

    private fun cropWindowOf(
        rect: CropRect,
        edit: PhotoEdit,
    ): CropWindow {
        val rotation = Rotation.of(edit.rotationDegrees)
        val sourceX = rotation.cos * rect.centerX + rotation.sin * rect.centerY
        val sourceY = -rotation.sin * rect.centerX + rotation.cos * rect.centerY
        return CropWindow(
            centerX = (sourceX + width / 2f) / width,
            centerY = (sourceY + height / 2f) / height,
            width = rect.width / turnedWidth(edit.quarterTurns),
            height = rect.height / turnedHeight(edit.quarterTurns),
        )
    }

    private fun fits(
        rect: CropRect,
        edit: PhotoEdit,
    ): Boolean {
        val rotation = Rotation.of(edit.rotationDegrees)
        val crop = cropWindowOf(rect, edit)
        val sourceX = crop.centerX * width
        val sourceY = crop.centerY * height
        val halfExtentX = rect.halfExtentX(rotation)
        val halfExtentY = rect.halfExtentY(rotation)
        return sourceX - halfExtentX >= -TOLERANCE &&
            sourceX + halfExtentX <= width + TOLERANCE &&
            sourceY - halfExtentY >= -TOLERANCE &&
            sourceY + halfExtentY <= height + TOLERANCE
    }

    private fun turnedWidth(quarterTurns: Int): Float = if (quarterTurns.mod(2) == 0) width else height

    private fun turnedHeight(quarterTurns: Int): Float = if (quarterTurns.mod(2) == 0) height else width

    private fun CropRect.halfExtentX(rotation: Rotation): Float =
        (width * abs(rotation.cos) + height * abs(rotation.sin)) / 2f

    private fun CropRect.halfExtentY(rotation: Rotation): Float =
        (width * abs(rotation.sin) + height * abs(rotation.cos)) / 2f

    private class Rotation(
        val cos: Float,
        val sin: Float,
    ) {
        companion object {
            fun of(degrees: Float): Rotation {
                val radians = Math.toRadians(degrees.toDouble())
                return Rotation(cos = snap(cos(radians)), sin = snap(sin(radians)))
            }

            private fun snap(value: Double): Float = if (abs(value) < SNAP_EPSILON) 0f else value.toFloat()
        }
    }

    private companion object {
        const val QUARTER_TURNS = 4
        const val OVERSIZED_LENGTH = 4f
        const val MIN_CROP_FRACTION = 0.1f
        const val RESIZE_SEARCH_STEPS = 24
        const val TOLERANCE = 1e-4f
        const val SNAP_EPSILON = 1e-6
    }
}

/** 결과 이미지 좌표계의 사각형. 중심은 원본 중심 기준이다. */
data class CropRect(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
)

enum class CropCorner(
    val signX: Int,
    val signY: Int,
) {
    TopStart(-1, -1),
    TopEnd(1, -1),
    BottomStart(-1, 1),
    BottomEnd(1, 1),
}
