package com.stonefive.chalkak.feature.authorposts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.stonefive.chalkak.core.designsystem.component.empty.ChalkakEmptyPostContent
import com.stonefive.chalkak.core.designsystem.component.image.sharedAuthorSignature
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.display.DisplayErrorContent
import com.stonefive.chalkak.feature.display.component.DisplayPhotoGrid

@Composable
fun AuthorPostsRoute(
    authorId: String,
    signatureThumbnailImageUrl: String?,
    isMine: Boolean,
    onNavigateBack: () -> Unit,
    onPhotoClick: (Post) -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
    removedPostId: String? = null,
    viewModel: AuthorPostsViewModel = viewModel(
        key = "author-posts-$authorId",
        factory = AuthorPostsViewModel.factory(authorId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(removedPostId) {
        removedPostId?.let(viewModel::onPostDeleted)
    }
    LaunchedEffect(uiState.isLoginRequired) {
        if (uiState.isLoginRequired) onLoginRequired()
    }
    AuthorPostsScreen(
        uiState = uiState,
        authorId = authorId,
        signatureThumbnailImageUrl = signatureThumbnailImageUrl,
        isMine = isMine,
        onNavigateBack = onNavigateBack,
        onPhotoClick = onPhotoClick,
        onRetry = viewModel::retry,
        onEndThresholdChanged = viewModel::onEndThresholdChanged,
        modifier = modifier,
    )
}

@Composable
fun AuthorPostsScreen(
    uiState: AuthorPostsUiState,
    signatureThumbnailImageUrl: String?,
    isMine: Boolean,
    onNavigateBack: () -> Unit,
    onPhotoClick: (Post) -> Unit,
    onRetry: () -> Unit,
    onEndThresholdChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    authorId: String? = null,
) {
    val photos = remember(uiState.posts, signatureThumbnailImageUrl, isMine) {
        uiState.posts.map { it.toDisplayPost(signatureThumbnailImageUrl, isMine) }
    }
    val gridState = rememberLazyStaggeredGridState()
    var collapsedFraction by rememberSaveable { mutableFloatStateOf(0f) }
    val collapseRangePx = with(LocalDensity.current) { (ExpandedHeaderHeight - CollapsedHeaderHeight).toPx() }
    val scrollConnection = remember(gridState, collapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && gridState.canScrollBackward) return Offset.Zero
                val previous = collapsedFraction
                collapsedFraction = (previous - available.y / collapseRangePx).coerceIn(0f, 1f)
                return Offset(0f, (previous - collapsedFraction) * collapseRangePx)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y > 0f) onPreScroll(available, source) else Offset.Zero
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .nestedScroll(scrollConnection),
    ) {
        AuthorPostsTopBar(
            authorId = authorId,
            signatureThumbnailImageUrl = signatureThumbnailImageUrl,
            isMine = isMine,
            isSignatureVisible = !uiState.isAuthorUnavailable,
            collapsedFraction = if (uiState.isAuthorUnavailable) 1f else collapsedFraction,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
        )
        if (photos.isEmpty() && !uiState.isAuthorUnavailable) {
            AuthorPostsDescription(
                isMine = isMine,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = ChalkakTheme.spacing.screenHorizontal,
                    vertical = ChalkakTheme.spacing.lg,
                ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("author-posts-body"),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color = ChalkakTheme.colors.actionPrimary,
                    modifier = Modifier.align(Alignment.Center).testTag("author-posts-loading"),
                )

                uiState.isAuthorUnavailable -> ChalkakEmptyPostContent(
                    title = "더 이상 사진을 볼 수 없어요",
                    description = null,
                    modifier = Modifier.fillMaxSize(),
                )

                photos.isEmpty() && uiState.hasError -> DisplayErrorContent(
                    message = "사진을 불러오지 못했어요",
                    onRetryClick = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )

                photos.isEmpty() -> ChalkakEmptyPostContent(
                    title = "아직 공개된 사진이 없어요",
                    description = null,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    DisplayPhotoGrid(
                        photos = photos,
                        state = gridState,
                        onPhotoClick = onPhotoClick,
                        onEndThresholdChanged = onEndThresholdChanged,
                        isLoadingNext = uiState.isLoadingNext,
                        showLikeCount = false,
                        showSignature = false,
                        header = {
                            AuthorPostsDescription(
                                isMine = isMine,
                                modifier = Modifier.fillMaxWidth().padding(vertical = ChalkakTheme.spacing.lg),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.hasError) {
                        DisplayErrorContent(
                            message = "사진을 불러오지 못했어요",
                            onRetryClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorPostsTopBar(
    authorId: String?,
    signatureThumbnailImageUrl: String?,
    isMine: Boolean,
    isSignatureVisible: Boolean,
    collapsedFraction: Float,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerHeight = lerp(ExpandedHeaderHeight, CollapsedHeaderHeight, collapsedFraction)
    val signatureHeight = lerp(ExpandedSignatureHeight, SignatureHeight, collapsedFraction)
    val signatureWidth = lerp(ExpandedSignatureWidth, SignatureWidth, collapsedFraction)
    Box(modifier = modifier.height(headerHeight).testTag("author-posts-header")) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = BackButtonStart, y = BackButtonTop)
                .size(TopBarTouchSize)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(interactionSource = null, indication = null, onClick = onNavigateBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(ChalkakTheme.spacing.xl),
            )
        }
        if (isSignatureVisible) {
            AsyncImage(
                model = signatureThumbnailImageUrl,
                contentDescription = if (isMine) "내 사인" else "작성자 사인",
                colorFilter = ColorFilter.tint(ChalkakTheme.colors.iconPrimary),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        y = lerp(
                            ExpandedSignatureTop,
                            BackButtonTop + (TopBarTouchSize - SignatureHeight) / 2,
                            collapsedFraction,
                        ),
                    ).sharedAuthorSignature(authorId)
                    .size(signatureWidth, signatureHeight)
                    .testTag("author-posts-signature"),
            )
        }
    }
}

@Composable
private fun AuthorPostsDescription(
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (isMine) "작가님이 공개한 시선들이에요" else "이 작가님의 시선을 감상해보세요",
        textAlign = TextAlign.Start,
        color = ChalkakTheme.colors.textSecondary,
        style = ChalkakTheme.typography.subheadline,
        modifier = modifier,
    )
}

private fun AuthorPost.toDisplayPost(
    signatureUrl: String?,
    isMine: Boolean,
): Post = Post(
    id = id,
    originalImageUrl = originalImageUrl,
    thumbnailImageUrl = thumbnailImageUrl ?: originalImageUrl,
    signatureOriginalImageUrl = signatureUrl.orEmpty(),
    signatureThumbnailImageUrl = signatureUrl,
    contentDescription = "${topic.date} ${topic.title}",
    title = null,
    likeCount = 0,
    isOwnedByCurrentUser = isMine,
)

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AuthorPostsScreenPreview() {
    ChalkakTheme {
        AuthorPostsScreen(
            uiState = AuthorPostsUiState(isLoading = false),
            signatureThumbnailImageUrl = null,
            isMine = false,
            onNavigateBack = {},
            onPhotoClick = {},
            onRetry = {},
            onEndThresholdChanged = {},
        )
    }
}

private val TopBarTouchSize = 44.dp
private val SignatureWidth = 72.dp
private val SignatureHeight = 32.dp

private val ExpandedHeaderHeight = 112.dp
private val ExpandedSignatureWidth = 144.dp
private val ExpandedSignatureHeight = 64.dp
private val ExpandedSignatureTop = 40.dp

private val BackButtonStart = 8.dp
private val BackButtonTop = 10.dp
private val CollapsedHeaderHeight = 62.dp
