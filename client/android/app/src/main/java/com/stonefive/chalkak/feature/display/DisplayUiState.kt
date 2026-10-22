package com.stonefive.chalkak.feature.display

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

data class DisplayUiState(
    val selectedDate: LocalDate? = null,
    val latestDate: LocalDate? = null,
    val earliestDate: LocalDate? = null,
    val topic: String = "",
    val content: DisplayContentState = DisplayContentState.Loading,
    val likedPhotoIds: Set<String> = emptySet(),
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val randomSeed: String? = null,
    val isLoadingNext: Boolean = false,
    val pendingMessage: UiMessage? = null,
) {
    val canGoPrevious: Boolean
        get() = content !is DisplayContentState.Loading &&
            selectedDate != null &&
            (earliestDate == null || selectedDate > earliestDate)

    val canGoNext: Boolean
        get() = content !is DisplayContentState.Loading &&
            selectedDate != null &&
            latestDate != null &&
            selectedDate < latestDate

    /** 날짜 휠을 열 수 있는지. 휠의 범위는 [earliestDate]부터 [latestDate]까지다. */
    val canPickDate: Boolean
        get() = content !is DisplayContentState.Loading &&
            selectedDate != null &&
            latestDate != null
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
