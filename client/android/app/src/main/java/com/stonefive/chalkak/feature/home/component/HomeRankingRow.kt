package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.rememberSharedPhotoSource
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.HomeRankingState
import com.stonefive.chalkak.feature.home.HomeSectionStatus
import java.time.LocalDate

@Composable
fun HomeRankingRow(
    title: String,
    ranking: HomeRankingState,
    displayDate: LocalDate,
    onOpenDisplay: (LocalDate) -> Unit,
    onOpenFeed: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HomeSectionHeader(
            title = title,
            moreContentDescription = "$title 더보기",
            onMoreClick = { onOpenDisplay(displayDate) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        when (ranking.status) {
            HomeSectionStatus.Loading -> RankingLoading()

            HomeSectionStatus.Error -> RankingMessage("$title 사진을 불러오지 못했어요")

            HomeSectionStatus.Ready -> if (ranking.photos.isEmpty()) {
                RankingMessage("아직 올라온 사진이 없어요")
            } else {
                RankingPhotoRow(
                    photos = ranking.photos,
                    onOpenFeed = onOpenFeed,
                    transitionSourceId = "home-ranking:$displayDate",
                )
            }
        }
    }
}

@Composable
private fun RankingPhotoRow(
    photos: List<Post>,
    onOpenFeed: (Post) -> Unit,
    transitionSourceId: String,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ChalkakTheme.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
    ) {
        items(items = photos.take(RANKING_PREVIEW_COUNT), key = Post::id) { photo ->
            RankingPhotoCard(
                photo = photo,
                onClick = { onOpenFeed(photo) },
                transitionSourceId = "$transitionSourceId:${photo.id}",
            )
        }
    }
}

@Composable
private fun RankingPhotoCard(
    photo: Post,
    onClick: () -> Unit,
    transitionSourceId: String,
    modifier: Modifier = Modifier,
) {
    val photoSource = rememberSharedPhotoSource(
        key = PhotoTransitionKey(photo.id, transitionSourceId),
        imageModel = photo.thumbnailImageUrl,
        signatureModel = null,
        contentScale = ContentScale.Crop,
        sourceShape = ChalkakTheme.shapes.small,
    )
    Box(
        modifier = modifier
            .width(RankingItemWidth)
            .aspectRatio(1f)
            .clip(ChalkakTheme.shapes.small)
            .background(ChalkakTheme.colors.calendarCell)
            .semantics(mergeDescendants = true) { contentDescription = photo.contentDescription }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = {
                    photoSource.select()
                    onClick()
                },
            ),
    ) {
        ChalkakImage(
            model = photo.thumbnailImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = photoSource.onSuccess,
            modifier = photoSource.modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RankingLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RankingStatusHeight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = ChalkakTheme.colors.actionPrimary,
            modifier = Modifier.size(RankingProgressSize),
        )
    }
}

@Composable
private fun RankingMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RankingStatusHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.footnote,
        )
    }
}

private const val RANKING_PREVIEW_COUNT = 5
private val RankingItemWidth = 82.dp
private val RankingStatusHeight = 76.dp
private val RankingProgressSize = 24.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeRankingRowPreview() {
    ChalkakTheme {
        HomeRankingRow(
            title = "어제 랭킹",
            ranking = HomeRankingState(
                status = HomeSectionStatus.Ready,
                photos = listOf(
                    previewRankingPost("1", R.drawable.preview_photo),
                    previewRankingPost("2", R.drawable.home_feed_photo),
                ),
            ),
            displayDate = LocalDate.of(2026, 8, 2),
            onOpenDisplay = {},
            onOpenFeed = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewRankingPost(
    id: String,
    image: Int,
): Post = Post(
    id = "preview-$id",
    originalImageUrl = "android.resource://com.stonefive.chalkak/$image",
    thumbnailImageUrl = "android.resource://com.stonefive.chalkak/$image",
    signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
    signatureThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
    contentDescription = "랭킹 사진 $id",
    title = "사진 $id",
    likeCount = id.toInt(),
)
