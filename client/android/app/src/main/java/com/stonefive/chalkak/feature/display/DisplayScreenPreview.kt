package com.stonefive.chalkak.feature.display

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.display.component.previewDisplayPhotos
import java.time.LocalDate

private val previewLatestState = DisplayUiState(
    selectedDate = LocalDate.of(2026, 8, 5),
    latestDate = LocalDate.of(2026, 8, 5),
    earliestDate = LocalDate.of(2026, 8, 1),
    topic = "바다",
    content = DisplayContentState.Latest(
        photos = previewDisplayPhotos,
        selectedSort = PostSort.LATEST,
    ),
)

private val previewArchiveState = DisplayUiState(
    selectedDate = LocalDate.of(2026, 8, 4),
    latestDate = LocalDate.of(2026, 8, 5),
    earliestDate = LocalDate.of(2026, 8, 1),
    topic = "다리",
    content = DisplayContentState.Archive(
        photos = previewDisplayPhotos,
        featuredPhotos = previewDisplayPhotos,
    ),
)

@Preview(name = "최신 전시", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LatestDisplayScreenPreview() {
    DisplayScreenPreviewContent(uiState = previewLatestState)
}

@Preview(name = "과거 전시", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ArchiveDisplayScreenPreview() {
    DisplayScreenPreviewContent(uiState = previewArchiveState)
}

@Preview(name = "본문 로딩", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun DisplayLoadingContentPreview() {
    ChalkakTheme {
        DisplayBody(
            content = DisplayContentState.Loading,
            onFeaturedPageChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "최신 본문", showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun LatestDisplayContentPreview() {
    ChalkakTheme {
        LatestDisplayContent(
            content = previewLatestState.content as DisplayContentState.Latest,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "과거 본문", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun ArchiveDisplayContentPreview() {
    ChalkakTheme {
        ArchiveDisplayContent(
            content = previewArchiveState.content as DisplayContentState.Archive,
            onFeaturedPageChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "오류 본문", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun DisplayErrorContentPreview() {
    ChalkakTheme {
        DisplayErrorContent(
            message = "전시를 불러오지 못했어요",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayScreenPreviewContent(uiState: DisplayUiState) {
    ChalkakTheme {
        DisplayScreen(
            uiState = uiState,
            onPreviousDateClick = {},
            onNextDateClick = {},
            onSortSelected = {},
            onFeaturedPageChanged = {},
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
