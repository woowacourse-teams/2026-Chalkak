package com.stonefive.chalkak.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.repository.SignatureRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SignatureViewModel(
    private val signatureRepository: SignatureRepository,
    private val pngEncoder: SignaturePngEncoder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignatureUiState())
    val uiState: StateFlow<SignatureUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<SignatureUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onAction(action: SignatureUiAction) {
        if (_uiState.value.isSubmitting) return

        when (action) {
            is SignatureUiAction.StrokeStarted -> startStroke(action.point)
            is SignatureUiAction.StrokeMoved -> moveStroke(action.point)
            SignatureUiAction.StrokeFinished -> finishStroke()
            SignatureUiAction.UndoClicked -> undo()
            SignatureUiAction.ClearClicked -> clear()
            SignatureUiAction.SubmitClicked -> submit()
        }
    }

    private fun startStroke(point: SignaturePoint) {
        _uiState.value = _uiState.value.copy(
            strokes = _uiState.value.strokes + SignatureStroke(points = listOf(point.normalized())),
            error = null,
        )
    }

    private fun moveStroke(point: SignaturePoint) {
        val strokes = _uiState.value.strokes
        if (strokes.isEmpty()) return
        val lastStroke = strokes.last()
        _uiState.value = _uiState.value.copy(
            strokes = strokes.dropLast(1) + lastStroke.copy(
                points = lastStroke.points + point.normalized(),
            ),
        )
    }

    private fun finishStroke() {
        val strokes = _uiState.value.strokes
        if (strokes
                .lastOrNull()
                ?.points
                .isNullOrEmpty()
        ) {
            _uiState.value = _uiState.value.copy(strokes = strokes.dropLast(1))
        }
    }

    private fun undo() {
        val strokes = _uiState.value.strokes
        if (strokes.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                strokes = strokes.dropLast(1),
                error = null,
            )
        }
    }

    private fun clear() {
        _uiState.value = _uiState.value.copy(
            strokes = emptyList(),
            error = null,
        )
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, error = null)
            runCatching {
                val signaturePng = pngEncoder.encode(state.strokes)
                signatureRepository.uploadSignature(signaturePng)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                _uiEvent.send(SignatureUiEvent.SignatureSaved)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = error,
                )
            }
        }
    }

    private fun SignaturePoint.normalized(): SignaturePoint = copy(
        xRatio = xRatio.coerceIn(0f, 1f),
        yRatio = yRatio.coerceIn(0f, 1f),
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                SignatureViewModel(
                    signatureRepository = application.appContainer.signatureRepository,
                    pngEncoder = AndroidSignaturePngEncoder(),
                )
            }
        }
    }
}
