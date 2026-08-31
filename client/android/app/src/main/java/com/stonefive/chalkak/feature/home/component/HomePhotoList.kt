package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.HOME_NEXT_LOADING_TEST_TAG
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomePhotoList(
    photos: List<Post>,
    likedPhotoIds: Set<String>,
    isLoadingNext: Boolean,
    areLikesEnabled: Boolean,
    onLikeClick: (String) -> Unit,
    onEndThresholdChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    topContentPadding: Dp = 0.dp,
) {
    LaunchedEffect(state, photos.size) {
        snapshotFlow { state.isNearEnd(photos.size) }
            .distinctUntilChanged()
            .collect(onEndThresholdChanged)
    }

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            top = topContentPadding,
            bottom = ChalkakTheme.spacing.xxl + ChalkakTheme.spacing.sm,
        ),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xxl),
    ) {
        items(
            items = photos,
            key = Post::id,
        ) { photo ->
            HomePhotoCard(
                photo = photo,
                isLiked = photo.id in likedPhotoIds,
                isLikeEnabled = areLikesEnabled,
                onLikeClick = { onLikeClick(photo.id) },
            )
        }
        if (isLoadingNext) {
            item(key = NEXT_PAGE_LOADING_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HOME_NEXT_LOADING_TEST_TAG)
                        .padding(ChalkakTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                }
            }
        }
    }
}

private fun LazyListState.isNearEnd(totalItemCount: Int): Boolean {
    if (totalItemCount == 0) return false
    val lastVisibleIndex = layoutInfo.visibleItemsInfo
        .lastOrNull()
        ?.index
        ?: return false
    return lastVisibleIndex >= totalItemCount - END_THRESHOLD - 1
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun HomePhotoListPreview() {
    ChalkakTheme {
        HomePhotoList(
            photos = listOf(
                Post(
                    id = "preview-1",
                    originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                    signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "노을이 진 하늘과 전신주",
                    title = "안녕하세요 찰캌입니다.",
                    likeCount = 24,
                ),
                Post(
                    id = "preview-2",
                    originalImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                    thumbnailImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                    signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                    signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "두 번째 사진",
                    title = null,
                    likeCount = 12,
                ),
            ),
            likedPhotoIds = setOf("preview-1"),
            isLoadingNext = true,
            areLikesEnabled = true,
            onLikeClick = {},
            onEndThresholdChanged = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(HOME_PHOTO_LIST_PREVIEW_HEIGHT),
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"

private const val END_THRESHOLD = 2
private const val NEXT_PAGE_LOADING_KEY = "next-page-loading"
private val HOME_PHOTO_LIST_PREVIEW_HEIGHT = 700.dp
