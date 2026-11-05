package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakFilledIconButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.component.image.aspectRatioForSize
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.home.HomeHeroState

const val HOME_HERO_TEST_TAG = "home-hero"

@Composable
fun HomeHeroCard(
    hero: HomeHeroState,
    topic: String,
    onOpenPhotoUpload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
) {
    // 내 사진은 피드처럼 원본 비율로 보여 준다. 비율을 알기 전과 다른 상태는 기본 비율을 쓴다.
    var photoAspectRatio by remember((hero as? HomeHeroState.Photo)?.imageUrl) { mutableStateOf<Float?>(null) }
    val aspectRatio = when (hero) {
        HomeHeroState.Empty, HomeHeroState.LoginRequired -> HERO_EMPTY_ASPECT_RATIO
        is HomeHeroState.Photo -> photoAspectRatio ?: HERO_ASPECT_RATIO
        else -> HERO_ASPECT_RATIO
    }
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(ChalkakTheme.shapes.photoCard)
            .testTag(HOME_HERO_TEST_TAG),
    ) {
        when (hero) {
            HomeHeroState.Loading -> HeroStatusContent("사진을 확인하고 있어요") {
                CircularProgressIndicator(
                    color = ChalkakTheme.colors.actionPrimary,
                    modifier = Modifier.size(HeroProgressSize),
                )
            }

            HomeHeroState.Empty -> {
                val displayTopic = topic.ifBlank { "오늘의 주제" }
                HeroActionContent(
                    message = "아직 \"$displayTopic\" 사진을 전시하지 않았어요.\n작가님의 시선을 공유해주세요!",
                    actionText = "사진 전시하기",
                    actionDescription = "$displayTopic 사진 전시하기",
                    onClick = onOpenPhotoUpload,
                    showsAddIcon = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            HomeHeroState.LoginRequired -> HeroActionContent(
                message = "사진을 전시하려면 로그인이 필요해요",
                actionText = "로그인하기",
                actionDescription = "로그인하기",
                onClick = onLogin,
                modifier = Modifier.fillMaxSize(),
            )

            HomeHeroState.NoOpenTopic -> HeroStatusContent("지금은 참여할 수 있는 주제가 없어요")

            HomeHeroState.Processing -> HeroStatusContent("사진을 처리하고 있어요")

            HomeHeroState.Error -> HeroStatusContent("사진 상태를 불러오지 못했어요") {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.md))
                ChalkakFilledIconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "홈 다시 시도",
                    )
                }
            }

            is HomeHeroState.Photo -> PhotoHeroContent(
                hero = hero,
                onImageAspectRatioAvailable = { photoAspectRatio = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoHeroContent(
    hero: HomeHeroState.Photo,
    onImageAspectRatioAvailable: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(ChalkakTheme.colors.surfaceElevated)
            .semantics(mergeDescendants = true) {
                contentDescription = if (hero.isPending) "오늘 제출한 사진, 검수 대기" else "오늘 제출한 사진"
            },
    ) {
        ChalkakSignedImage(
            imageModel = hero.imageUrl,
            signatureModel = hero.signatureUrl,
            contentDescription = null,
            thumbnailImageModel = hero.thumbnailUrl,
            thumbnailSignatureModel = hero.signatureThumbnailUrl,
            contentScale = ContentScale.Crop,
            onImageSuccess = { state ->
                aspectRatioForSize(state.result.image.width, state.result.image.height)
                    ?.let(onImageAspectRatioAvailable)
            },
            onThumbnailImageSuccess = { state ->
                aspectRatioForSize(state.result.image.width, state.result.image.height)
                    ?.let(onImageAspectRatioAvailable)
            },
            modifier = Modifier.fillMaxSize(),
            signatureModifier = Modifier.size(width = HeroSignatureWidth, height = HeroSignatureHeight),
        )
        if (hero.isPending) {
            PendingBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ChalkakTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun HeroActionContent(
    message: String,
    actionText: String,
    actionDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsAddIcon: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(ChalkakTheme.colors.surface)
            .border(BorderStroke(HeroEmptyBorderWidth, ChalkakTheme.colors.border), ChalkakTheme.shapes.photoCard)
            .semantics(mergeDescendants = true) {
                contentDescription = actionDescription
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.lg),
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
        Row(
            modifier = Modifier
                .clip(ChalkakTheme.shapes.button)
                .background(ChalkakTheme.colors.actionPrimary)
                .padding(horizontal = ChalkakTheme.spacing.lg, vertical = ChalkakTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showsAddIcon) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = ChalkakTheme.colors.onActionPrimary,
                    modifier = Modifier.size(HeroAddIconSize),
                )
                Spacer(modifier = Modifier.width(ChalkakTheme.spacing.sm))
            }
            Text(
                text = actionText,
                color = ChalkakTheme.colors.onActionPrimary,
                style = ChalkakTheme.typography.subheadline,
            )
        }
    }
}

@Composable
private fun HeroStatusContent(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.surface)
            .border(BorderStroke(HeroEmptyBorderWidth, ChalkakTheme.colors.border), ChalkakTheme.shapes.photoCard),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.subheadline,
        )
        action()
    }
}

@Composable
private fun PendingBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(ChalkakTheme.shapes.pill)
            .background(ChalkakTheme.colors.scrim)
            .padding(horizontal = ChalkakTheme.spacing.md, vertical = ChalkakTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "검수 대기",
            color = ChalkakTheme.colors.textOnImage,
            style = ChalkakTheme.typography.caption,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val HERO_ASPECT_RATIO = 5f / 6f
private const val HERO_EMPTY_ASPECT_RATIO = 19f / 10f
private val HeroEmptyBorderWidth = 1.dp
private val HeroAddIconSize = 20.dp
private val HeroProgressSize = 28.dp
private val HeroSignatureWidth = 64.dp
private val HeroSignatureHeight = 48.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeHeroCardPreview() {
    ChalkakTheme {
        HomeHeroCard(
            hero = HomeHeroState.Empty,
            topic = "하늘",
            onOpenPhotoUpload = {},
            onRetry = {},
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeHeroCardLoginRequiredPreview() {
    ChalkakTheme {
        HomeHeroCard(
            hero = HomeHeroState.LoginRequired,
            topic = "하늘",
            onOpenPhotoUpload = {},
            onRetry = {},
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
