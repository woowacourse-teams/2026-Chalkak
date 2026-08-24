package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.RecordPhoto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CALENDAR_PHOTO_ASPECT_RATIO = 1f
private val CalendarGridItemPadding = 6.dp
private val CalendarHorizontalPadding = 20.dp
private val CalendarCellCornerRadius = 7.dp
private val CalendarDividerWidth = 1.dp
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

@Composable
fun RecordCalendarGrid(
    month: YearMonth,
    photos: List<RecordPhoto>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photosByDate = photos.associateBy(RecordPhoto::date)
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarHorizontalPadding)
            .drawBehind {
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
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CalendarGridItemPadding),
                ) {
                    week.forEach { date ->
                        val photo = date?.let { photosByDate[it] }
                        if (photo == null) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(CALENDAR_PHOTO_ASPECT_RATIO),
                            )
                        } else {
                            RecordDayCell(
                                photo = photo,
                                onClick = { onDateClick(photo.date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordDayCell(
    photo: RecordPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CalendarCellCornerRadius)
    val cellModifier = modifier
        .aspectRatio(CALENDAR_PHOTO_ASPECT_RATIO)
        .clip(shape)
        .background(ChalkakTheme.colors.calendarCell)
        .semantics { contentDescription = "${photo.date.format(DateFormatter)} 사진" }
        .clickable(onClick = onClick)
    Box(modifier = cellModifier) {
        ChalkakImage(
            model = photo.imageUrl,
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
    val photo = RecordPhoto(
        date = month.atDay(2),
        imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
        contentDescription = "노을과 전신주",
        title = "물결",
    )

    ChalkakTheme {
        RecordCalendarGrid(
            month = month,
            photos = listOf(photo),
            onDateClick = {},
        )
    }
}
