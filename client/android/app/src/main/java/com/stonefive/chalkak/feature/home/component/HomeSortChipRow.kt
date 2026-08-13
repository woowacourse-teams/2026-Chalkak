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
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortSelector
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PhotoSort

@Composable
fun HomeSortChipRow(
    selectedSort: PhotoSort,
    onSortSelected: (PhotoSort) -> Unit,
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
            options = PhotoSort.entries,
            selectedOption = selectedSort,
            optionLabel = { it.label },
            onOptionSelected = onSortSelected,
        )
    }
}

private val PhotoSort.label: String
    get() = when (this) {
        PhotoSort.LATEST -> "최신순"
        PhotoSort.POPULAR -> "인기순"
        PhotoSort.RANDOM -> "랜덤순"
    }
