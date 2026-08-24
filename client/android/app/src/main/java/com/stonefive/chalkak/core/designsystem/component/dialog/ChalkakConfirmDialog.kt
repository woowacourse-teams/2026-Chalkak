package com.stonefive.chalkak.core.designsystem.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

enum class ChalkakConfirmDialogStyle {
    PRIMARY,
    DESTRUCTIVE,
}

@Composable
fun ChalkakConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: ChalkakConfirmDialogStyle = ChalkakConfirmDialogStyle.PRIMARY,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier,
            shape = ChalkakTheme.shapes.large,
            color = ChalkakTheme.colors.surfaceElevated,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = DialogHorizontalPadding,
                    top = DialogTopPadding,
                    end = DialogHorizontalPadding,
                    bottom = DialogBottomPadding,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = ChalkakTheme.typography.title3,
                    color = ChalkakTheme.colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(TitleMessageSpacing))

                Text(
                    text = message,
                    style = ChalkakTheme.typography.callout,
                    color = ChalkakTheme.colors.textNeutral,
                )

                Spacer(modifier = Modifier.height(MessageButtonSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
                ) {
                    DialogActionButton(
                        text = "취소",
                        onClick = onDismiss,
                        containerColor = ChalkakTheme.colors.actionSecondary,
                        contentColor = ChalkakTheme.colors.onActionSecondary,
                        modifier = Modifier.weight(1f),
                    )

                    DialogActionButton(
                        text = confirmText,
                        onClick = onConfirm,
                        containerColor = when (confirmStyle) {
                            ChalkakConfirmDialogStyle.PRIMARY ->
                                ChalkakTheme.colors.actionPrimary

                            ChalkakConfirmDialogStyle.DESTRUCTIVE ->
                                ChalkakTheme.colors.destructive
                        },
                        contentColor = ChalkakTheme.colors.onActionPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CONFIRM_BUTTON_TEST_TAG),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ChalkakTheme.shapes.button,
        contentPadding = PaddingValues(
            vertical = ButtonVerticalPadding,
        ),
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

@Preview(showBackground = true, widthDp = 390, heightDp = 300)
@Composable
private fun LogoutDialogPreview() {
    ChalkakTheme {
        ChalkakConfirmDialog(
            title = "로그아웃",
            message = "정말 로그아웃 하시겠습니까?",
            confirmText = "로그아웃",
            onConfirm = {},
            onDismiss = {},
            modifier = Modifier.width(PreviewDialogWidth),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 300)
@Composable
private fun WithdrawDialogPreview() {
    ChalkakTheme {
        ChalkakConfirmDialog(
            title = "회원탈퇴",
            message = "정말 회원탈퇴 하시겠습니까?",
            confirmText = "회원탈퇴",
            onConfirm = {},
            onDismiss = {},
            modifier = Modifier.width(PreviewDialogWidth),
            confirmStyle = ChalkakConfirmDialogStyle.DESTRUCTIVE,
        )
    }
}

private val PreviewDialogWidth = 317.dp
private val DialogHorizontalPadding = 40.dp
private val DialogTopPadding = 24.dp
private val DialogBottomPadding = 26.dp
private val TitleMessageSpacing = 8.dp
private val MessageButtonSpacing = 23.dp
private val ButtonSpacing = 10.dp
private val ButtonVerticalPadding = 9.5.dp

const val CONFIRM_BUTTON_TEST_TAG = "chalkak_confirm_dialog_confirm_button"
