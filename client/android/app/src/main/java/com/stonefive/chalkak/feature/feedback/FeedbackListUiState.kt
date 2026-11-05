package com.stonefive.chalkak.feature.feedback

/** 고객센터 목록 영역에 무엇을 보여 줄지. 당겨 새로고침·이어보기는 [FeedbackListUiState]의 플래그로 따로 둔다. */
enum class FeedbackListStatus {
    /** 처음 불러오는 중. 목록이 비어 있어 가운데 로딩을 보여 준다. */
    Loading,

    /** 문의가 1건 이상 있다. */
    Content,

    /** 첫 페이지가 `feedbacks: []`였다. 빈 안내를 보여 준다. */
    Empty,

    /** 첫 페이지를 불러오지 못했다. 다시 시도 버튼을 보여 준다. */
    Failed,
}

/**
 * 고객센터(내 문의 목록) 화면 상태.
 *
 * @property currentPage 마지막으로 불러온 페이지(1부터). 아직 없으면 0.
 * @property isRefreshing 목록을 보여 주는 중에 첫 페이지부터 다시 불러오는 중(당겨 새로고침 인디케이터).
 * @property loadMoreFailed 다음 페이지 요청이 실패했다. 자동으로 다시 요청하지 않고 "더 불러오기"를 보여 준다.
 * @property isReauthenticationRequired 401을 받아 로그인 화면으로 보내야 한다.
 */
data class FeedbackListUiState(
    val status: FeedbackListStatus = FeedbackListStatus.Loading,
    val items: List<FeedbackListItemUiState> = emptyList(),
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isReauthenticationRequired: Boolean = false,
)

/**
 * 목록 한 줄. [timeText]는 접수 시각을 알림함과 같은 "M월 d일 HH:mm"(Asia/Seoul)으로 바꾼 값이다.
 */
data class FeedbackListItemUiState(
    val id: String,
    val content: String,
    val timeText: String,
)
