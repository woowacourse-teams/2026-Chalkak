package com.stonefive.chalkak.debuglaunch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.BannerPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostUpdate
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.BannerRepository
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.feature.home.HomeScreen
import com.stonefive.chalkak.feature.home.HomeViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 홈 화면
 * - home-before-upload(#1039): 로그인했고 오늘 사진을 아직 올리지 않은 홈. 히어로 아래에 피드백 카드 자리가 있다.
 *   상태는 실제 HomeViewModel이 아래 가짜 저장소 응답(이번 주 기록 없음, 오늘 사진 안 올림)으로 만든다(서버 요청 없음).
 *   피드백 시작은 실제 HomeViewModel.startFeedback()으로 이어진다. AI 리뷰 저장소도 가짜라 요청해도 서버에 가지 않는다.
 */
internal val homeDebugScreens: Map<String, @Composable () -> Unit> = mapOf(
    "home-before-upload" to { DebugHomeBeforeUpload() },
)

private val DEBUG_HOME_DATE: LocalDate = LocalDate.of(2026, 11, 16)

@Composable
private fun DebugHomeBeforeUpload() {
    val viewModel = viewModel {
        val repository = DebugHomeRepository()
        HomeViewModel(
            repository = repository,
            uploadEntryRepository = repository,
            sessionState = MutableStateFlow(UserSessionState.Authenticated("debug-user")),
            bannerRepository = repository,
            aiReviewRepository = DebugAiReviewRepository(),
            dateProvider = { DEBUG_HOME_DATE },
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // HomeRoute가 화면이 보일 때 부르는 순서(미리 불러오기 → 화면에 보임)를 따른다.
    LaunchedEffect(viewModel) {
        viewModel.startPreloading()
        viewModel.onResume()
    }
    HomeScreen(
        uiState = uiState,
        onOpenPhotoUpload = {},
        onOpenRecord = {},
        onOpenDisplay = {},
        onOpenFeed = {},
        onOpenOwnFeed = {},
        onNavigateToBottomBar = {},
        onRetry = {},
        onNotificationClick = {},
        onSettingsClick = {},
        onIssueClick = { _, _ -> },
        onStartFeedback = viewModel::startFeedback,
        onDismissFeedbackMessage = viewModel::dismissFeedbackMessage,
    )
}

/** 오늘의 주제는 있고, 이번 주 기록은 비어 있고, 오늘 사진은 아직 안 올린 계정처럼 답하는 가짜 저장소. */
private class DebugHomeRepository :
    PostRepository,
    PhotoUploadEntryRepository,
    BannerRepository {
    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> =
        HomeResult.Success(PostContent(query.date, "하늘", emptyList(), emptySet()))

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> =
        HomeResult.Success(PostPage(emptyList(), emptySet(), 1, false, null))

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> =
        HomeResult.Success(PostCalendar(month, emptyList()))

    override suspend fun getTodayPostStatus(): TodayPostStatusResult =
        TodayPostStatusResult.Success(TodayPostStatus(DEBUG_HOME_DATE, false, null, null))

    override suspend fun getBanners(
        page: Int,
        pageSize: Int,
    ): HomeResult<BannerPage> = HomeResult.Success(BannerPage(1, pageSize, false, emptyList()))

    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun deletePost(postId: String): HomeResult<Unit> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun updatePost(
        postId: String,
        title: String?,
        description: String?,
        instagramConsent: InstagramConsent,
    ): HomeResult<PostUpdate> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = HomeResult.Failure(HomeFailure.Network)
}

/** 서버에 가지 않는 AI 리뷰 저장소. 오늘 사진이 없으면 불리지 않아야 한다. 불리면 네트워크 실패로 답한다. */
private class DebugAiReviewRepository : AiReviewRepository {
    override suspend fun getReview(postId: String): AiReviewResponseResult =
        AiReviewResponseResult.Failure(AiReviewFailure.Network)

    override suspend fun requestReview(postId: String): AiReviewResponseResult =
        AiReviewResponseResult.Failure(AiReviewFailure.Network)
}
