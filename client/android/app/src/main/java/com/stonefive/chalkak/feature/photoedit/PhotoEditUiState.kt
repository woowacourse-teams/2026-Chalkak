package com.stonefive.chalkak.feature.photoedit

import androidx.compose.runtime.Immutable
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoAdjustment
import com.stonefive.chalkak.domain.model.PhotoAdjustments
import com.stonefive.chalkak.domain.model.PhotoEdit

@Immutable
data class PhotoEditUiState(
    val imageUri: String,
    val edit: PhotoEdit = PhotoEdit(),
    val imageSize: PhotoImageSize? = null,
    val selectedTab: PhotoEditTab = PhotoEditTab.Crop,
    val selectedAdjustItem: PhotoAdjustItem = PhotoAdjustItem.Auto,
    /** 자른 사진을 분석한 자동 보정값. 분석 전에는 null이다. */
    val autoAdjustments: PhotoAdjustments? = null,
    /** 자동 보정 위에 더한 항목별 값. [PhotoEdit.adjustments]는 `자동 보정값 × 강도 + 이 값`이다. */
    val manualAdjustments: PhotoAdjustments = PhotoAdjustments(),
    val aspectRatio: CropAspectRatio = CropAspectRatio.Free,
    val lockedAspectRatio: Float? = null,
    val completedEdit: PhotoEdit? = null,
) {
    val canReset: Boolean
        get() = edit.quarterTurns != 0 ||
            edit.straightenDegrees != 0f ||
            edit.crop != CropWindow.Full ||
            aspectRatio != CropAspectRatio.Free

    val canResetAdjustments: Boolean
        get() = edit.autoAdjustmentIntensity > 0f || !manualAdjustments.isIdentity

    /** 자동 보정이 각 항목에 더한 값. 자동 보정이 꺼져 있거나 분석 전이면 null이다. */
    val appliedAutoAdjustments: PhotoAdjustments?
        get() = autoAdjustments
            ?.takeIf { edit.autoAdjustmentIntensity > 0f }
            ?.scaled(scale = edit.autoAdjustmentIntensity, offset = PhotoAdjustments())

    fun value(item: PhotoAdjustItem): Float = when (item) {
        PhotoAdjustItem.Auto -> edit.autoAdjustmentIntensity
        is PhotoAdjustItem.Manual -> edit.adjustments[item.adjustment]
    }

    /** 현재 90° 회전 방향에서 본 원본 비율(가로/세로). 이미지를 불러오기 전에는 null이다. */
    val originalAspectRatio: Float?
        get() = imageSize?.let { size ->
            if (edit.quarterTurns.mod(2) == 0) {
                size.width.toFloat() / size.height
            } else {
                size.height.toFloat() / size.width
            }
        }
}

@Immutable
data class PhotoImageSize(
    val width: Int,
    val height: Int,
)

/** 조정 화면에서 고르는 항목. 자동 보정은 강도(0..1)를, 나머지는 항목 값(-1..1)을 바꾼다. */
sealed interface PhotoAdjustItem {
    val range: ClosedFloatingPointRange<Float>

    data object Auto : PhotoAdjustItem {
        override val range = 0f..1f
    }

    data class Manual(val adjustment: PhotoAdjustment) : PhotoAdjustItem {
        override val range = -1f..1f
    }

    companion object {
        val entries: List<PhotoAdjustItem> = listOf(Auto) + PhotoAdjustment.entries.map(::Manual)
    }
}

enum class PhotoEditTab {
    Crop,
    Adjust,
    Filter,
}

/** 고정할 결과 비율(가로/세로). [Original]과 [Free]는 이미지에 따라 정해진다. 선언 순서가 화면 순서다. */
enum class CropAspectRatio(val ratio: Float?) {
    Original(null),
    Free(null),
    Square(1f),
    Portrait4x5(4f / 5f),
    Landscape5x4(5f / 4f),
    Portrait3x4(3f / 4f),
    Landscape4x3(4f / 3f),
    Portrait9x16(9f / 16f),
    Landscape16x9(16f / 9f),
}
