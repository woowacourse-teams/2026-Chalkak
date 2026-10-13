package com.stonefive.chalkak.feature.home.component

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun HomeIssueSection(
    onIssueClick: () -> Unit,
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
        HomeIssueSamples.forEachIndexed { index, issue ->
            if (index > 0) Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
            HomeIssueCard(issue = issue, onClick = onIssueClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeIssueCard(
    issue: HomeIssue,
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
                model = issue.imageRes,
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
            text = issue.description,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.caption,
        )
    }
}

@Immutable
private data class HomeIssue(
    val title: String,
    val description: String,
    @param:DrawableRes val imageRes: Int,
)

private val HomeIssueSamples = listOf(
    HomeIssue(
        title = "바다에서 구도를 잘 잡는 방법",
        description = "수평선을 활용해 바다를 담는 작은 팁",
        imageRes = R.drawable.record_landscape_photo,
    ),
    HomeIssue(
        title = "지난 주 가장 인기있었던 사진",
        description = "좋아요를 많이 받은 전시 사진 모아보기",
        imageRes = R.drawable.home_feed_photo,
    ),
)

private const val ISSUE_IMAGE_ASPECT_RATIO = 2.35f

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeIssueSectionPreview() {
    ChalkakTheme {
        HomeIssueSection(
            onIssueClick = {},
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
