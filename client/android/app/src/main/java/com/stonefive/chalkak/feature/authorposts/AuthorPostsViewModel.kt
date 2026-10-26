package com.stonefive.chalkak.feature.authorposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthorPostsViewModel(
    private val repository: PostRepository,
    private val authorId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthorPostsUiState())
    val uiState = _uiState.asStateFlow()
    private val deletedPostIds = mutableSetOf<String>()
    private var isNearEnd = false

    init {
        loadPage(1)
    }

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingNext || state.isAuthorUnavailable || state.isLoginRequired) return
        loadPage(if (state.currentPage == 0) 1 else state.currentPage + 1)
    }

    fun onEndThresholdChanged(isNearEnd: Boolean) {
        this.isNearEnd = isNearEnd
        val state = _uiState.value
        if (!isNearEnd || !state.hasNext || state.hasError || state.isLoading || state.isLoadingNext ||
            state.isAuthorUnavailable || state.isLoginRequired
        ) {
            return
        }
        loadPage(state.currentPage + 1)
    }

    fun onPostDeleted(postId: String) {
        deletedPostIds += postId
        _uiState.update { it.copy(posts = it.posts.filterNot { post -> post.id == postId }) }
    }

    private fun loadPage(page: Int) {
        _uiState.update {
            if (page == 1) {
                AuthorPostsUiState(pageSize = it.pageSize)
            } else {
                it.copy(isLoadingNext = true, hasError = false)
            }
        }
        viewModelScope.launch {
            val result = repository.getAuthorPosts(authorId, page, _uiState.value.pageSize)
            val previousPostCount = _uiState.value.posts.size
            when (result) {
                is HomeResult.Success -> _uiState.update { state ->
                    val response = result.value
                    state.copy(
                        posts = (if (page == 1) response.posts else state.posts + response.posts)
                            .filterNot { it.id in deletedPostIds }
                            .distinctBy { it.id },
                        isLoading = false,
                        isLoadingNext = false,
                        currentPage = response.currentPage,
                        pageSize = response.pageSize,
                        hasNext = response.hasNext && response.posts.isNotEmpty(),
                        hasError = false,
                    )
                }

                is HomeResult.Failure -> _uiState.update { state ->
                    val isUnavailable = result.reason == HomeFailure.Http(404)
                    val isUnauthorized =
                        result.reason == HomeFailure.Unauthorized || result.reason == HomeFailure.Http(401)
                    state.copy(
                        posts = if (isUnavailable || isUnauthorized) emptyList() else state.posts,
                        isLoading = false,
                        isLoadingNext = false,
                        hasError = !isUnavailable && !isUnauthorized,
                        isAuthorUnavailable = isUnavailable,
                        isLoginRequired = isUnauthorized,
                        hasNext = state.hasNext && !isUnavailable && !isUnauthorized,
                    )
                }
            }
            if (result is HomeResult.Success && page > 1 &&
                _uiState.value.posts.size == previousPostCount
            ) {
                onEndThresholdChanged(isNearEnd)
            }
        }
    }

    companion object {
        fun factory(authorId: String) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                AuthorPostsViewModel(application.appContainer.postRepository, authorId)
            }
        }
    }
}
