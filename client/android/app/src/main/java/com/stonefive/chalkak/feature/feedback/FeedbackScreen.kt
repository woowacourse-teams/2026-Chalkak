package com.stonefive.chalkak.feature.feedback

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.feedback.component.FeedbackTopBar

@Composable
fun FeedbackRoute(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    onReauthenticationRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackViewModel = viewModel(factory = FeedbackViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                FeedbackUiEvent.Submitted -> onSubmitted()
                FeedbackUiEvent.ReauthenticationRequired -> onReauthenticationRequired()
            }
        }
    }

    FeedbackScreen(
        uiState = uiState,
        onContentChange = viewModel::updateContent,
        onBackClick = onBack,
        onSubmitClick = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
fun FeedbackScreen(
    uiState: FeedbackUiState,
    onContentChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(focusManager, keyboardController) {
                detectTapGestures {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
        containerColor = ChalkakTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FeedbackTopBar(
                onBackClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ButtonAreaHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                    .padding(top = ContentTopPadding),
            ) {
                Text(
                    text = "찰캌을 사용하며\n느낀 점을 알려주세요.",
                    style = ChalkakTheme.typography.title2,
                    color = ChalkakTheme.colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.md))

                Text(
                    text = "서비스 이용 중 불편한 점이나 개선되었으면 하는 점을\n자유롭게 남겨주세요.",
                    style = ChalkakTheme.typography.subheadline,
                    color = ChalkakTheme.colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(InputTopPadding))

                Text(
                    text = "피드백 내용",
                    style = ChalkakTheme.typography.subheadline,
                    color = ChalkakTheme.colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))

                ChalkakTextField(
                    value = uiState.content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = InputMinHeight)
                        .testTag(FEEDBACK_CONTENT_TAG),
                    placeholder = "내용을 입력해 주세요.",
                    enabled = !uiState.isSubmitting,
                    textStyle = ChalkakTheme.typography.subheadline,
                    minLines = 8,
                    maxLength = FEEDBACK_MAX_CONTENT_LENGTH,
                )

                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))

                Text(
                    text = "보내주신 의견은 더 나은 서비스를 만드는 데 활용돼요.",
                    style = ChalkakTheme.typography.footnote,
                    color = ChalkakTheme.colors.textMuted,
                )
            }

            ChalkakButton(
                text = if (uiState.isSubmitting) "보내는 중..." else "보내기",
                onClick = onSubmitClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = ChalkakTheme.spacing.xl)
                    .testTag(FEEDBACK_SUBMIT_BUTTON_TAG),
                enabled = uiState.canSubmit,
            )
        }
    }
}

const val FEEDBACK_SUBMIT_BUTTON_TAG = "feedbackSubmitButton"
const val FEEDBACK_CONTENT_TAG = "feedbackContent"

private val ContentTopPadding = 36.dp
private val InputTopPadding = 28.dp
private val InputMinHeight = 240.dp
private val ButtonAreaHeight = 104.dp

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedbackScreenEmptyPreview() {
    ChalkakTheme {
        FeedbackScreen(
            uiState = FeedbackUiState(),
            onContentChange = {},
            onBackClick = {},
            onSubmitClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedbackScreenFilledPreview() {
    ChalkakTheme {
        FeedbackScreen(
            uiState = FeedbackUiState(content = "사진 업로드 후 화면이 멈춰요."),
            onContentChange = {},
            onBackClick = {},
            onSubmitClick = {},
        )
    }
}
