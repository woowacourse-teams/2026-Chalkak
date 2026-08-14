package com.stonefive.chalkak.feature.signature

import androidx.compose.runtime.Immutable

@Immutable
data class SignaturePoint(
    val xRatio: Float,
    val yRatio: Float,
)

@Immutable
data class SignatureStroke(val points: List<SignaturePoint>)

data class SignatureUiState(
    val strokes: List<SignatureStroke> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: Throwable? = null,
) {
    val hasSignature: Boolean
        get() = strokes.any { it.points.isNotEmpty() }

    val canSubmit: Boolean
        get() = hasSignature && !isSubmitting
}
