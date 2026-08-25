package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun HomePhotoList(
    photos: List<Post>,
    likedPhotoIds: Set<String>,
    onLikeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    topContentPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            top = topContentPadding,
            bottom = 48.dp,
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
                onLikeClick = { onLikeClick(photo.id) },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun HomePhotoListPreview() {
    ChalkakTheme {
        HomePhotoList(
            photos = listOf(
                Post(
                    id = "preview-1",
                    imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "노을이 진 하늘과 전신주",
                    title = "안녕하세요 찰캌입니다.",
                    likeCount = 24,
                ),
                Post(
                    id = "preview-2",
                    imageUrl = drawableResourceUrl(R.drawable.preview_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "두 번째 사진",
                    title = null,
                    likeCount = 12,
                ),
            ),
            likedPhotoIds = setOf("preview-1"),
            onLikeClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp),
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
