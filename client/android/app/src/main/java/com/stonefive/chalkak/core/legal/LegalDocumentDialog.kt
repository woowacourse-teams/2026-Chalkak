package com.stonefive.chalkak.core.legal

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun LegalDocumentDialog(
    document: LegalDocument,
    closeContentDescription: String,
    loadFailedText: String,
    retryText: String,
    onDismiss: () -> Unit,
) {
    var loadState by remember(document) { mutableStateOf(LegalDocumentLoadState.LOADING) }
    var webView by remember(document) { mutableStateOf<WebView?>(null) }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
        }

        LegalDocumentDialogContent(
            loadState = loadState,
            closeContentDescription = closeContentDescription,
            loadFailedText = loadFailedText,
            retryText = retryText,
            onDismiss = onDismiss,
            onRetry = {
                loadState = LegalDocumentLoadState.LOADING
                webView?.reload()
            },
            modifier = Modifier,
        ) {
            LegalDocumentWebView(
                modifier = Modifier.fillMaxSize(),
                document = document,
                onWebViewChanged = { webView = it },
                onPageStarted = {
                    loadState = LegalDocumentLoadState.LOADING
                },
                onPageFinished = {
                    if (loadState != LegalDocumentLoadState.ERROR) {
                        loadState = LegalDocumentLoadState.LOADED
                    }
                },
                onMainFrameError = {
                    loadState = LegalDocumentLoadState.ERROR
                },
            )
        }
    }
}

@Preview(
    name = "법률 문서 로딩 완료",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=432dp,height=840dp",
)
@Composable
private fun LegalDocumentDialogPreview() {
    LegalDocumentPreview(loadState = LegalDocumentLoadState.LOADED)
}

@Preview(
    name = "법률 문서 로딩 실패",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=432dp,height=840dp",
)
@Composable
private fun LegalDocumentLoadErrorPreview() {
    LegalDocumentPreview(loadState = LegalDocumentLoadState.ERROR)
}

@Composable
private fun LegalDocumentPreview(loadState: LegalDocumentLoadState) {
    ChalkakTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChalkakTheme.colors.background),
        ) {
            LegalDocumentPreviewBackground()
            LegalDocumentDialogContent(
                loadState = loadState,
                closeContentDescription = "닫기",
                loadFailedText = "문서를 불러오지 못했어요",
                retryText = "다시 시도",
                onDismiss = {},
                onRetry = {},
            ) {
                LegalDocumentPreviewBody()
            }
        }
    }
}

@Composable
private fun LegalDocumentPreviewBackground() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "찰칵에\n오신 것을 환영합니다.",
            style = ChalkakTheme.typography.display,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = ChalkakTheme.shapes.button,
            color = ChalkakTheme.colors.actionSecondary
                .copy(alpha = 0.18f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "다음",
                    style = ChalkakTheme.typography.title3,
                    color = ChalkakTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentPreviewBody() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 28.dp),
    ) {
        Text(
            text = "찰칵 - 이용약관",
            style = ChalkakTheme.typography.display,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "시행일: 2026년 08월 24일",
            style = ChalkakTheme.typography.title2,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "제1조(목적)",
            style = ChalkakTheme.typography.title2,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "이 약관은 회사가 제공하는 사진 기반 소셜 서비스 찰칵 및 이에 부수하는 서비스의 이용과 관련하여 회사와 이용자 사이의 권리, 의무 및 책임사항을 정하는 것을 목적으로 합니다.",
            style = ChalkakTheme.typography.body,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "제2조(용어의 정의)",
            style = ChalkakTheme.typography.title2,
            color = ChalkakTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "회원이란 이 약관에 동의하고 서비스를 이용하는 사람을 말합니다. 비회원이란 계정을 생성하거나 로그인하지 않고 공개 피드를 열람하는 사람을 말합니다.",
            style = ChalkakTheme.typography.body,
            color = ChalkakTheme.colors.textPrimary,
        )
    }
}
