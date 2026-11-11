package com.stonefive.chalkak.feature.filter

import androidx.lifecycle.ViewModel
import com.stonefive.chalkak.R
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

// 필터 API 연동 전 임시 데이터이며, 연동 시 data 계층 조회로 교체한다.
private val SampleFilters = List(6) { index ->
    FilterItem(
        id = "sample-$index",
        name = "필터 ${index + 1}",
        description = "언제 언제 쓰면 예뻐요",
        creatorSignature = "제작자\n사인",
        coverImageRes = R.drawable.home_feed_photo,
        creatorPhotoResList = List(4) { R.drawable.home_feed_photo },
        usedPhotoResList = List(3) { R.drawable.home_feed_photo },
    )
}
