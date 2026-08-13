package com.stonefive.chalkak.feature.login

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: Throwable? = null,
)
