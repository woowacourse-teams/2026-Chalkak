package com.stonefive.chalkak.feature.signature

sealed interface SignatureUiAction {
    data class StrokeStarted(val point: SignaturePoint) : SignatureUiAction

    data class StrokeMoved(val point: SignaturePoint) : SignatureUiAction

    data object StrokeFinished : SignatureUiAction

    data object UndoClicked : SignatureUiAction

    data object ClearClicked : SignatureUiAction

    data object SubmitClicked : SignatureUiAction
}
