package com.stonefive.chalkak.feature.photoedit

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.stonefive.chalkak.domain.model.PhotoFilter

/** 인코더와 같은 계수로 미리보기 색을 맞춘다. */
fun PhotoFilter.toColorFilter(): ColorFilter? = colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

val PhotoFilter.label: String
    get() = when (this) {
        PhotoFilter.Original -> "원본"
        PhotoFilter.Mono -> "흑백"
        PhotoFilter.Warm -> "따뜻하게"
        PhotoFilter.Cool -> "차갑게"
        PhotoFilter.Vintage -> "빈티지"
        PhotoFilter.Vivid -> "선명하게"
    }

val CropAspectRatio.label: String
    get() = when (this) {
        CropAspectRatio.Original -> "원본"
        CropAspectRatio.Free -> "자유"
        CropAspectRatio.Square -> "1:1"
        CropAspectRatio.Portrait4x5 -> "4:5"
        CropAspectRatio.Landscape5x4 -> "5:4"
        CropAspectRatio.Portrait3x4 -> "3:4"
        CropAspectRatio.Landscape4x3 -> "4:3"
        CropAspectRatio.Portrait9x16 -> "9:16"
        CropAspectRatio.Landscape16x9 -> "16:9"
    }

val PhotoEditTab.label: String
    get() = when (this) {
        PhotoEditTab.Crop -> "자르기"
        PhotoEditTab.Filter -> "필터"
    }
