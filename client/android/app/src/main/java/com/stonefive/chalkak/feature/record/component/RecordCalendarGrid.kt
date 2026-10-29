package com.stonefive.chalkak.feature.record.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CALENDAR_PHOTO_ASPECT_RATIO = 1f
private val CalendarGridItemPadding = 6.dp
private val CalendarCellCornerRadius = 7.dp
private val CalendarDividerWidth = 1.dp
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

@Composable
fun RecordCalendarGrid(
    month: YearMonth,
    posts: List<PostCalendarItem>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    showsDayNumbers: Boolean = false,
    selectedWeekIndex: Int? = null,
) {
    val postsByDate = posts.associateBy(PostCalendarItem::topicDate)
    val leadingEmptyCells = month
        .atDay(1)
        .dayOfWeek.value % 7
    val dates = buildList<LocalDate?> {
        repeat(leadingEmptyCells) { add(null) }
        (1..month.lengthOfMonth()).forEach { day -> add(month.atDay(day)) }
        repeat((7 - size % 7) % 7) { add(null) }
    }
    val calendarDividerColor = ChalkakTheme.colors.calendarCellBorder
        .copy(alpha = 0.6f)
    val weeks = dates.chunked(7)

    Box(
        modifier = modifier.drawBehind {
            val gap = CalendarGridItemPadding.toPx()
            val itemWidth = (size.width - gap * 6) / 7
            val rowHeight = (size.height - gap * (weeks.size - 1)) / weeks.size

            for (column in 1 until 7) {
                val x = itemWidth * column + gap * (column - 0.5f)
                drawLine(
                    color = calendarDividerColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = CalendarDividerWidth.toPx(),
                    cap = StrokeCap.Square,
                )
            }
            for (row in 1 until weeks.size) {
                val y = rowHeight * row + gap * (row - 0.5f)
                drawLine(
                    color = calendarDividerColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = CalendarDividerWidth.toPx(),
                    cap = StrokeCap.Square,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CalendarGridItemPadding),
        ) {
            weeks.forEachIndexed { index, week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (index == selectedWeekIndex) 1f else 0f)
                        .liftedWeek(isLifted = index == selectedWeekIndex, dividerColor = calendarDividerColor),
                    horizontalArrangement = Arrangement.spacedBy(CalendarGridItemPadding),
                ) {
                    week.forEach { date ->
                        val post = date?.let { postsByDate[it] }
                        if (post == null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(CALENDAR_PHOTO_ASPECT_RATIO),
                            ) {
                                if (date != null && showsDayNumbers) {
                                    Text(
                                        text = "${date.dayOfMonth}",
                                        color = ChalkakTheme.colors.textSecondary,
                                        style = ChalkakTheme.typography.caption.copy(
                                            fontFeatureSettings = TABULAR_NUMBERS,
                                        ),
                                        modifier = Modifier.clearAndSetSemantics { },
                                    )
                                }
                            }
                        } else {
                            RecordDayCell(
                                post = post,
                                onClick = { onDateClick(post.topicDate) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 고른 주의 줄을 흰 카드에 얹어 띄운다. 주가 바뀌면 이전 줄은 가라앉고 새 줄이 올라온다.
 * 카드가 격자 구분선을 덮으므로 칸 사이 세로선은 카드 위에 다시 긋는다.
 */
@Composable
private fun Modifier.liftedWeek(
    isLifted: Boolean,
    dividerColor: Color,
): Modifier {
    val lift by animateFloatAsState(
        targetValue = if (isLifted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = WEEK_LIFT_STIFFNESS),
        label = "record-week-lift",
    )
    val cardColor = ChalkakTheme.colors.surfaceElevated
    return this
        .graphicsLayer {
            scaleX = 1f + (WEEK_LIFT_SCALE - 1f) * lift
            scaleY = scaleX
            translationY = -WeekLiftRise.toPx() * lift
            shadowElevation = WeekCardShadow.toPx() * lift
            shape = WeekCardShape
            clip = false
        }.drawBehind {
            if (lift <= 0f) return@drawBehind
            val outsetX = WeekCardOutsetX.toPx()
            val outsetY = WeekCardOutsetY.toPx()
            drawRoundRect(
                color = cardColor.copy(alpha = cardColor.alpha * lift),
                topLeft = Offset(-outsetX, -outsetY),
                size = Size(size.width + outsetX * 2, size.height + outsetY * 2),
                cornerRadius = CornerRadius(WeekCardRadius.toPx()),
            )
            val gap = CalendarGridItemPadding.toPx()
            val itemWidth = (size.width - gap * 6) / 7
            for (column in 1 until 7) {
                val x = itemWidth * column + gap * (column - 0.5f)
                drawLine(
                    color = dividerColor.copy(alpha = dividerColor.alpha * lift),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = CalendarDividerWidth.toPx(),
                    cap = StrokeCap.Square,
                )
            }
        }
}

/** 카드 둘레를 줄보다 조금 넓게 잡은 모양. 그림자가 카드 모양을 따른다. */
private val WeekCardShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = with(density) {
        val outsetX = WeekCardOutsetX.toPx()
        val outsetY = WeekCardOutsetY.toPx()
        Outline.Rounded(
            RoundRect(
                left = -outsetX,
                top = -outsetY,
                right = size.width + outsetX,
                bottom = size.height + outsetY,
                cornerRadius = CornerRadius(WeekCardRadius.toPx()),
            ),
        )
    }
}

private const val WEEK_LIFT_SCALE = 1.03f

/** 0.42초 응답의 스프링. 강성은 (2π / 0.42)²이다. */
private const val WEEK_LIFT_STIFFNESS = 224f
private val WeekLiftRise = 2.dp
private val WeekCardRadius = 10.dp
private val WeekCardOutsetX = 6.dp
private val WeekCardOutsetY = 3.dp
private val WeekCardShadow = 6.dp

@Composable
private fun RecordDayCell(
    post: PostCalendarItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CalendarCellCornerRadius)
    val cellModifier = modifier
        .aspectRatio(CALENDAR_PHOTO_ASPECT_RATIO)
        .clip(shape)
        .background(ChalkakTheme.colors.calendarCell)
        .semantics { contentDescription = "${post.topicDate.format(DateFormatter)} 사진" }
        .clickable(onClick = onClick)
    Box(modifier = cellModifier) {
        ChalkakImage(
            model = post.thumbnailImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordCalendarGridPreview() {
    val month = YearMonth.of(2026, 8)
    val post = PostCalendarItem(
        postId = "preview-post",
        topicDate = month.atDay(2),
        thumbnailImageUrl = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.home_feed_photo}",
        status = PostStatus.APPROVED,
    )

    ChalkakTheme {
        RecordCalendarGrid(
            month = month,
            posts = listOf(post),
            onDateClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
    }
}
