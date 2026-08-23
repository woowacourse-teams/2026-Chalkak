package com.stonefive.chalkak.feature.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.DisplayRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DisplayViewModel(
    private val repository: DisplayRepository,
    private val initialDate: LocalDate? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DisplayUiState())
    val uiState: StateFlow<DisplayUiState> = _uiState.asStateFlow()

    private var latestLoadGeneration = 0
    private var selectedSort = PostSort.LATEST

    init {
        loadDisplay(date = initialDate)
    }

    fun moveToPreviousDate() {
        val state = _uiState.value
        val selectedDate = state.selectedDate ?: return
        val earliestDate = state.earliestDate ?: return
        val targetDate = selectedDate.minusDays(1).coerceAtLeast(earliestDate)
        if (targetDate == selectedDate) return

        startDateChange(targetDate)
    }

    fun moveToNextDate() {
        val state = _uiState.value
        val selectedDate = state.selectedDate ?: return
        val latestDate = state.latestDate ?: return
        val targetDate = selectedDate.plusDays(1).coerceAtMost(latestDate)
        if (targetDate == selectedDate) return

        startDateChange(targetDate)
    }

    fun selectSort(sort: PostSort) {
        val state = _uiState.value
        val latestContent = state.content as? DisplayContentState.Latest ?: return
        if (sort == latestContent.selectedSort) return

        selectedSort = sort
        _uiState.update { it.copy(content = DisplayContentState.Loading) }
        loadDisplay(state.selectedDate)
    }

    fun updateFeaturedPage(page: Int) {
        _uiState.update { state ->
            val archive = state.content as? DisplayContentState.Archive ?: return@update state
            val lastPage = archive.featuredPhotos.lastIndex
                .coerceAtLeast(0)
            state.copy(
                content = archive.copy(featuredPage = page.coerceIn(0, lastPage)),
            )
        }
    }

    private fun startDateChange(targetDate: LocalDate) {
        val sort = if (targetDate < requireNotNull(_uiState.value.latestDate)) {
            PostSort.POPULAR
        } else {
            selectedSort
        }
        _uiState.update {
            it.copy(
                selectedDate = targetDate,
                content = DisplayContentState.Loading,
            )
        }
        loadDisplay(targetDate, sort)
    }

    private fun loadDisplay(
        date: LocalDate?,
        sort: PostSort = selectedSort,
    ) {
        val generation = ++latestLoadGeneration

        viewModelScope.launch {
            _uiState.update { it.copy(content = DisplayContentState.Loading) }
            runCatching { repository.getDisplay(date, sort) }
                .onSuccess { display ->
                    if (generation != latestLoadGeneration) return@onSuccess
                    val content = if (display.selectedDate < display.latestDate) {
                        DisplayContentState.Archive(
                            photos = display.photos,
                            featuredPhotos = display.featuredPhotos,
                        )
                    } else {
                        DisplayContentState.Latest(
                            photos = display.photos,
                            selectedSort = selectedSort,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            selectedDate = display.selectedDate,
                            latestDate = display.latestDate,
                            earliestDate = display.earliestDate,
                            topic = display.topic,
                            content = content,
                        )
                    }
                }.onFailure {
                    if (generation != latestLoadGeneration) return@onFailure
                    _uiState.update {
                        it.copy(
                            content = DisplayContentState.Error(
                                message = "전시를 불러오지 못했어요",
                            ),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(initialDate: LocalDate? = null) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                DisplayViewModel(
                    repository = application.appContainer.displayRepository,
                    initialDate = initialDate,
                )
            }
        }

        val Factory = factory()
    }
}
