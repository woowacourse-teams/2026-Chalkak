package com.stonefive.chalkak.core.designsystem.component.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val WheelItemHeight = 44.dp
private val WheelDividerThickness = 1.dp
private const val VISIBLE_ITEM_COUNT = 5
private const val EDGE_ITEM_COUNT = VISIBLE_ITEM_COUNT / 2
private const val FAR_ITEM_ALPHA = 0.22f

/**
 * 위아래로 굴려 값 하나를 고르는 휠.
 *
 * - 가운데 줄(위아래 구분선 사이)에 멈춘 값이 선택값이다. 스크롤이 멈출 때만 [onSelectedItemChange]를 부른다.
 * - 값을 누르면 그 값이 가운데로 굴러온다. 칸을 넘길 때마다 짧게 진동한다.
 * - [selectedItem]이 밖에서 바뀌면(예: 월을 바꿔 일 목록이 줄어듦) 그 값으로 바로 이동한다.
 * - 고를 수 없는 값은 [items]에 넣지 않는다. 목록 끝 너머는 빈 칸으로 보인다.
 * - TalkBack에서는 [contentDescription]과 현재 값을 읽고, 위아래 스와이프로 한 칸씩 바꾼다.
 *
 * 예: `ChalkakWheelPicker(items = listOf(9, 10), selectedItem = 10, onSelectedItemChange = {}, itemLabel = { "${it}월" }, contentDescription = "월")`
 */
@Composable
fun <T> ChalkakWheelPicker(
    items: List<T>,
    selectedItem: T,
    onSelectedItemChange: (T) -> Unit,
    itemLabel: (T) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val centeredIndex by remember(listState) {
        derivedStateOf { listState.centeredItemIndex() }
    }
    val currentItems by rememberUpdatedState(items)
    val currentSelectedItem by rememberUpdatedState(selectedItem)
    val currentOnSelectedItemChange by rememberUpdatedState(onSelectedItemChange)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { isScrolling -> !isScrolling }
            .collect {
                val settledItem = currentItems.getOrNull(listState.centeredItemIndex()) ?: return@collect
                if (settledItem != currentSelectedItem) currentOnSelectedItemChange(settledItem)
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { centeredIndex }
            .drop(1)
            .collect {
                if (listState.isScrollInProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
    }
    LaunchedEffect(items, selectedIndex) {
        if (!listState.isScrollInProgress && listState.centeredItemIndex() != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .height(WheelItemHeight * VISIBLE_ITEM_COUNT)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                stateDescription = items
                    .getOrNull(selectedIndex)
                    ?.let(itemLabel)
                    .orEmpty()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = selectedIndex.toFloat(),
                    range = 0f..items.lastIndex
                        .coerceAtLeast(0)
                        .toFloat(),
                    steps = (items.size - 2).coerceAtLeast(0),
                )
                setProgress { target ->
                    val targetItem = items.getOrNull(target.roundToInt()) ?: return@setProgress false
                    if (targetItem != selectedItem) onSelectedItemChange(targetItem)
                    true
                }
            },
    ) {
        WheelSelectionDivider(modifier = Modifier.offset(y = WheelItemHeight * EDGE_ITEM_COUNT))
        WheelSelectionDivider(
            modifier = Modifier.offset(y = WheelItemHeight * (EDGE_ITEM_COUNT + 1) - WheelDividerThickness),
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = listState,
                snapPosition = SnapPosition.Center,
            ),
            contentPadding = PaddingValues(vertical = WheelItemHeight * EDGE_ITEM_COUNT),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, item ->
                val distance = abs(index - centeredIndex)
                Text(
                    text = itemLabel(item),
                    style = wheelItemStyle(distance),
                    color = wheelItemColor(distance),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable(interactionSource = null, indication = null) {
                            scope.launch { listState.animateScrollToItem(index) }
                        }.wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun WheelSelectionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WheelDividerThickness)
            .background(ChalkakTheme.colors.border),
    )
}

@Composable
private fun wheelItemStyle(distance: Int): TextStyle = when (distance) {
    0 -> ChalkakTheme.typography.title3
    1 -> ChalkakTheme.typography.body
    else -> ChalkakTheme.typography.callout
}

@Composable
private fun wheelItemColor(distance: Int) = when (distance) {
    0 -> ChalkakTheme.colors.textPrimary

    1 -> ChalkakTheme.colors.textMuted

    else ->
        ChalkakTheme.colors.textPrimary
            .copy(alpha = FAR_ITEM_ALPHA)
}

/** 뷰포트 세로 가운데에 가장 가까운 항목의 index. 아직 배치 전이면 첫 항목 index를 쓴다. */
private fun LazyListState.centeredItemIndex(): Int {
    val info = layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
        ?.index
        ?: firstVisibleItemIndex
}

@Preview(showBackground = true, widthDp = 342)
@Composable
private fun ChalkakWheelPickerPreview() {
    ChalkakTheme {
        var month by remember { mutableIntStateOf(10) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ChalkakWheelPicker(
                items = listOf(2026),
                selectedItem = 2026,
                onSelectedItemChange = {},
                itemLabel = { "${it}년" },
                contentDescription = "연도",
                modifier = Modifier.weight(1f),
            )
            ChalkakWheelPicker(
                items = (1..10).toList(),
                selectedItem = month,
                onSelectedItemChange = { month = it },
                itemLabel = { "${it}월" },
                contentDescription = "월",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
