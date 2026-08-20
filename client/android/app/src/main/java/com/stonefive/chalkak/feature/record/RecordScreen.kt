package com.stonefive.chalkak.feature.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.RecordPhoto
import com.stonefive.chalkak.feature.record.component.RecordCalendarGrid
import com.stonefive.chalkak.feature.record.component.RecordCalendarHeader
import com.stonefive.chalkak.feature.record.component.RecordSelectedPhoto
import com.stonefive.chalkak.feature.record.component.RecordWeekdayHeader
import java.time.LocalDate

private val RecordHorizontalPadding = 20.dp

@Composable
fun RecordRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RecordScreen(
        uiState = uiState,
        onPreviousMonthClick = viewModel::moveToPreviousMonth,
        onNextMonthClick = viewModel::moveToNextMonth,
        onDateClick = viewModel::selectDate,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onNavigateToBottomBar = onNavigateToBottomBar,
        modifier = modifier,
    )
}

@Composable
fun RecordScreen(
    uiState: RecordUiState,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.RECORD,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            RecordCalendarHeader(
                month = uiState.month,
                onPreviousMonthClick = onPreviousMonthClick,
                onNextMonthClick = onNextMonthClick,
            )
            RecordWeekdayHeader(
                modifier = Modifier.padding(top = 8.dp),
            )
            RecordCalendarGrid(
                month = uiState.month,
                photos = uiState.photos,
                selectedDate = uiState.selectedDate,
                onDateClick = onDateClick,
                modifier = Modifier.padding(top = 14.dp),
            )
            RecordSummary(
                photoCount = uiState.photos.size,
            )
            if (uiState.errorMessage == null) {
                RecordSelectedPhoto(
                    photo = uiState.selectedPhoto,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = uiState.errorMessage,
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.body,
                    modifier = Modifier.padding(RecordHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun RecordSummary(
    photoCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = RecordHorizontalPadding,
                vertical = 20.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "이번 달에는 ${photoCount}장을 담았어요",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "이미지로 저장",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun RecordScreenPreview() {
    val photo = RecordPhoto(
        date = LocalDate.of(2026, 8, 2),
        imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
        contentDescription = "노을과 전신주",
        title = "물결",
    )

    ChalkakTheme {
        RecordScreen(
            uiState = RecordUiState(
                isLoading = false,
                photos = listOf(photo),
                selectedDate = photo.date,
            ),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onDateClick = {},
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
        )
    }
}
