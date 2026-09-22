package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FeedTitleEditDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier,
            shape = ChalkakTheme.shapes.large,
            color = ChalkakTheme.colors.surfaceElevated,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 24.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "게시물 제목 수정",
                    style = ChalkakTheme.typography.title3,
                    color = ChalkakTheme.colors.textPrimary,
                )
                ChalkakTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag(FEED_TITLE_INPUT_TAG),
                    placeholder = "제목을 입력해 주세요.",
                    enabled = !isSubmitting,
                    textStyle = ChalkakTheme.typography.subheadline,
                    singleLine = true,
                    maxLength = POST_TITLE_MAX_LENGTH,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DialogButton(
                        text = "취소",
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        containerColor = ChalkakTheme.colors.actionSecondary,
                        contentColor = ChalkakTheme.colors.onActionSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    DialogButton(
                        text = if (isSubmitting) "저장 중..." else "저장",
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        containerColor = ChalkakTheme.colors.actionPrimary,
                        contentColor = ChalkakTheme.colors.onActionPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(FEED_TITLE_CONFIRM_TAG),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ChalkakTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = text,
            style = ChalkakTheme.typography.callout,
        )
    }
}

const val FEED_TITLE_INPUT_TAG = "feed_title_input"
const val FEED_TITLE_CONFIRM_TAG = "feed_title_confirm"

private const val POST_TITLE_MAX_LENGTH = 10
