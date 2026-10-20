package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Banner

@Composable
fun HomeIssueSection(
    banner: Banner?,
    onIssueClick: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = "ISSUE",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        if (banner == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ISSUE_IMAGE_ASPECT_RATIO)
                    .clip(ChalkakTheme.shapes.large)
                    .background(ChalkakTheme.colors.surfaceElevated),
            )
        } else {
            HomeIssueCard(
                issue = banner,
                onClick = { onIssueClick("ISSUE", banner.webViewUrl) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeIssueCard(
    issue: Banner,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = issue.title }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ISSUE_IMAGE_ASPECT_RATIO)
                .clip(ChalkakTheme.shapes.large)
                .background(ChalkakTheme.colors.surfaceElevated),
        ) {
            ChalkakImage(
                model = issue.thumbnailUrl,
                error = ColorPainter(ChalkakTheme.colors.surfaceElevated),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        Text(
            text = issue.title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        Text(
            text = issue.subtitle,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.caption,
        )
    }
}

private const val ISSUE_IMAGE_ASPECT_RATIO = 1672f / 941f

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeIssueSectionPreview() {
    ChalkakTheme {
        HomeIssueSection(
            banner = Banner(
                id = "preview-banner",
                title = "함께 만들어주신 찰캌",
                subtitle = "베타테스트에 함께해주셔서 감사합니다",
                webViewUrl = "https://example.com/issue",
                thumbnailUrl = "https://example.com/banner.jpg",
                createdAt = "2026-10-20T08:08:11.001Z",
            ),
            onIssueClick = { _, _ -> },
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
