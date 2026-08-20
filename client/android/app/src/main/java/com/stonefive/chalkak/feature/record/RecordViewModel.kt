package com.stonefive.chalkak.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.repository.RecordRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repository: RecordRepository,
    initialMonth: YearMonth = INITIAL_RECORD_MONTH,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordUiState(month = initialMonth))
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var latestLoadGeneration = 0

    init {
        loadRecord(initialMonth)
    }

    fun moveToPreviousMonth() {
        loadRecord(
            month = _uiState.value.month
                .minusMonths(1),
        )
    }

    fun moveToNextMonth() {
        loadRecord(
            month = _uiState.value.month
                .plusMonths(1),
        )
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (date !in state.photos.map { it.date }) return

        _uiState.update { it.copy(selectedDate = date) }
    }

    private fun loadRecord(month: YearMonth) {
        val generation = ++latestLoadGeneration
        _uiState.update {
            it.copy(
                month = month,
                photos = emptyList(),
                selectedDate = null,
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching { repository.getRecord(month) }
                .onSuccess { content ->
                    if (generation != latestLoadGeneration) return@onSuccess

                    _uiState.update {
                        it.copy(
                            month = content.month,
                            photos = content.photos,
                            selectedDate = content.photos
                                .firstOrNull()
                                ?.date,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }.onFailure { error ->
                    if (generation != latestLoadGeneration) return@onFailure

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?: "기록을 불러오지 못했어요",
                        )
                    }
                }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                RecordViewModel(
                    repository = application.appContainer.recordRepository,
                )
            }
        }
    }
}
