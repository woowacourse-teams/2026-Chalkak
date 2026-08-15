package com.stonefive.chalkak.feature.display

import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

data class DisplayUiState(
    val selectedDate: LocalDate? = null,
    val latestDate: LocalDate? = null,
    val earliestDate: LocalDate? = null,
    val topic: String = "",
    val content: DisplayContentState = DisplayContentState.Loading,
) {
    val canGoPrevious: Boolean
        get() = selectedDate != null && earliestDate != null && selectedDate > earliestDate

    val canGoNext: Boolean
        get() = selectedDate != null && latestDate != null && selectedDate < latestDate
}

sealed interface DisplayContentState {
    data object Loading : DisplayContentState

    data class Latest(
        val photos: List<Post>,
        val selectedSort: PostSort,
    ) : DisplayContentState

    data class Archive(
        val photos: List<Post>,
        val featuredPhotos: List<Post>,
        val featuredPage: Int = 0,
    ) : DisplayContentState

    data class Error(val message: String) : DisplayContentState
}
