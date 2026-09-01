package com.stonefive.chalkak.feature.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.record.component.RecordCalendarGrid
import com.stonefive.chalkak.feature.record.component.RecordPhotoActions
import com.stonefive.chalkak.feature.record.component.RecordSelectedPhoto
import com.stonefive.chalkak.feature.record.component.RecordTopBar
import com.stonefive.chalkak.feature.record.component.RecordWeekdayHeader
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RecordHorizontalPadding = 20.dp

@Composable
fun RecordRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
    onOpenFeed: (String) -> Unit = {},
    deletedPostId: String? = null,
    onDeletedPostConsumed: () -> Unit = {},
    onOpenDisplay: (LocalDate) -> Unit = {
        onNavigateToBottomBar(ChalkakBottomBarItem.DISPLAY)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(deletedPostId) {
        deletedPostId?.let {
            viewModel.removeDeletedPost(it)
            onDeletedPostConsumed()
        }
    }

    RecordScreen(
        uiState = uiState,
        onPreviousMonthClick = viewModel::moveToPreviousMonth,
        onNextMonthClick = viewModel::moveToNextMonth,
        onDateClick = viewModel::selectDate,
        onRetryClick = viewModel::retryCurrentMonth,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onOpenFeed = onOpenFeed,
        onOpenDisplay = onOpenDisplay,
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
    onRetryClick: () -> Unit = {},
    onOpenFeed: (String) -> Unit = {},
    onOpenDisplay: (LocalDate) -> Unit = {
        onNavigateToBottomBar(ChalkakBottomBarItem.DISPLAY)
    },
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val calendarLayer = rememberGraphicsLayer()
    val saveCalendarImageNow: () -> Unit = {
        coroutineScope.launch {
            val image = calendarLayer.toImageBitmap()
            val saved = withContext(Dispatchers.IO) {
                saveCalendarImageToGallery(
                    context = context,
                    image = image,
                    month = uiState.month,
                )
            }
            val message = if (saved) "달력을 이미지로 저장했어요" else "이미지 저장에 실패했어요"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            saveCalendarImageNow()
        } else {
            Toast.makeText(context, "이미지 저장 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }
    }
    val saveCalendarImage: () -> Unit = {
        val hasStoragePermission = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED

        if (hasStoragePermission) {
            saveCalendarImageNow()
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

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
                .verticalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChalkakBackground)
                        .drawWithContent {
                            calendarLayer.record {
                                drawRect(color = ChalkakBackground)
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(calendarLayer)
                        },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RecordTopBar(
                            month = uiState.month,
                            canGoPrevious = uiState.canGoPrevious,
                            canGoNext = uiState.canGoNext,
                            onPreviousMonthClick = onPreviousMonthClick,
                            onNextMonthClick = onNextMonthClick,
                            showSaveLink = false,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RecordWeekdayHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RecordHorizontalPadding),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        RecordCalendarGrid(
                            month = uiState.month,
                            posts = uiState.posts,
                            onDateClick = onDateClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RecordHorizontalPadding),
                        )
                        if (uiState.selectedPost != null) {
                            Spacer(modifier = Modifier.height(36.dp))
                        }
                    }
                }
                Text(
                    text = "이미지로 저장",
                    color = ChalkakTheme.colors.textMuted,
                    style = ChalkakTheme.typography.footnote,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = 20.dp,
                            end = 20.dp,
                        ).clickable(onClick = saveCalendarImage)
                        .height(48.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            if (uiState.errorMessage == null) {
                val selectedPost = uiState.selectedPost
                RecordSelectedPhoto(
                    post = selectedPost,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (
                    selectedPost != null &&
                    selectedPost.status in setOf(PostStatus.PENDING, PostStatus.APPROVED)
                ) {
                    RecordPhotoActions(
                        onFeedClick = {
                            onOpenFeed(selectedPost.postId)
                        },
                        onDisplayClick = {
                            onOpenDisplay(selectedPost.topicDate)
                        },
                        isDisplayVisible = selectedPost.status == PostStatus.APPROVED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = RecordHorizontalPadding,
                                top = 24.dp,
                                end = RecordHorizontalPadding,
                                bottom = 32.dp,
                            ),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.body,
                        modifier = Modifier.padding(horizontal = RecordHorizontalPadding),
                    )
                    TextButton(onClick = onRetryClick) {
                        Text(
                            text = "다시 시도",
                            color = ChalkakTheme.colors.actionPrimary,
                            style = ChalkakTheme.typography.body,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 1000)
@Composable
private fun RecordScreenPreview() {
    val post = PostCalendarItem(
        postId = "preview-post",
        topicDate = LocalDate.of(2026, 8, 2),
        thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        status = PostStatus.APPROVED,
    )

    ChalkakTheme {
        RecordScreen(
            uiState = RecordUiState(
                isLoading = false,
                posts = listOf(post),
                selectedDate = post.topicDate,
            ),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onDateClick = {},
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
        )
    }
}
