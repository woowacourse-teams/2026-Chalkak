package com.stonefive.chalkak.feature.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakScreenBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.rememberScreenBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.filter.component.FilterCard
import com.stonefive.chalkak.feature.filter.component.FilterDetailSheet

@Composable
fun FilterRoute(
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onOpenPhotoUpload: () -> Unit,
    onUseFilterClick: (filterId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilterViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FilterScreen(
        uiState = uiState,
        onFilterClick = viewModel::selectFilter,
        onFavoriteToggle = viewModel::toggleFavorite,
        onUseFilterClick = { filterId ->
            viewModel.dismissFilter()
            onUseFilterClick(filterId)
        },
        onDismissSheet = viewModel::dismissFilter,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onAddClick = onOpenPhotoUpload,
        modifier = modifier,
    )
}

@Composable
fun FilterScreen(
    uiState: FilterUiState,
    onFilterClick: (filterId: String) -> Unit,
    onFavoriteToggle: (filterId: String) -> Unit,
    onUseFilterClick: (filterId: String) -> Unit,
    onDismissSheet: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomBarState = rememberScreenBottomBarScrollState(ChalkakBottomBarItem.FILTER)
    val isSheetVisible = uiState.selectedFilter != null
    // 하단바는 화면 맨 위 레이어라 시트와 사용 버튼을 가리므로, 시트가 열려 있는 동안만 내려 둔다.
    DisposableEffect(isSheetVisible, bottomBarState) {
        if (isSheetVisible) bottomBarState.offset = bottomBarState.height.toFloat()
        onDispose { bottomBarState.offset = 0f }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ChalkakTheme.colors.background,
            bottomBar = {
                ChalkakScreenBottomBar(
                    selectedItem = ChalkakBottomBarItem.FILTER,
                    onItemSelected = onNavigateToBottomBar,
                    onAddClick = onAddClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) { innerPadding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMN_COUNT),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = ChalkakTheme.spacing.screenHorizontal,
                    vertical = ChalkakTheme.spacing.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
            ) {
                items(uiState.filters, key = { it.id }) { filter ->
                    FilterCard(
                        name = filter.name,
                        description = filter.description,
                        coverImageRes = filter.coverImageRes,
                        onClick = { onFilterClick(filter.id) },
                    )
                }
            }
        }

        uiState.selectedFilter?.let { filter ->
            FilterDetailSheet(
                filter = filter,
                isFavorite = uiState.isFavorite(filter.id),
                onFavoriteToggle = { onFavoriteToggle(filter.id) },
                onUseClick = { onUseFilterClick(filter.id) },
                onDismiss = onDismissSheet,
            )
        }
    }
}

private const val GRID_COLUMN_COUNT = 2

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FilterScreenPreview() {
    ChalkakTheme {
        FilterScreen(
            uiState = FilterUiState(filters = SampleFilters),
            onFilterClick = {},
            onFavoriteToggle = {},
            onUseFilterClick = {},
            onDismissSheet = {},
            onNavigateToBottomBar = {},
            onAddClick = {},
        )
    }
}
