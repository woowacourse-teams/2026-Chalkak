package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortOption
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortSelector
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.HomeSort

@Composable
fun HomeSortChipRow(
    selectedSort: HomeSort,
    onSortSelected: (HomeSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(ChalkakBackground)
            .padding(start = ChalkakTheme.spacing.screenHorizontal),
        contentAlignment = Alignment.CenterStart,
    ) {
        ChalkakSortSelector(
            selectedOption = selectedSort.toDesignSystemOption(),
            onOptionSelected = { onSortSelected(it.toHomeSort()) },
        )
    }
}

private fun HomeSort.toDesignSystemOption(): ChalkakSortOption = when (this) {
    HomeSort.LATEST -> ChalkakSortOption.LATEST
    HomeSort.POPULAR -> ChalkakSortOption.POPULAR
    HomeSort.RANDOM -> ChalkakSortOption.RANDOM
}

private fun ChalkakSortOption.toHomeSort(): HomeSort = when (this) {
    ChalkakSortOption.LATEST -> HomeSort.LATEST
    ChalkakSortOption.POPULAR -> HomeSort.POPULAR
    ChalkakSortOption.RANDOM -> HomeSort.RANDOM
}
