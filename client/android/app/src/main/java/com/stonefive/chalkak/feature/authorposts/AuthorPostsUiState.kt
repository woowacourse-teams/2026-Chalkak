package com.stonefive.chalkak.feature.authorposts

import com.stonefive.chalkak.domain.model.AuthorPost

data class AuthorPostsUiState(
    val posts: List<AuthorPost> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingNext: Boolean = false,
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    val hasNext: Boolean = false,
    val hasError: Boolean = false,
    val isAuthorUnavailable: Boolean = false,
    val isLoginRequired: Boolean = false,
)
