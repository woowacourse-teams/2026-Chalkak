package com.stonefive.chalkak.feature.record.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PhotoColor
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.YearMonth

/**
 * 한 해를 한 화면에 보는 연간 달력.
 * - 달마다 날짜를 작은 칸으로 그리고, 기록이 있는 날은 그 사진의 대표색으로 칠한다.
 * - 달을 누르면 그 달의 월간 화면으로 들어간다. 아직 오지 않은 달과 기록이 없는 달은 누를 수 없다.
 *   지금 보고 있던 달은 기록이 없어도 돌아갈 수 있게 남겨 둔다.
 * - 다른 해는 위의 화살표로 옮겨 간다.
 *
 * @param shownMonth 연간으로 나오기 전에 보던 달.
 */
@Composable
fun RecordYearCalendar(
    year: Int,
    latestMonth: YearMonth,
    shownMonth: YearMonth,
    postsByMonth: Map<YearMonth, List<PostCalendarItem>>,
    photoColors: Map<String, PhotoColor>,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    canGoPreviousYear: Boolean = false,
    canGoNextYear: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = YearHorizontalPadding,
                top = ChalkakTheme.spacing.sm,
                end = YearHorizontalPadding,
                bottom = ChalkakTheme.spacing.xxl,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = year, label = "record-year") { shownYear ->
                Text(
                    text = "$shownYear",
                    color = ChalkakTheme.colors.textPrimary,
                    style = ChalkakTheme.typography.title1
                        .copy(fontFeatureSettings = TABULAR_NUMBERS),
                    modifier = Modifier.semantics {
                        contentDescription = "${shownYear}년"
                        heading()
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            YearButton("이전 해", R.drawable.ic_display_arrow_left, canGoPreviousYear, onPreviousYear)
            YearButton("다음 해", R.drawable.ic_display_arrow_right, canGoNextYear, onNextYear)
        }

        Column(verticalArrangement = Arrangement.spacedBy(MonthRowSpacing)) {
            (1..12).chunked(COLUMN_COUNT).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(MonthSpacing)) {
                    row.forEach { number ->
                        val month = YearMonth.of(year, number)
                        RecordYearMonthCell(
                            month = month,
                            posts = postsByMonth[month],
                            photoColors = photoColors,
                            latestMonth = latestMonth,
                            shownMonth = shownMonth,
                            onClick = { onSelectMonth(month) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearButton(
    title: String,
    iconRes: Int,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(enabled = isEnabled, onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = if (isEnabled) ChalkakTheme.colors.iconPrimary else ChalkakTheme.colors.textInactive,
        )
    }
}

/**
 * @param posts 그 달의 기록. 아직 받지 않은 달은 null이며, 흐리게 하지 않고 누르지만 못하게 둔다(받자마자 깜빡이지 않게).
 */
@Composable
private fun RecordYearMonthCell(
    month: YearMonth,
    posts: List<PostCalendarItem>?,
    photoColors: Map<String, PhotoColor>,
    latestMonth: YearMonth,
    shownMonth: YearMonth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasNoRecords = posts?.isEmpty() == true
    val isAvailable = month <= latestMonth && (!hasNoRecords || month == shownMonth)
    val canEnter = isAvailable && (!posts.isNullOrEmpty() || month == shownMonth)

    Column(
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
        modifier = modifier
            .alpha(if (isAvailable) 1f else UNAVAILABLE_ALPHA)
            .clearAndSetSemantics {
                contentDescription = "${month.monthValue}월, 기록 ${posts.orEmpty().size}개"
            }.clickable(enabled = canEnter, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = "${month.monthValue}월",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
        )
        RecordYearMonthDays(month = month, posts = posts.orEmpty(), photoColors = photoColors)
    }
}

/** 한 달의 날짜 칸. 일요일부터 한 줄에 7칸. */
@Composable
private fun RecordYearMonthDays(
    month: YearMonth,
    posts: List<PostCalendarItem>,
    photoColors: Map<String, PhotoColor>,
) {
    val postsByDay = posts.associateBy { it.topicDate.dayOfMonth }
    val leading = month
        .atDay(1)
        .dayOfWeek.value % 7
    val cells = List<Int?>(leading) { null } + (1..month.lengthOfMonth()).toList()
    val shape = RoundedCornerShape(DayCornerRadius)
    val colors = ChalkakTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(DaySpacing)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(DaySpacing)) {
                (0 until 7).forEach { offset ->
                    val day = week.getOrNull(offset)
                    val cellModifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                    if (day == null) {
                        Spacer(modifier = cellModifier)
                    } else {
                        // 기록이 없는 날은 빈 칸 색, 색을 아직 구하지 못한 사진은 조금 진한 자리표시 색.
                        val post = postsByDay[day]
                        val fill = when {
                            post == null -> colors.calendarCell

                            else -> photoColors[post.postId]?.let { Color(it.red, it.green, it.blue) }
                                ?: colors.calendarCellBorder
                        }
                        Box(modifier = cellModifier.background(fill, shape))
                    }
                }
            }
        }
    }
}

private val YearHorizontalPadding = 20.dp
private const val COLUMN_COUNT = 3
private val MonthSpacing = 18.dp
private val MonthRowSpacing = 28.dp
private val DaySpacing = 2.5.dp
private val DayCornerRadius = 2.5.dp
private const val UNAVAILABLE_ALPHA = 0.35f

@Preview(showBackground = true, widthDp = 393, heightDp = 760)
@Composable
private fun RecordYearCalendarPreview() {
    val month = YearMonth.of(2026, 8)
    val posts = listOf(2, 5, 9, 14).map { day ->
        PostCalendarItem(
            postId = "preview-$day",
            topicDate = month.atDay(day),
            thumbnailImageUrl = "",
            status = PostStatus.APPROVED,
        )
    }
    ChalkakTheme {
        RecordYearCalendar(
            year = 2026,
            latestMonth = YearMonth.of(2026, 10),
            shownMonth = month,
            postsByMonth = mapOf(month to posts, YearMonth.of(2026, 9) to emptyList()),
            photoColors = mapOf("preview-2" to PhotoColor(0.8f, 0.5f, 0.3f)),
            onPreviousYear = {},
            onNextYear = {},
            onSelectMonth = {},
            canGoPreviousYear = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
