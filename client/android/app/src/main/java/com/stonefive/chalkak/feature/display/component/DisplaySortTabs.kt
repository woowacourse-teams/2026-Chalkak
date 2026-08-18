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
        options = PostSort.entries,
        selectedOption = selectedSort,
        optionLabel = PostSort::displayLabel,
        onOptionSelected = onSortSelected,
        modifier = modifier,
    )
}

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
            selectedSort = PostSort.LATEST,
            onSortSelected = {},
        )
    }
}
