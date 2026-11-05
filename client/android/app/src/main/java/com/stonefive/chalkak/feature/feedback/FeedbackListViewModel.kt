package com.stonefive.chalkak.feature.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.FeedbackInquiry
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 고객센터(내 문의 목록). 만들어지면 첫 페이지를 불러온다.
 *
 * - [refresh]: 첫 페이지부터 다시 불러와 목록을 바꾼다(접수 성공·삭제 성공·당겨 새로고침).
 * - [loadNextPage]: `hasNext`면 `currentPage + 1`을 불러와 이어 붙이고, 이미 있는 문의 ID는 한 번만 둔다.
 */
class FeedbackListViewModel(private val repository: FeedbackRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedbackListUiState())
    val uiState: StateFlow<FeedbackListUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // 새로고침이 이어보기보다 우선한다. 진행 중인 이어보기 응답이 새 목록에 붙지 않게 취소한다.
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update { state ->
            if (state.status == FeedbackListStatus.Content) {
                state.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false)
            } else {
                state.copy(status = FeedbackListStatus.Loading, isLoadingMore = false, loadMoreFailed = false)
            }
        }
        refreshJob = viewModelScope.launch {
            try {
                val page = repository.getFeedbacks(FIRST_PAGE)
                _uiState.update { state ->
                    val items = page.inquiries
                        .map(FeedbackInquiry::toUiState)
                        .distinctBy { it.id }
                    state.copy(
                        status = if (items.isEmpty()) FeedbackListStatus.Empty else FeedbackListStatus.Content,
                        items = items,
                        currentPage = page.currentPage,
                        hasNext = page.hasNext,
                        isRefreshing = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { state ->
                    when {
                        error.isUnauthorized() -> state.copy(isRefreshing = false, isReauthenticationRequired = true)

                        // 보여 주던 목록이 있으면 유지하고 인디케이터만 내린다.
                        state.status == FeedbackListStatus.Content -> state.copy(isRefreshing = false)

                        else -> state.copy(
                            status = FeedbackListStatus.Failed,
                            items = emptyList(),
                            isRefreshing = false,
                        )
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasNext || state.isRefreshing || state.isLoadingMore || refreshJob?.isActive == true) return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        loadMoreJob = viewModelScope.launch {
            try {
                val page: FeedbackInquiryPage = repository.getFeedbacks(state.currentPage + 1)
                _uiState.update { current ->
                    current.copy(
                        items = (current.items + page.inquiries.map(FeedbackInquiry::toUiState)).distinctBy { it.id },
                        currentPage = page.currentPage,
                        hasNext = page.hasNext,
                        isLoadingMore = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (error.isUnauthorized()) {
                        current.copy(isLoadingMore = false, isReauthenticationRequired = true)
                    } else {
                        current.copy(isLoadingMore = false, loadMoreFailed = true)
                    }
                }
            }
        }
    }

    /** Route가 로그인 화면으로 보낸 뒤 부른다. 같은 상태로 다시 이동하지 않게 한다. */
    fun reauthenticationHandled() {
        _uiState.update { it.copy(isReauthenticationRequired = false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedbackListViewModel(application.appContainer.feedbackRepository)
            }
        }
    }
}

private fun Exception.isUnauthorized(): Boolean =
    (this as? FeedbackInquiryException)?.reason == FeedbackInquiryFailure.UNAUTHORIZED

private fun FeedbackInquiry.toUiState(): FeedbackListItemUiState = FeedbackListItemUiState(
    id = id,
    content = content,
    timeText = createdAt.toFeedbackTimeText(),
)

private const val FIRST_PAGE = 1
