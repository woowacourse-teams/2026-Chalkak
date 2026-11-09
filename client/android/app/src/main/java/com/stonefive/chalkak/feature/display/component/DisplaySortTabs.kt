package com.stonefive.chalkak.feature.display.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortSelector
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostSort

@Composable
fun DisplaySortTabs(
    selectedSort: PostSort,
    onSortSelected: (PostSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChalkakSortSelector(
        options = DisplaySortOrder,
        selectedOption = selectedSort,
        optionLabel = PostSort::displayLabel,
        onOptionSelected = onSortSelected,
        modifier = modifier,
    )
}

/**
 * 전시 정렬 탭에 보이는 순서(왼쪽부터). 기본 정렬인 랜덤순을 맨 앞에 둔다(#974).
 * [PostSort]의 선언 순서와 상관없이 이 목록 순서대로 그린다. iOS `DisplayScreen`의 `sortTabOrder`와 같다.
 */
private val DisplaySortOrder = listOf(PostSort.RANDOM, PostSort.POPULAR, PostSort.LATEST)

private val PostSort.displayLabel: String
    get() = when (this) {
        PostSort.LATEST -> "최신순"
        PostSort.POPULAR -> "인기순"
        PostSort.RANDOM -> "랜덤순"
    }

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DisplaySortTabsPreview() {
    ChalkakTheme {
        DisplaySortTabs(
            selectedSort = PostSort.RANDOM,
            onSortSelected = {},
        )
    }
}
