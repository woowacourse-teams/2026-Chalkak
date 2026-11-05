package com.stonefive.chalkak.feature.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialog
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialogStyle
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.feedback.component.FeedbackTopBar
import com.stonefive.chalkak.feature.feedback.component.FeedbackTopBarPadding

/**
 * 문의 상세 Route. 상태가 바뀌면 결과 콜백을 부른다.
 *
 * @param onNotFound 상세·삭제가 404일 때 안내 문구("문의를 찾을 수 없어요")를 넘긴다. 호출부가 토스트 후 목록을 새로고침한다.
 * @param onDeleted 삭제 204를 받았을 때. 호출부가 목록으로 돌아가 새로고침한다.
 * @param onReauthenticationRequired 상세·삭제가 401일 때.
 */
@Composable
fun FeedbackDetailRoute(
    feedbackId: String,
    onBackClick: () -> Unit,
    onNotFound: (String) -> Unit,
    onDeleted: () -> Unit,
    onReauthenticationRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackDetailViewModel = viewModel(
        key = "feedback-detail-$feedbackId",
        factory = FeedbackDetailViewModel.factory(feedbackId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    // 이 상태들은 모두 이 화면을 떠나는 결과라, NavHost가 백스택에서 이 화면을 지워 반복 이동이 없다.
    LaunchedEffect(uiState.status) {
        when (val status = uiState.status) {
            is FeedbackDetailStatus.NotFound -> onNotFound(status.message)
            FeedbackDetailStatus.Deleted -> onDeleted()
            FeedbackDetailStatus.ReauthenticationRequired -> onReauthenticationRequired()
            else -> Unit
        }
    }

    FeedbackDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onDeleteConfirm = viewModel::delete,
        modifier = modifier,
    )
}

/** 문의 상세 화면. 문의 내용·접수 시각, 그 아래 답변(또는 "아직 답변이 없어요"), 맨 아래 삭제 버튼. */
@Composable
fun FeedbackDetailScreen(
    uiState: FeedbackDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        FeedbackTopBar(
            title = "내 문의",
            onBackClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeedbackTopBarPadding),
        )

        val detail = uiState.detail
        when {
            uiState.status == FeedbackDetailStatus.Content && detail != null -> {
                FeedbackDetailContent(
                    detail = detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                ChalkakOutlinedButton(
                    text = if (uiState.isDeleting) "삭제하는 중..." else "문의 삭제",
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                        .padding(top = ChalkakTheme.spacing.sm, bottom = ChalkakTheme.spacing.lg),
                    enabled = !uiState.isDeleting,
                )
            }

            uiState.status == FeedbackDetailStatus.Failed -> FeedbackDetailFailure(
                onRetryClick = onRetryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.status == FeedbackDetailStatus.Loading) {
                    CircularProgressIndicator(color = ChalkakTheme.colors.iconPrimary)
                }
            }
        }
    }

    if (showDeleteDialog) {
        ChalkakConfirmDialog(
            title = "문의를 삭제할까요?",
            message = "삭제한 문의와 답변은 다시 볼 수 없어요.",
            confirmText = "삭제",
            onConfirm = {
                showDeleteDialog = false
                onDeleteConfirm()
            },
            onDismiss = { showDeleteDialog = false },
            confirmStyle = ChalkakConfirmDialogStyle.DESTRUCTIVE,
        )
    }
}

@Composable
private fun FeedbackDetailContent(
    detail: FeedbackDetailContentUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal, vertical = ChalkakTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
    ) {
        FeedbackDetailSection(label = "문의 내용", text = detail.content, timeText = detail.createdAtText)

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = DividerDefaults.Thickness,
            color = ChalkakTheme.colors.border,
        )

        if (detail.answerContent != null) {
            FeedbackDetailSection(label = "답변", text = detail.answerContent, timeText = detail.answeredAtText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm)) {
                FeedbackDetailSectionLabel(text = "답변")
                Text(
                    text = "아직 답변이 없어요",
                    style = ChalkakTheme.typography.subheadline,
                    color = ChalkakTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun FeedbackDetailSection(
    label: String,
    text: String,
    timeText: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm)) {
        FeedbackDetailSectionLabel(text = label)
        SelectionContainer {
            Text(
                text = text,
                style = ChalkakTheme.typography.body,
                color = ChalkakTheme.colors.textPrimary,
            )
        }
        timeText?.let {
            Text(
                text = it,
                style = ChalkakTheme.typography.caption,
                color = ChalkakTheme.colors.textInactive,
            )
        }
    }
}

@Composable
private fun FeedbackDetailSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = ChalkakTheme.typography.caption,
        color = ChalkakTheme.colors.textSecondary,
    )
}

@Composable
private fun FeedbackDetailFailure(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "문의를 불러오지 못했어요",
            style = ChalkakTheme.typography.subheadline,
            color = ChalkakTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        ChalkakOutlinedButton(text = "다시 시도", onClick = onRetryClick)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackDetailScreenAnsweredPreview() {
    ChalkakTheme {
        FeedbackDetailScreen(
            uiState = FeedbackDetailUiState(
                status = FeedbackDetailStatus.Content,
                detail = FeedbackDetailContentUiState(
                    content = "사진 업로드 후 화면이 멈춰요.",
                    createdAtText = "11월 3일 15:00",
                    answerContent = "확인했어요. 다음 업데이트에서 고칠게요.",
                    answeredAtText = "11월 4일 10:00",
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onDeleteConfirm = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackDetailScreenUnansweredPreview() {
    ChalkakTheme {
        FeedbackDetailScreen(
            uiState = FeedbackDetailUiState(
                status = FeedbackDetailStatus.Content,
                detail = FeedbackDetailContentUiState(content = "알림이 늦게 와요.", createdAtText = "11월 3일 15:00"),
            ),
            onBackClick = {},
            onRetryClick = {},
            onDeleteConfirm = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackDetailScreenFailedPreview() {
    ChalkakTheme {
        FeedbackDetailScreen(
            uiState = FeedbackDetailUiState(status = FeedbackDetailStatus.Failed),
            onBackClick = {},
            onRetryClick = {},
            onDeleteConfirm = {},
        )
    }
}
