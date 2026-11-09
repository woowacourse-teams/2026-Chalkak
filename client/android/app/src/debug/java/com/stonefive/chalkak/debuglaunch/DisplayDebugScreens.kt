package com.stonefive.chalkak.debuglaunch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.domain.model.AuthorPostsPage
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
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.feature.display.DisplayScreen
import com.stonefive.chalkak.feature.display.DisplayViewModel
import com.stonefive.chalkak.feature.display.component.previewDisplayPhotos
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 전시 화면(#988)
 * - display-latest: 사진 30장인 최신 전시(정렬 탭 있음). 당겨서 새로고침이 몇 번 불렸는지 "새로고침 N"으로 보여 준다.
 *   상태는 실제 DisplayViewModel이 첫 페이지를 받아 만든다(저장소만 아래 가짜, 서버 요청 없음).
 *   새로고침은 가짜다: 횟수를 올리고 isRefreshing을 1초 켰다 끈다(ViewModel.refresh()는 부르지 않는다).
 */
internal val displayDebugScreens: Map<String, @Composable () -> Unit> = mapOf(
    "display-latest" to { DebugDisplayLatest() },
)

private val DEBUG_DISPLAY_DATE: LocalDate = LocalDate.of(2026, 11, 9)
private const val DEBUG_DISPLAY_PHOTO_COUNT = 30
private const val DEBUG_REFRESH_MILLIS = 1_000L

@Composable
private fun DebugDisplayLatest() {
    val viewModel = viewModel {
        DisplayViewModel(
            repository = DebugDisplayPostRepository(),
            dateProvider = { DEBUG_DISPLAY_DATE },
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var refreshCount by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        DisplayScreen(
            uiState = uiState.copy(isRefreshing = isRefreshing),
            onPreviousDateClick = {},
            onNextDateClick = {},
            onSortSelected = {},
            onFeaturedPageChanged = {},
            onEndThresholdChanged = {},
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
            onRefresh = {
                refreshCount++
                isRefreshing = true
                scope.launch {
                    delay(DEBUG_REFRESH_MILLIS)
                    isRefreshing = false
                }
            },
        )
        Text(
            text = "새로고침 $refreshCount",
            modifier = Modifier
                .align(Alignment.Center)
                .safeDrawingPadding()
                .padding(16.dp),
        )
    }
}

/** 첫 페이지만 돌려주는 가짜 저장소. 사진은 앱 리소스 이미지(previewDisplayPhotos)를 id만 바꿔 30장 만든다. */
private class DebugDisplayPostRepository : PostRepository {
    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> = HomeResult.Success(
        PostContent(
            topicDate = query.date,
            topic = "바다",
            photos = List(DEBUG_DISPLAY_PHOTO_COUNT) { index ->
                previewDisplayPhotos[index % previewDisplayPhotos.size].copy(id = "debug-display-$index")
            },
            likedPhotoIds = emptySet(),
            hasNext = false,
            randomSeed = "debug",
        ),
    )

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> =
        HomeResult.Failure(HomeFailure.Network)

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
