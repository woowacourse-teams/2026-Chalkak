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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakFilledIconButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.home.HomeHeroState

const val HOME_HERO_TEST_TAG = "home-hero"

@Composable
fun HomeHeroCard(
    hero: HomeHeroState,
    topic: String,
    onOpenPhotoUpload: () -> Unit,
    onOpenRecord: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(HERO_ASPECT_RATIO)
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

            HomeHeroState.Empty -> EmptyHeroContent(
                topic = topic,
                onOpenPhotoUpload = onOpenPhotoUpload,
                modifier = Modifier.fillMaxSize(),
            )

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
                onOpenRecord = onOpenRecord,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoHeroContent(
    hero: HomeHeroState.Photo,
    onOpenRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(ChalkakTheme.colors.surfaceElevated)
            .semantics(mergeDescendants = true) {
                contentDescription = if (hero.isPending) "오늘 제출한 사진, 검수 대기" else "오늘 제출한 사진"
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onOpenRecord,
            ),
    ) {
        ChalkakSignedImage(
            imageModel = hero.imageUrl,
            signatureModel = hero.signatureUrl,
            contentDescription = null,
            thumbnailImageModel = hero.thumbnailUrl,
            thumbnailSignatureModel = hero.signatureThumbnailUrl,
            contentScale = ContentScale.Crop,
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
private fun EmptyHeroContent(
    topic: String,
    onOpenPhotoUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTopic = topic.ifBlank { "오늘의 주제" }
    Column(
        modifier = modifier
            .background(ChalkakTheme.colors.surface)
            .border(BorderStroke(HeroEmptyBorderWidth, ChalkakTheme.colors.border), ChalkakTheme.shapes.photoCard)
            .semantics(mergeDescendants = true) {
                contentDescription = "$displayTopic 사진을 공유하지 않았어요"
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onOpenPhotoUpload,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "\"$displayTopic\" 사진을 공유하지 않았어요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
        Box(
            modifier = Modifier
                .size(HeroAddButtonSize)
                .clip(CircleShape)
                .background(ChalkakTheme.colors.actionPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = ChalkakTheme.colors.onActionPrimary,
                modifier = Modifier.size(HeroAddIconSize),
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
private val HeroEmptyBorderWidth = 1.dp
private val HeroAddButtonSize = 36.dp
private val HeroAddIconSize = 22.dp
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
            onOpenRecord = {},
            onRetry = {},
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
