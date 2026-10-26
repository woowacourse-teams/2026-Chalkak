package com.stonefive.chalkak.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialog
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialogStyle
import com.stonefive.chalkak.core.designsystem.component.image.photoTransitionChrome
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.aireview.AiReviewPhase
import com.stonefive.chalkak.feature.aireview.component.AiReviewAudioWaveIcon
import com.stonefive.chalkak.feature.aireview.component.AiReviewFailureSheet
import com.stonefive.chalkak.feature.aireview.component.AiReviewResultSheet
import com.stonefive.chalkak.feature.aireview.component.AiReviewSheetDragHandle
import com.stonefive.chalkak.feature.aireview.component.AiReviewStartSheet
import com.stonefive.chalkak.feature.feed.component.FeedContent
import com.stonefive.chalkak.feature.feed.component.FeedSignatureHintSheet
import com.stonefive.chalkak.feature.feed.component.FeedTitleEditDialog
import com.stonefive.chalkak.feature.feed.component.FeedTopBar
import java.time.LocalDate

@Composable
fun FeedRoute(
    onNavigateBack: () -> Unit,
    onPostDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    postId: String? = null,
    entryThumbnailImageUrl: String? = null,
    openAiReviewResult: Boolean = false,
    initialContent: FeedContentState.Success? = null,
    isOwnedByCurrentUser: Boolean = initialContent?.post?.isOwnedByCurrentUser == true,
    viewModel: FeedViewModel = viewModel(
        key = "feed-${postId ?: initialContent?.post?.id ?: "latest"}",
        factory = FeedViewModel.factory(
            postId = postId,
            initialContent = initialContent,
            isOwnedByCurrentUser = isOwnedByCurrentUser,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        var isStarted = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!isStarted) {
                    isStarted = true
                    viewModel.onResume()
                }

                Lifecycle.Event.ON_STOP -> {
                    isStarted = false
                    viewModel.onPause()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED) && !isStarted
        ) {
            isStarted = true
            viewModel.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown, snackbarHostState)

    LaunchedEffect(uiState.deleteSuccessPostId) {
        uiState.deleteSuccessPostId?.let(onPostDeleted)
    }

    FeedScreen(
        uiState = uiState,
        entryPostId = postId,
        entryThumbnailImageUrl = entryThumbnailImageUrl,
        openAiReviewResult = openAiReviewResult,
        onNavigateBack = onNavigateBack,
        onEditTitleClick = viewModel::updatePostTitle,
        onDeleteClick = viewModel::deletePost,
        onLikeClick = viewModel::onLikeClicked,
        onSignatureClick = {
            viewModel.onSignatureClicked()?.let { post ->
                onAuthorClick(
                    post.authorId,
                    post.signatureThumbnailImageUrl ?: post.signatureOriginalImageUrl,
                    post.isOwnedByCurrentUser,
                )
            }
        },
        onSignatureVisible = viewModel::onSignatureVisible,
        onDismissSignatureHint = viewModel::dismissSignatureHint,
        snackbarHostState = snackbarHostState,
        onRetryClick = viewModel::retry,
        onStartFeedback = viewModel::startFeedback,
        onDismissFeedbackMessage = viewModel::dismissFeedbackMessage,
        modifier = modifier,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    onLikeClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onSignatureClick: () -> Unit = {},
    onSignatureVisible: () -> Unit = {},
    onDismissSignatureHint: () -> Unit = {},
    onEditTitleClick: (String?) -> Unit = {},
    onRetryClick: () -> Unit = {},
    entryPostId: String? = null,
    entryThumbnailImageUrl: String? = null,
    openAiReviewResult: Boolean = false,
    onStartFeedback: () -> Unit = {},
    onDismissFeedbackMessage: () -> Unit = {},
) {
    var photoBounds by remember(
        uiState.content
            ?.post
            ?.id,
        entryPostId,
    ) { mutableStateOf<Rect?>(null) }
    var pillBounds by remember { mutableStateOf<Rect?>(null) }
    val isPillOverPhoto = photoBounds?.let { photo -> pillBounds?.overlaps(photo) } == true
    val hasEntryPhoto = entryPostId != null && entryThumbnailImageUrl != null
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTitleEditDialog by rememberSaveable { mutableStateOf(false) }
    var titleDraft by rememberSaveable { mutableStateOf("") }
    val aiReviewKey = uiState.content
        ?.post
        ?.id to uiState.aiReview.phase
    var showAiResultSheet by rememberSaveable(aiReviewKey) { mutableStateOf(false) }
    var showAiStartSheet by rememberSaveable(aiReviewKey) { mutableStateOf(false) }
    var showAiFailureSheet by rememberSaveable(aiReviewKey) { mutableStateOf(false) }
    var didAutoOpenAiReviewResult by rememberSaveable { mutableStateOf(false) }
    val canRequestAiReviewAgain = uiState.aiReview.phase == AiReviewPhase.Failed && uiState.aiReview.canRequestAgain

    LaunchedEffect(openAiReviewResult, uiState.aiReview.phase, uiState.aiReview.status) {
        if (!openAiReviewResult || didAutoOpenAiReviewResult) return@LaunchedEffect
        if (uiState.aiReview.status == AiReview.Status.FAILED) {
            didAutoOpenAiReviewResult = true
            if (canRequestAiReviewAgain) showAiStartSheet = true else showAiFailureSheet = true
        } else if (uiState.aiReview.phase == AiReviewPhase.Completed && uiState.aiReview.result != null) {
            didAutoOpenAiReviewResult = true
            showAiResultSheet = true
        }
    }

    LaunchedEffect(uiState.titleUpdateSuccessPostId) {
        if (uiState.titleUpdateSuccessPostId != null) {
            showTitleEditDialog = false
        }
    }

    if (showDeleteDialog) {
        ChalkakConfirmDialog(
            title = "게시물 삭제",
            message = "게시물을 삭제하시겠어요?",
            confirmText = "삭제",
            onConfirm = {
                showDeleteDialog = false
                onDeleteClick()
            },
            onDismiss = { showDeleteDialog = false },
            modifier = Modifier.fillMaxWidth(0.81f),
            confirmStyle = ChalkakConfirmDialogStyle.DESTRUCTIVE,
        )
    }

    if (showTitleEditDialog) {
        FeedTitleEditDialog(
            title = titleDraft,
            onTitleChange = { titleDraft = it },
            onConfirm = { onEditTitleClick(titleDraft) },
            onDismiss = { if (!uiState.isUpdatingTitle) showTitleEditDialog = false },
            isSubmitting = uiState.isUpdatingTitle,
            modifier = Modifier.fillMaxWidth(0.88f),
        )
    }
    uiState.aiReview.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissFeedbackMessage,
            confirmButton = {
                androidx.compose.material3
                    .TextButton(onClick = onDismissFeedbackMessage) { Text("확인") }
            },
            text = { Text(message) },
        )
    }
    if (showAiFailureSheet) {
        AiReviewFailureSheet(
            // 다시 요청 횟수가 남았는데도 이 시트가 뜨는 경우는 오늘 사진이 아닐 때뿐이다.
            message = if (uiState.aiReview.canRequestAgain) {
                "AI 피드백은 오늘 올린 사진만 재요청할 수 있어요."
            } else {
                "이 사진은 더 이상 AI 피드백을 요청할 수 없어요."
            },
            onDismiss = { showAiFailureSheet = false },
        )
    }
    if (showAiResultSheet) {
        uiState.aiReview.result?.let { result ->
            AiReviewResultSheet(result = result, onDismiss = { showAiResultSheet = false })
        }
    }
    if (uiState.showSignatureHint) {
        FeedSignatureHintSheet(onDismiss = onDismissSignatureHint)
    }
    if (showAiStartSheet) {
        val sheetState = rememberModalBottomSheetState()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAiStartSheet = false },
            sheetState = sheetState,
            dragHandle = null,
        ) {
            AiReviewSheetDragHandle(
                sheetState = sheetState,
                onDismiss = { showAiStartSheet = false },
                modifier = Modifier.fillMaxWidth(),
            )
            AiReviewStartSheet(
                onStart = {
                    showAiStartSheet = false
                    onStartFeedback()
                },
                modifier = Modifier.padding(ChalkakTheme.spacing.xl),
                title = if (canRequestAiReviewAgain) "AI 피드백을 재요청할까요?" else "AI 피드백을 시작할까요?",
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .photoTransitionChrome(animateVisibility = false)
                    .background(ChalkakBackground),
            )
        },
        topBar = {
            FeedTopBar(
                onNavigateBack = onNavigateBack,
                onEditClick = {
                    titleDraft = uiState.content
                        ?.post
                        ?.title
                        .orEmpty()
                    showTitleEditDialog = true
                },
                onDeleteClick = { showDeleteDialog = true },
                isEditVisible = uiState.content
                    ?.post
                    ?.isOwnedByCurrentUser == true && uiState.isTitleEditable,
                isDeleteVisible = uiState.content
                    ?.post
                    ?.isOwnedByCurrentUser == true,
                isDeleteEnabled = !uiState.isDeleting,
                isEditEnabled =
                    !uiState.isDeleting &&
                        !uiState.isUpdatingTitle &&
                        !uiState.isRefreshing &&
                        uiState.isTitleEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .photoTransitionChrome()
                    .background(ChalkakBackground)
                    .statusBarsPadding()
                    .padding(
                        start = 8.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 8.dp,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.content != null || (hasEntryPhoto && uiState.isLoading) -> FeedContent(
                    content = uiState.content,
                    onLikeClick = onLikeClick,
                    modifier = Modifier.fillMaxSize(),
                    entryPostId = entryPostId,
                    entryThumbnailImageUrl = entryThumbnailImageUrl,
                    onPhotoBoundsChanged = { photoBounds = it },
                    onSignatureClick = onSignatureClick,
                    onSignatureVisible = onSignatureVisible,
                )

                uiState.isLoading -> CircularProgressIndicator(
                    color = ChalkakTheme.colors.actionPrimary,
                )

                uiState.errorMessage != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.body,
                    )
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
                    androidx.compose.material3.TextButton(onClick = onRetryClick) {
                        Text(
                            text = "다시 시도",
                            color = ChalkakTheme.colors.actionPrimary,
                            style = ChalkakTheme.typography.body,
                        )
                    }
                }
            }
            if (uiState.aiReview.phase != AiReviewPhase.Hidden && uiState.content
                    ?.post
                    ?.isOwnedByCurrentUser == true
            ) {
                AiReviewPill(
                    phase = uiState.aiReview.phase,
                    canRequestAgain = uiState.aiReview.canRequestAgain,
                    isOverPhoto = isPillOverPhoto,
                    onClick = {
                        when (uiState.aiReview.phase) {
                            AiReviewPhase.Ready -> showAiStartSheet = true

                            AiReviewPhase.Completed -> showAiResultSheet = true

                            AiReviewPhase.Failed -> {
                                if (canRequestAiReviewAgain) showAiStartSheet = true else showAiFailureSheet = true
                            }

                            AiReviewPhase.Hidden, AiReviewPhase.Processing -> Unit
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ChalkakTheme.spacing.lg)
                        .onGloballyPositioned { pillBounds = it.boundsInWindow() },
                )
            }
        }
    }
}

@Composable
private fun AiReviewPill(
    phase: AiReviewPhase,
    canRequestAgain: Boolean,
    isOverPhoto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = phase != AiReviewPhase.Processing
    val label = when (phase) {
        AiReviewPhase.Ready -> "AI 피드백 받기"
        AiReviewPhase.Processing -> "AI 분석중"
        AiReviewPhase.Completed -> "AI 피드백 보기"
        AiReviewPhase.Failed -> if (canRequestAgain) "AI 피드백 재요청" else "AI 피드백 실패"
        AiReviewPhase.Hidden -> return
    }
    val width = when {
        phase == AiReviewPhase.Processing -> AiReviewProcessingWidth
        phase == AiReviewPhase.Failed && canRequestAgain -> AiReviewRequestAgainWidth
        else -> AiReviewButtonWidth
    }
    val shape = RoundedCornerShape(ChalkakTheme.spacing.xl)
    Row(
        modifier = modifier
            .width(width)
            .height(AiReviewButtonHeight)
            .background(
                ChalkakTheme.colors.surfaceElevated.copy(
                    alpha = if (isOverPhoto) AI_REVIEW_PHOTO_BACKGROUND_ALPHA else 1f,
                ),
                shape,
            ).border(
                AI_REVIEW_PILL_BORDER_WIDTH,
                if (isOverPhoto) Color.Transparent else ChalkakTheme.colors.border,
                shape,
            ).clip(shape)
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = ChalkakTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            ChalkakTheme.spacing.sm,
            Alignment.CenterHorizontally,
        ),
    ) {
        AiReviewAudioWaveIcon(isAnimating = phase == AiReviewPhase.Processing)
        Text(
            text = label,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private val AiReviewButtonWidth = 148.dp
private val AiReviewProcessingWidth = 120.dp
private val AiReviewRequestAgainWidth = 160.dp
private val AiReviewButtonHeight = 44.dp
private const val AI_REVIEW_PHOTO_BACKGROUND_ALPHA = 0.7f
private val AI_REVIEW_PILL_BORDER_WIDTH = 1.dp

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedScreenPreview() {
    val resourcePrefix = "android.resource://com.stonefive.chalkak"

    ChalkakTheme {
        FeedScreen(
            uiState = FeedUiState(
                content = FeedContentState.Success(
                    dateLabel = "8월 3일의 주제",
                    topic = "하늘하늘하늘",
                    post = Post(
                        id = "preview",
                        originalImageUrl = "$resourcePrefix/${R.drawable.home_feed_photo}",
                        thumbnailImageUrl = "$resourcePrefix/${R.drawable.home_feed_photo}",
                        signatureOriginalImageUrl = "$resourcePrefix/${R.drawable.preview_signature}",
                        signatureThumbnailImageUrl = "$resourcePrefix/${R.drawable.preview_signature}",
                        contentDescription = "노을이 진 하늘과 전신주",
                        title = "안녕하세요 감사합니다.",
                        likeCount = 24,
                        isOwnedByCurrentUser = true,
                    ),
                    isLiked = false,
                    topicDate = LocalDate
                        .of(2026, 8, 3),
                ),
            ),
            onNavigateBack = {},
            onDeleteClick = {},
            onLikeClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
