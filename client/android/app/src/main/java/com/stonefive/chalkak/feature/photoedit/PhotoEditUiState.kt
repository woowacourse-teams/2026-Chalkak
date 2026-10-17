package com.stonefive.chalkak.feature.photoedit

import androidx.compose.runtime.Immutable
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit

@Immutable
data class PhotoEditUiState(
    val imageUri: String,
    val edit: PhotoEdit = PhotoEdit(),
    val imageSize: PhotoImageSize? = null,
    val selectedTab: PhotoEditTab = PhotoEditTab.Crop,
    val aspectRatio: CropAspectRatio = CropAspectRatio.Free,
    val lockedAspectRatio: Float? = null,
    val completedEdit: PhotoEdit? = null,
) {
    val canReset: Boolean
        get() = edit.quarterTurns != 0 ||
            edit.straightenDegrees != 0f ||
            edit.crop != CropWindow.Full ||
            aspectRatio != CropAspectRatio.Free

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

enum class PhotoEditTab {
    Crop,
    Filter,
}

/** 고정할 결과 비율(가로/세로). [Free]와 [Original]은 이미지에 따라 정해진다. */
enum class CropAspectRatio(val ratio: Float?) {
    Free(null),
    Original(null),
    Square(1f),
    Portrait4x5(4f / 5f),
    Portrait3x4(3f / 4f),
    Landscape16x9(16f / 9f),
}
