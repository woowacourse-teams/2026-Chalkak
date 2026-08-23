package com.stonefive.chalkak.feature.upload

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoUploadUiState(
    val selectedImage: String? = null,
    val signatureModel: String? = null,
    val caption: String = "",
    val isCameraAvailable: Boolean = true,
    val successContent: PhotoUploadSuccessContent = PhotoUploadSuccessContent(),
) {
    val canSubmit: Boolean
        get() = selectedImage != null
}

@Immutable
data class PhotoUploadSuccessContent(
    val dateLabel: String = "2025. 07. 18",
    val topic: String = "주제",
    val nickname: String = "@@",
    val exhibitionCount: Int = 128,
)
