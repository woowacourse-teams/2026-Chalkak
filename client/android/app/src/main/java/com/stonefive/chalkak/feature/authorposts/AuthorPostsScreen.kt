package com.stonefive.chalkak.feature.authorposts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.stonefive.chalkak.core.designsystem.component.empty.ChalkakEmptyPostContent
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
) {
    val photos = remember(uiState.posts, signatureThumbnailImageUrl, isMine) {
        uiState.posts.map { it.toDisplayPost(signatureThumbnailImageUrl, isMine) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding(),
    ) {
        AuthorPostsTopBar(
            signatureThumbnailImageUrl = signatureThumbnailImageUrl,
            isMine = isMine,
            isSignatureVisible = !uiState.isAuthorUnavailable,
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.md),
        )
        if (photos.isEmpty() && !uiState.isAuthorUnavailable) {
            AuthorPostsDescription(
                isMine = isMine,
                modifier = Modifier.padding(
                    horizontal = ChalkakTheme.spacing.screenHorizontal,
                    vertical = ChalkakTheme.spacing.lg,
                ),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color = ChalkakTheme.colors.actionPrimary,
                    modifier = Modifier.align(Alignment.Center),
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

                else -> Column {
                    DisplayPhotoGrid(
                        photos = photos,
                        onPhotoClick = onPhotoClick,
                        onEndThresholdChanged = onEndThresholdChanged,
                        isLoadingNext = uiState.isLoadingNext,
                        showLikeCount = false,
                        showSignature = false,
                        header = {
                            AuthorPostsDescription(
                                isMine = isMine,
                                modifier = Modifier.padding(vertical = ChalkakTheme.spacing.lg),
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
    signatureThumbnailImageUrl: String?,
    isMine: Boolean,
    isSignatureVisible: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.size(TopBarTouchSize)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(ChalkakTheme.spacing.xl),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (isSignatureVisible) {
                AsyncImage(
                    model = signatureThumbnailImageUrl,
                    contentDescription = if (isMine) "내 사인" else "작성자 사인",
                    colorFilter = ColorFilter.tint(ChalkakTheme.colors.iconPrimary),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(SignatureWidth, SignatureHeight),
                )
            }
        }
        Spacer(modifier = Modifier.size(TopBarTouchSize))
    }
}

@Composable
private fun AuthorPostsDescription(
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (isMine) "작가님이 공개한 시선들이에요" else "한 작가님의 시선을 따라가 보세요",
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
