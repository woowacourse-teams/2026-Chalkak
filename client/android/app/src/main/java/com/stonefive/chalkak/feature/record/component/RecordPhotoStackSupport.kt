package com.stonefive.chalkak.feature.record.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 월간 상단 사진 묶음의 모양. 화면 오른쪽 위 전환 버튼으로 바꿔 보며 A/B로 비교한다. */
enum class RecordPhotoStackStyle(
    val title: String,
    @param:DrawableRes val iconRes: Int,
) {
    /** 한 줄 기둥 + 눈금자. */
    PILLARS("기둥과 눈금자", R.drawable.ic_record_stack_pillars),

    /** 한 달이 한 권인 종이책. */
    BOOK("월간 책자", R.drawable.ic_record_stack_book),
    ;

    val toggled: RecordPhotoStackStyle
        get() = if (this == PILLARS) BOOK else PILLARS
}

/** 묶음 위 한 장. 기록이 있는 날은 사진, 없는 날은 빈 프레임이다. */
data class RecordPhotoStackEntry(
    val date: LocalDate,
    val post: PostCalendarItem?,
) {
    val key: String
        get() = post?.postId ?: "empty-$date"

    val day: Int
        get() = date.dayOfMonth
}

/**
 * 묶음 안의 순서. 화면 크기와 무관한 값만 다룬다.
 * 두 모양 모두 위치(position)는 보고 있는 날의 순서다(가장 최근 날이 0).
 *
 * @param elapsedDays 빈 프레임까지 채울 마지막 날. 기본값은 오늘(지난 달은 말일)이다.
 */
class RecordPhotoStackOrder(
    month: YearMonth,
    posts: List<PostCalendarItem>,
    elapsedDays: Int = month.elapsedDayCount(),
) {
    /** 그 달 1일부터 지난 날까지, 가장 최근 날이 먼저 온다. */
    val entries: List<RecordPhotoStackEntry>

    init {
        val postsByDay = posts
            .filter { YearMonth.from(it.topicDate) == month }
            .groupBy { it.topicDate.dayOfMonth }
            .mapValues { it.value.first() }
        val lastDay = minOf(
            month.lengthOfMonth(),
            maxOf(elapsedDays, postsByDay.keys.maxOrNull() ?: 0),
        )
        entries = (lastDay downTo 1).map { day ->
            RecordPhotoStackEntry(date = month.atDay(day), post = postsByDay[day])
        }
    }

    /** 묶음이 지금 보여 주는 장. 책은 표지·속표지를 볼 때 위치가 마지막 장을 넘어가므로 1일로 맞춘다. */
    fun focusedIndex(position: Float): Int? {
        if (entries.isEmpty()) return null
        return position.roundToInt().coerceIn(0, entries.lastIndex)
    }

    /** 그 장을 보여 주는 위치. [focusedIndex]의 반대다. */
    fun position(index: Int): Float = index.coerceIn(0, maxOf(0, entries.lastIndex)).toFloat()

    /** 묶음 안에서 그 사진이 몇 번째인지(가장 최근 날이 0). */
    fun indexOf(post: PostCalendarItem): Int? = entries
        .indexOfFirst { it.post?.postId == post.postId }
        .takeIf { it >= 0 }

    /** 그 날짜가 몇 번째인지(가장 최근 날이 0). */
    fun indexOf(date: LocalDate): Int? = entries
        .indexOfFirst { it.date == date }
        .takeIf { it >= 0 }

    companion object {
        private const val RUBBER_BAND_RATIO = 0.35f

        /** 범위를 넘어 끌면 고무줄처럼 덜 움직인다. */
        fun rubberBanded(
            raw: Float,
            range: ClosedFloatingPointRange<Float>,
        ): Float = when {
            raw < range.start -> range.start + (raw - range.start) * RUBBER_BAND_RATIO
            raw > range.endInclusive -> range.endInclusive + (raw - range.endInclusive) * RUBBER_BAND_RATIO
            else -> raw
        }
    }
}

private val RecordZone: ZoneId = ZoneId.of("Asia/Seoul")

/** 이미 지난(오늘 포함) 날짜 수. 지난 달은 말일까지, 이번 달은 오늘까지, 아직 오지 않은 달은 0이다. */
fun YearMonth.elapsedDayCount(today: LocalDate = LocalDate.now(RecordZone)): Int {
    val current = YearMonth.from(today)
    return when {
        this < current -> lengthOfMonth()
        this > current -> 0
        else -> today.dayOfMonth
    }
}

/** 일요일 시작 달력에서 날짜가 속한 주의 순서(0부터). */
fun YearMonth.weekIndexOf(date: LocalDate): Int {
    val leadingEmptyDays = atDay(1).dayOfWeek.value % 7
    return (date.dayOfMonth + leadingEmptyDays - 1) / 7
}

/** 사진 비율(너비 / 높이). 아직 내려받지 못한 사진은 4:5로 자리를 잡는다. */
object RecordPhotoAspect {
    const val PLACEHOLDER = 4f / 5f

    fun widthRatio(image: ImageBitmap?): Float = if (image != null && image.width > 0 && image.height > 0) {
        image.width.toFloat() / image.height
    } else {
        PLACEHOLDER
    }
}

/** 내려받은 썸네일 또는 자리표시 배경. */
@Composable
fun RecordPhotoImage(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (image == null) {
        Box(modifier = modifier.background(ChalkakTheme.colors.calendarCell))
    } else {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

/**
 * 기록이 없는 날의 자리. 바탕 위에 기본 글자색을 55% 겹친 불투명 차콜 면으로, 뒤 사진이 비치지 않는다.
 * 빈 날끼리 겹쳐도 갈라져 보이도록 옅은 흰 안쪽 테두리를 두른다.
 *
 * @param day null이면 날짜를 적지 않는다.
 */
@Composable
fun RecordEmptyFrame(
    day: Int?,
    modifier: Modifier = Modifier,
    labelAlignment: Alignment = Alignment.TopEnd,
) {
    val colors = ChalkakTheme.colors
    Box(
        modifier = modifier
            .background(colors.background)
            .background(colors.textPrimary.copy(alpha = EMPTY_FRAME_INK_ALPHA))
            .border(EmptyFrameEdgeWidth, colors.textOnImage.copy(alpha = EMPTY_FRAME_EDGE_ALPHA), RectangleShape)
            .clearAndSetSemantics {},
    ) {
        if (day != null) {
            Text(
                text = "$day",
                color = colors.textOnImage.copy(alpha = EMPTY_FRAME_LABEL_ALPHA),
                style = ChalkakTheme.typography.caption
                    .copy(fontFeatureSettings = TABULAR_NUMBERS),
                modifier = Modifier
                    .align(labelAlignment)
                    .padding(EmptyFrameLabelPadding),
            )
        }
    }
}

private val EmptyFrameEdgeWidth = 1.dp
private val EmptyFrameLabelPadding = 6.dp
private const val EMPTY_FRAME_INK_ALPHA = 0.55f
private const val EMPTY_FRAME_LABEL_ALPHA = 0.72f
private const val EMPTY_FRAME_EDGE_ALPHA = 0.22f

/** 숫자 폭을 고르게 맞춘다. 날짜가 바뀌어도 글자가 흔들리지 않는다. */
internal const val TABULAR_NUMBERS = "tnum"

/**
 * 묶음 왼쪽 위의 월과 연도. 누르면 연간 달력으로 나가 다른 달을 고른다.
 * 달은 크게, 연도는 작게 한 줄로 적는다. 예: "10월 2026".
 */
@Composable
fun RecordStackMonthButton(
    month: YearMonth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = RecordStackMetrics.ButtonDiameter)
            .clip(ChalkakTheme.shapes.small)
            .semantics(mergeDescendants = true) {
                contentDescription = "${month.year}년 ${month.monthValue}월"
            }.clickable(
                onClickLabel = "연간 달력에서 다른 달 고르기",
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = MonthButtonHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${month.monthValue}월",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = "${month.year}",
            color = ChalkakTheme.colors.textMuted,
            style = ChalkakTheme.typography.footnote,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** 묶음 위에 떠 있는 둥근 아이콘 버튼. 꺼지면 아이콘을 비활성 색으로 바꿔 누를 수 없음을 보인다. */
@Composable
fun RecordFloatingIconButton(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    diameter: Dp = RecordStackMetrics.ButtonDiameter,
    iconSize: Dp = FloatingButtonIconSize,
) {
    val colors = ChalkakTheme.colors
    // 누르는 영역은 바깥 사각형(최소 터치 크기)이고, 리플은 보이는 원 안에만 그린다.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(maxOf(diameter, RecordStackMetrics.MinimumTouch))
            .semantics {
                contentDescription = title
                role = Role.Button
                if (!enabled) onClick(label = null) { false }
            }.clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .alpha(if (enabled) 1f else FLOATING_BUTTON_DISABLED_ALPHA)
                .shadow(FloatingButtonElevation, CircleShape)
                .clip(CircleShape)
                .background(colors.surfaceElevated.copy(alpha = FLOATING_BUTTON_SURFACE_ALPHA))
                .indication(interactionSource, ripple()),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) colors.iconPrimary else colors.textInactive,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** 바깥 여백(12dp)과 더해 달력 왼쪽 끝(20dp)에 글자를 맞춘다. */
private val MonthButtonHorizontalPadding = 8.dp
private val FloatingButtonIconSize = 22.dp
private val FloatingButtonElevation = 2.dp
private const val FLOATING_BUTTON_DISABLED_ALPHA = 0.6f
private const val FLOATING_BUTTON_SURFACE_ALPHA = 0.92f

object RecordStackMetrics {
    /** 묶음 영역의 높이. 두 모양이 같은 높이를 쓴다. 책 아래 그림자가 잘리지 않을 만큼 여유를 둔다. */
    val Height = 300.dp

    /**
     * 묶음 내용(기둥, 책 표지)이 시작하는 높이. 위 버튼 줄(위 여백 8 + 48dp) 아래로 28dp를 띄운다.
     * 묶음이 커지거나 줄어도, 기둥이든 책자든 이 간격은 같다.
     */
    const val CONTENT_TOP = 84f

    val ButtonDiameter = 48.dp
    val MinimumTouch = 48.dp
}

object RecordStackFormat {
    val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.KOREAN)
}

internal fun LocalDate.isSunday(): Boolean = dayOfWeek == DayOfWeek.SUNDAY

/** 장식 그림자처럼 쓰는 검정. 화면 색 토큰이 아니라 빛의 세기를 나타낸다. */
internal val Ink = Color.Black
