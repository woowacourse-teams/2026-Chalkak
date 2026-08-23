package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort

data class HomeUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val topic: String = "",
    val photos: List<Post> = emptyList(),
    val selectedSort: PostSort = PostSort.LATEST,
    val likedPhotoIds: Set<String> = emptySet(),
)
