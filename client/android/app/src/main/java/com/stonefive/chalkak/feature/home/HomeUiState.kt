package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.Banner
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.feature.aireview.AiReviewPhase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HomeUiState(
    val date: LocalDate,
    val topic: String = "",
    val topicStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val hero: HomeHeroState = HomeHeroState.Loading,
    val week: List<HomeWeekDay> = homeWeekDates(date).map(::HomeWeekDay),
    val recordsStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val yesterday: HomeRankingState = HomeRankingState(),
    val trending: HomeRankingState = HomeRankingState(),
    val banners: List<Banner> = emptyList(),
    val bannerStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val bannerCurrentPage: Int = 0,
    val bannerHasNext: Boolean = false,
    val isLoadingMoreBanners: Boolean = false,
    val bannerLoadMoreError: Boolean = false,
    val isIssueOnTop: Boolean = false,
    val isIssueLayoutResolved: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAuthenticated: Boolean = false,
    val feedbackPhase: HomeFeedbackPhase = HomeFeedbackPhase.Hidden,
    val feedbackNotice: HomeFeedbackNotice? = null,
    val feedbackResult: AiReviewContent? = null,
    val feedbackMessage: String? = null,
    val feedbackCanRequestAgain: Boolean = false,
    val hasUnreadNotifications: Boolean = false,
)

val HomeUiState.isInitialLoadComplete: Boolean
    get() = topicStatus != HomeSectionStatus.Loading &&
        hero != HomeHeroState.Loading &&
        recordsStatus != HomeSectionStatus.Loading &&
        yesterday.status != HomeSectionStatus.Loading &&
        trending.status != HomeSectionStatus.Loading

// 오늘 사진을 올리기 전에는 히어로 카드와 함께 이번 주 기록의 오늘 칸도 업로드 진입점이 된다.
val HomeUiState.showsUploadEntry: Boolean
    get() = hero == HomeHeroState.Empty

// 비로그인 상태에는 기록이 없으므로 이번 주 기록 영역을 숨긴다.
val HomeUiState.showsWeeklyRecord: Boolean
    get() = hero != HomeHeroState.LoginRequired

// 배치가 정해지기 전에는 ISSUE를 숨겨 아래에서 위로 튀지 않게 한다.
val HomeUiState.showsIssue: Boolean
    get() = isIssueLayoutResolved && (banners.isNotEmpty() || bannerStatus == HomeSectionStatus.Loading)

// 추가 로딩 중이거나 실패하면 캐러셀 끝에 로딩·재시도 카드가 한 장 붙는다.
val HomeUiState.issueCarouselPageCount: Int
    get() = banners.size + if (isLoadingMoreBanners || bannerLoadMoreError) 1 else 0

fun HomeUiState.shouldLoadMoreBanners(lastVisibleIssueIndex: Int?): Boolean = banners.isNotEmpty() &&
    bannerStatus == HomeSectionStatus.Ready &&
    bannerHasNext &&
    !isRefreshing &&
    !isLoadingMoreBanners &&
    !bannerLoadMoreError &&
    lastVisibleIssueIndex != null &&
    lastVisibleIssueIndex >= banners.lastIndex - ISSUE_PREFETCH_DISTANCE

private const val ISSUE_PREFETCH_DISTANCE = 2

// 피드백 카드가 보이는 경우는 두 가지다.
// 1) 승인 전 안내(feedbackNotice가 있음): 오늘 사진을 올리기 전이거나 검수 중·대기 중일 때. 누를 수 없다.
// 2) 오늘 올린 사진이 검수를 통과(승인)했고 AI 리뷰 상태를 받아 왔을 때(feedbackPhase가 Hidden이 아님).
// 그 밖(비로그인, 열린 주제 없음, 불러오기 실패, AI 리뷰 꺼짐)에는 숨긴다.
val HomeUiState.showsFeedback: Boolean
    get() = feedbackNotice != null ||
        ((hero as? HomeHeroState.Photo)?.isPending == false && feedbackPhase != HomeFeedbackPhase.Hidden)

// 조회 전 상태는 카드를 숨긴다. FAILED는 실패 안내를 보여 주고, 다시 요청할 수 있으면(feedbackCanRequestAgain) 카드를 눌러 바로 다시 요청한다.
typealias HomeFeedbackPhase = AiReviewPhase

/**
 * 오늘 사진이 아직 승인되지 않아 AI 피드백을 받을 수 없을 때, 피드백 카드 자리에 보여 줄 안내의 종류다.
 * 안내 카드는 누를 수 없고, AI 리뷰 API도 부르지 않는다(승인된 사진만 AI 리뷰 대상).
 * 피드와 같이 쓰는 [AiReviewPhase]에는 승인 전 상태가 없어서 홈에서만 따로 둔다.
 */
enum class HomeFeedbackNotice {
    /** 오늘 사진을 아직 올리지 않았다(히어로 [HomeHeroState.Empty]). */
    BeforeUpload,

    /** 오늘 사진을 올렸지만 자동 검사 중([HomeHeroState.Processing])이거나 검수 대기(PENDING)다. */
    AwaitingApproval,
}

/**
 * 오늘 히어로 상태로 승인 전 안내의 종류를 정한다. 안내가 필요 없는 상태(승인됨, 비로그인, 열린 주제 없음, 오류, 로딩)는 null.
 * 예: [HomeHeroState.Empty] → [HomeFeedbackNotice.BeforeUpload], 검수 대기 [HomeHeroState.Photo] → [HomeFeedbackNotice.AwaitingApproval].
 * AI 리뷰 기능이 꺼져 있는지는 여기서 보지 않는다. 호출하는 쪽(HomeViewModel)이 먼저 확인한다.
 */
fun HomeHeroState.feedbackNotice(): HomeFeedbackNotice? = when (this) {
    HomeHeroState.Empty -> HomeFeedbackNotice.BeforeUpload
    HomeHeroState.Processing -> HomeFeedbackNotice.AwaitingApproval
    is HomeHeroState.Photo -> if (isPending) HomeFeedbackNotice.AwaitingApproval else null
    else -> null
}

enum class HomeSectionStatus {
    Loading,
    Ready,
    Error,
}

sealed interface HomeHeroState {
    data object Loading : HomeHeroState

    data object Empty : HomeHeroState

    data object LoginRequired : HomeHeroState

    data object NoOpenTopic : HomeHeroState

    data object Processing : HomeHeroState

    data object Error : HomeHeroState

    data class Photo(
        val imageUrl: String,
        val thumbnailUrl: String = imageUrl,
        val signatureUrl: String? = null,
        val signatureThumbnailUrl: String? = null,
        val isPending: Boolean = false,
        val postId: String? = null,
    ) : HomeHeroState
}

// 맨 위 사진 카드를 눌렀을 때 열 피드. 홈 맨 위 카드는 늘 내가 올린 오늘 사진이라 내 게시물로 연다.
// thumbnailImageUrl은 피드가 상세를 받기 전까지 보여 줄 자리 표시 이미지다.
data class HomeFeedTarget(
    val postId: String,
    val isOwnedByCurrentUser: Boolean,
    val thumbnailImageUrl: String?,
)

// 사진 카드의 게시물 ID를 알 때만 열 피드가 있다. 다른 카드 상태와 게시물 ID를 모르는 사진은 null이다.
// 예: Photo(imageUrl = "thumb", postId = "post-1") → HomeFeedTarget("post-1", true, "thumb")
val HomeHeroState.feedTarget: HomeFeedTarget?
    get() {
        val photo = this as? HomeHeroState.Photo ?: return null
        val postId = photo.postId ?: return null
        return HomeFeedTarget(postId = postId, isOwnedByCurrentUser = true, thumbnailImageUrl = photo.thumbnailUrl)
    }

data class HomeWeekDay(
    val date: LocalDate,
    val post: PostCalendarItem? = null,
)

data class HomeRankingState(
    val status: HomeSectionStatus = HomeSectionStatus.Loading,
    val photos: List<Post> = emptyList(),
)

fun homeWeekDates(date: LocalDate): List<LocalDate> {
    val sunday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    return List(7) { sunday.plusDays(it.toLong()) }
}
