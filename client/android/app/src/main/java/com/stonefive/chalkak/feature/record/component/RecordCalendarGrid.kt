package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private const val CALENDAR_PHOTO_ASPECT_RATIO = 3f / 4f
private val CalendarGridGap = 6.dp
private val CalendarHorizontalPadding = 20.dp
private val SelectedBorderWidth = 2.dp
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

@Composable
fun RecordCalendarGrid(
    month: YearMonth,
    photos: List<RecordPhoto>,
    selectedDate: LocalDate?,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(CalendarGridGap),
    ) {
        dates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalendarGridGap),
            ) {
                week.forEach { date ->
                    RecordDayCell(
                        date = date,
                        photo = date?.let { photosByDate[it] },
                        selected = date == selectedDate,
                        onClick = { date?.let(onDateClick) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordDayCell(
    date: LocalDate?,
    photo: RecordPhoto?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ChalkakTheme.shapes.photoCard
    val hasPhoto = date != null && photo != null
    val cellDescription = when {
        date == null -> "달력 빈 칸"
        hasPhoto -> "${date.format(DateFormatter)} 사진"
        else -> "${date.format(DateFormatter)} 사진 없음"
    }
    var cellModifier = modifier
        .aspectRatio(CALENDAR_PHOTO_ASPECT_RATIO)
        .clip(shape)
        .background(ChalkakTheme.colors.calendarCell)
        .semantics { contentDescription = cellDescription }
        .border(
            width = 1.dp,
            color = ChalkakTheme.colors.calendarCellBorder,
            shape = shape,
        )

    if (hasPhoto) {
        cellModifier = cellModifier.clickable(onClick = onClick)
    }
    if (selected && hasPhoto) {
        cellModifier = cellModifier.border(
            width = SelectedBorderWidth,
            color = ChalkakTheme.colors.calendarSelection,
            shape = shape,
        )
    }

    Box(modifier = cellModifier) {
        if (photo != null) {
            ChalkakImage(
                model = photo.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
            selectedDate = photo.date,
            onDateClick = {},
        )
    }
}
