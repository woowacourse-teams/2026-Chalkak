package com.stonefive.chalkak.feature.signature

sealed interface SignatureUiEvent {
    data class SignatureSaved(val signaturePng: ByteArray) : SignatureUiEvent
}
