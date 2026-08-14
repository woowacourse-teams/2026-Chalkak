package com.stonefive.chalkak.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortSelector
import com.stonefive.chalkak.domain.model.PostSort

@Composable
fun HomeSortSelector(
    selectedSort: PostSort,
    onSortSelected: (PostSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChalkakSortSelector(
        options = PostSort.entries,
        selectedOption = selectedSort,
        optionLabel = { it.label },
        onOptionSelected = onSortSelected,
        modifier = modifier,
    )
}

private val PostSort.label: String
    get() = when (this) {
        PostSort.LATEST -> "최신순"
        PostSort.POPULAR -> "인기순"
        PostSort.RANDOM -> "랜덤순"
    }
