package com.stonefive.chalkak.feature.filter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FilterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FilterUiState(filters = SampleFilters))
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    fun selectFilter(filterId: String) {
        _uiState.update { it.copy(selectedFilterId = filterId) }
    }

    fun dismissFilter() {
        _uiState.update { it.copy(selectedFilterId = null) }
    }

    fun toggleFavorite(filterId: String) {
        _uiState.update { state ->
            val favorites = if (state.isFavorite(filterId)) {
                state.favoriteFilterIds - filterId
            } else {
                state.favoriteFilterIds + filterId
            }
            state.copy(favoriteFilterIds = favorites)
        }
    }
}
