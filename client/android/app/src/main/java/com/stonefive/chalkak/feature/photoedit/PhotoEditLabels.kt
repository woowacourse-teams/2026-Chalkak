package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.PhotoAdjustment
import com.stonefive.chalkak.domain.model.PhotoFilter

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
        PhotoEditTab.Adjust -> "조정"
        PhotoEditTab.Filter -> "필터"
    }

val PhotoAdjustment.label: String
    get() = when (this) {
        PhotoAdjustment.Exposure -> "노출"
        PhotoAdjustment.Contrast -> "대비"
        PhotoAdjustment.Highlights -> "하이라이트"
        PhotoAdjustment.Shadows -> "그림자"
        PhotoAdjustment.BlackPoint -> "블랙 포인트"
        PhotoAdjustment.Saturation -> "채도"
        PhotoAdjustment.Warmth -> "따뜻함"
    }

val PhotoAdjustItem.label: String
    get() = when (this) {
        PhotoAdjustItem.Auto -> "자동"
        is PhotoAdjustItem.Manual -> adjustment.label
    }
