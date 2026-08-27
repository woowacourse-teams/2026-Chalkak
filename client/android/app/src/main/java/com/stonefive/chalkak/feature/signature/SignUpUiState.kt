package com.stonefive.chalkak.feature.signature

data class SignUpUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
