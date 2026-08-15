package com.stonefive.chalkak.feature.upload

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoUploadUiState(
    val selectedImage: Any? = null,
    val caption: String = "",
) {
    val canSubmit: Boolean
        get() = selectedImage != null
}
