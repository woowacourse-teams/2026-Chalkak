package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoRotation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 편집 화면에서 자르기 프레임을 옮기고, 늘리고, 돌릴 때 프레임이 사진 밖으로 나가지 않도록 새 [CropWindow]를 계산한다.
 *
 * 계산은 "중심 단위 좌표"에서 한다. 원본 긴 변을 1로 두고(4000×3000 사진이면 [width] 1, [height] 0.75),
 * 원본 가운데를 (0, 0)으로 둔 뒤, 사진과 같이 시계 방향으로 돌린 좌표다. 화면의 프레임은 사진이 돌아가도 늘 똑바로
 * 서 있으므로, 이 좌표에서 프레임은 기울지 않은 사각형([CenteredCropRect])이 되어 계산이 단순해진다.
 * [CropWindow](0..1 비율)와 이 좌표 사이 변환은 [centeredRect]와 cropWindowOf가 맡는다.
 * 다른 좌표계와 비교는 docs/architecture/photo-edit.md 참고.
 */
class CropGeometry(
    imageWidth: Float,
    imageHeight: Float,
) {
    /** 원본 긴 변을 1로 둔 원본 가로·세로. 둘 중 하나는 1이다. */
    val width: Float
    val height: Float

    init {
        require(imageWidth > 0f && imageHeight > 0f) { "Image size must be positive" }
        val longEdge = max(imageWidth, imageHeight)
        width = imageWidth / longEdge
        height = imageHeight / longEdge
    }

    /** 90°씩 [quarterTurns]번 돌린 원본의 가로 ÷ 세로. */
    fun turnedAspectRatio(quarterTurns: Int): Float = turnedWidth(quarterTurns) / turnedHeight(quarterTurns)

    /** [edit]의 자르기 영역을 중심 단위 좌표의 사각형으로 바꾼다. */
    fun centeredRect(edit: PhotoEdit): CenteredCropRect {
        val rotation = PhotoRotation.of(edit.rotationDegrees)
        val sourceX = edit.crop.centerX * width - width / 2f
        val sourceY = edit.crop.centerY * height - height / 2f
        return CenteredCropRect(
            centerX = rotation.cos * sourceX - rotation.sin * sourceY,
            centerY = rotation.sin * sourceX + rotation.cos * sourceY,
            width = edit.crop.width * turnedWidth(edit.quarterTurns),
            height = edit.crop.height * turnedHeight(edit.quarterTurns),
        )
    }

    fun isInside(edit: PhotoEdit): Boolean = fits(centeredRect(edit), edit)

    /** 돌린 원본 전체가 화면 영역에 들어가도록 하는 배율. 중심 단위 1이 화면 몇 픽셀인지를 돌려준다. */
    fun fitScale(
        rotationDegrees: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Float {
        val rotation = PhotoRotation.of(rotationDegrees)
        val boundsWidth = width * abs(rotation.cos) + height * abs(rotation.sin)
        val boundsHeight = width * abs(rotation.sin) + height * abs(rotation.cos)
        return min(viewportWidth / boundsWidth, viewportHeight / boundsHeight)
    }

    /** 원본을 벗어난 영역을 비율을 유지한 채 줄이고, 그래도 벗어나면 안쪽으로 옮긴다. */
    fun constrain(edit: PhotoEdit): CropWindow {
        val rotation = PhotoRotation.of(edit.rotationDegrees)
        val rect = centeredRect(edit)
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

    /** 사진과 프레임을 같이 90° 돌린다. 프레임의 가로·세로가 바뀌고, 원본 기준 중심은 그대로다. */
    fun rotateQuarter(edit: PhotoEdit): PhotoEdit {
        val rotated = edit.copy(
            quarterTurns = (edit.quarterTurns + 1).mod(QUARTER_TURNS),
            crop = edit.crop.copy(width = edit.crop.height, height = edit.crop.width),
        )
        return rotated.copy(crop = constrain(rotated))
    }

    /** 사진만 돌리고 결과 사각형의 가로·세로는 그대로 둔다. 원본을 벗어나면 비율을 유지한 채 줄인다. */
    fun rotateQuarterKeepingFrame(edit: PhotoEdit): PhotoEdit {
        val rect = centeredRect(edit)
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

    /** 화면에서 끈 만큼(중심 단위) 프레임을 옮긴다. 원본 밖으로 나가면 경계에서 멈춘다. */
    fun move(
        edit: PhotoEdit,
        deltaX: Float,
        deltaY: Float,
    ): CropWindow {
        val rect = centeredRect(edit)
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
        val rect = centeredRect(edit)
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
        ) = CenteredCropRect(
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
        rectAt: (Float) -> CenteredCropRect,
    ): CenteredCropRect {
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
        rect: CenteredCropRect,
        edit: PhotoEdit,
    ): CropWindow {
        val rotation = PhotoRotation.of(edit.rotationDegrees)
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
        rect: CenteredCropRect,
        edit: PhotoEdit,
    ): Boolean {
        val rotation = PhotoRotation.of(edit.rotationDegrees)
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

    private fun CenteredCropRect.halfExtentX(rotation: PhotoRotation): Float =
        (width * abs(rotation.cos) + height * abs(rotation.sin)) / 2f

    private fun CenteredCropRect.halfExtentY(rotation: PhotoRotation): Float =
        (width * abs(rotation.sin) + height * abs(rotation.cos)) / 2f

    private companion object {
        const val QUARTER_TURNS = 4
        const val OVERSIZED_LENGTH = 4f
        const val MIN_CROP_FRACTION = 0.1f
        const val RESIZE_SEARCH_STEPS = 24
        const val TOLERANCE = 1e-4f
    }
}

/** 중심 단위 좌표의 기울지 않은 사각형. 중심은 원본 가운데가 (0, 0)이고, 길이는 원본 긴 변이 1이다. */
data class CenteredCropRect(
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
