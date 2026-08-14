package com.stonefive.chalkak.feature.signature

sealed interface SignatureUiEvent {
    data object SignatureSaved : SignatureUiEvent
}
