package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakFilledIconButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.home.HomeSectionStatus
import com.stonefive.chalkak.feature.home.HomeWeekDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeWeeklyRecord(
    days: List<HomeWeekDay>,
    status: HomeSectionStatus,
    onOpenRecord: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        HomeSectionHeader(
            title = "이번 주 기록",
            suffix = "${days.count { it.post != null }}/7",
            moreContentDescription = "이번 주 기록 더보기",
            onMoreClick = onOpenRecord,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        when (status) {
            HomeSectionStatus.Loading -> WeeklyLoading()
            HomeSectionStatus.Error -> WeeklyError(onRetry)
            HomeSectionStatus.Ready -> WeeklyRecordRow(days)
        }
    }
}

@Composable
private fun WeeklyRecordRow(days: List<HomeWeekDay>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        days.forEach { day ->
            WeeklyRecordCell(day = day, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeeklyRecordCell(
    day: HomeWeekDay,
    modifier: Modifier = Modifier,
) {
    val post = day.post
    val description = buildString {
        append(day.date.format(WeeklyDateFormatter))
        append(" 기록 ")
        append(if (post == null) "없음" else "있음")
        if (post?.status == PostStatus.PENDING) append(", 검수 대기")
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(ChalkakTheme.shapes.small)
            .background(ChalkakTheme.colors.calendarCell)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (post != null) {
            ChalkakImage(
                model = post.thumbnailImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (post.status == PostStatus.PENDING) {
                Text(
                    text = "대기",
                    color = ChalkakTheme.colors.textOnImage,
                    style = ChalkakTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(ChalkakTheme.colors.scrim)
                        .padding(vertical = WeeklyBadgePadding),
                )
            }
        } else {
            Text(
                text = day.date.format(WeeklyDayFormatter),
                color = ChalkakTheme.colors.textSecondary,
                style = ChalkakTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun WeeklyLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeeklyStatusHeight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = ChalkakTheme.colors.actionPrimary,
            modifier = Modifier.size(WeeklyProgressSize),
        )
    }
}

@Composable
private fun WeeklyError(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeeklyStatusHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "기록을 불러오지 못했어요",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.footnote,
        )
        Spacer(modifier = Modifier.width(ChalkakTheme.spacing.sm))
        ChalkakFilledIconButton(onClick = onRetry) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "기록 다시 시도")
        }
    }
}

@Composable
fun HomeSectionHeader(
    title: String,
    moreContentDescription: String,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            fontWeight = FontWeight.Bold,
        )
        if (suffix != null) {
            Spacer(modifier = Modifier.width(ChalkakTheme.spacing.xs))
            Text(
                text = suffix,
                color = ChalkakTheme.colors.textSecondary,
                style = ChalkakTheme.typography.caption,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .semantics { contentDescription = moreContentDescription }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onMoreClick,
                ).padding(vertical = ChalkakTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "보러가기",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.caption,
            )
            Spacer(modifier = Modifier.width(ChalkakTheme.spacing.xs))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(SectionChevronSize),
            )
        }
    }
}

private val WeeklyBadgePadding = 1.dp
private val SectionChevronSize = 12.dp

private val WeeklyStatusHeight = 48.dp
private val WeeklyProgressSize = 24.dp
private val WeeklyDayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
private val WeeklyDateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeWeeklyRecordPreview() {
    val today = LocalDate.of(2026, 8, 3)
    ChalkakTheme {
        HomeWeeklyRecord(
            days = List(7) { index ->
                HomeWeekDay(
                    date = today.plusDays(index.toLong()),
                    post = if (index % 2 == 0) {
                        PostCalendarItem(
                            postId = "preview-$index",
                            topicDate = today.plusDays(index.toLong()),
                            thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}",
                            status = PostStatus.APPROVED,
                        )
                    } else {
                        null
                    },
                )
            },
            status = HomeSectionStatus.Ready,
            onOpenRecord = {},
            onRetry = {},
            modifier = Modifier.fillMaxWidth().padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeSectionHeaderPreview() {
    ChalkakTheme {
        HomeSectionHeader(
            title = "이번 주 기록",
            moreContentDescription = "이번 주 기록 더보기",
            onMoreClick = {},
            modifier = Modifier.fillMaxWidth(),
            suffix = "5/7",
        )
    }
}
