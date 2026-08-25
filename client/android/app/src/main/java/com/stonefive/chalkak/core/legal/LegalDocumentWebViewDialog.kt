package com.stonefive.chalkak.core.legal

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
internal fun LegalDocumentWebViewDialog(
    document: LegalDocument,
    closeContentDescription: String,
    loadFailedText: String,
    retryText: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember(document) { mutableStateOf(true) }
    var hasLoadError by remember(document) { mutableStateOf(false) }
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

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val availableHeight = maxHeight

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = availableHeight * 0.05f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(availableHeight * 0.95f),
                    shape = ChalkakTheme.shapes.sheet,
                    color = ChalkakTheme.colors.surfaceElevated,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = closeContentDescription,
                                    tint = ChalkakTheme.colors.textSecondary,
                                )
                            }
                        }

                        HorizontalDivider(color = ChalkakTheme.colors.border)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = {
                                    WebView(context).apply {
                                        webView = this
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                        settings.userAgentString = settings.userAgentString
                                            .replace("; wv", "")
                                            .replace(" Version/4.0", "")
                                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        isVerticalScrollBarEnabled = false
                                        setBackgroundColor(AndroidColor.TRANSPARENT)
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(
                                                view: WebView,
                                                url: String?,
                                                favicon: Bitmap?,
                                            ) {
                                                isLoading = true
                                                hasLoadError = false
                                            }

                                            override fun onPageFinished(
                                                view: WebView,
                                                url: String?,
                                            ) {
                                                isLoading = false
                                                view.post {
                                                    view.evaluateJavascript(
                                                        NOTION_WEB_VIEW_LAYOUT_FIX_SCRIPT,
                                                        null,
                                                    )
                                                }
                                            }

                                            override fun onReceivedError(
                                                view: WebView,
                                                request: WebResourceRequest,
                                                error: WebResourceError,
                                            ) {
                                                if (request.isForMainFrame) {
                                                    isLoading = false
                                                    hasLoadError = true
                                                }
                                            }
                                        }
                                        loadUrl(document.url)
                                    }
                                },
                                update = { view ->
                                    if (view.url != document.url) {
                                        view.loadUrl(document.url)
                                    }
                                },
                                onRelease = { view ->
                                    view.stopLoading()
                                    view.destroy()
                                },
                            )

                            if (hasLoadError) {
                                LegalDocumentLoadError(
                                    message = loadFailedText,
                                    retryText = retryText,
                                    onRetry = {
                                        hasLoadError = false
                                        isLoading = true
                                        webView?.reload()
                                    },
                                )
                            } else if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ChalkakTheme.colors.surfaceElevated),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Notion's flex layout can have a zero-height scroll container in Android WebView. */
private const val NOTION_WEB_VIEW_LAYOUT_FIX_SCRIPT = """
    (() => {
        let attempts = 0;

        const fixLayout = () => {
            attempts += 1;

            const main = document.getElementById('main');
            const scroller = main?.querySelector('.notion-scroller');
            if (!main || !scroller) {
                if (attempts < 50) setTimeout(fixLayout, 100);
                return;
            }

            const viewportHeight = window.innerHeight;
            main.style.height = viewportHeight + 'px';
            main.style.minHeight = viewportHeight + 'px';
            main.style.maxHeight = 'none';
            main.style.flexShrink = '0';

            const mainRect = main.getBoundingClientRect();
            const scrollerRect = scroller.getBoundingClientRect();
            const topInset = Math.max(scrollerRect.top - mainRect.top, 0);
            scroller.style.height = Math.max(viewportHeight - topInset, 0) + 'px';
            scroller.style.minHeight = '0px';
            scroller.style.maxHeight = 'none';
            scroller.style.flex = '1 1 auto';

            if (attempts < 50) setTimeout(fixLayout, 100);
        };

        fixLayout();
    })();
"""

@Composable
private fun LegalDocumentLoadError(
    message: String,
    retryText: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.surfaceElevated)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = ChalkakTheme.typography.callout,
                color = ChalkakTheme.colors.textNeutral,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(
                    text = retryText,
                    style = ChalkakTheme.typography.callout,
                    color = ChalkakTheme.colors.textPrimary,
                )
            }
        }
    }
}

/** Design-time shell preview; the real WebView is intentionally replaced with fake content. */
@Preview(
    name = "법률 문서 로딩 완료",
    showBackground = true,
    showSystemUi = true,
    widthDp = 432,
    heightDp = 840,
)
@Composable
private fun LegalDocumentWebViewDialogPreview() {
    ChalkakTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChalkakTheme.colors.background),
        ) {
            LegalDocumentPreviewBackground()
            LegalDocumentPreviewFrame {
                LegalDocumentPreviewBody()
            }
        }
    }
}

@Preview(
    name = "법률 문서 로딩 실패",
    showBackground = true,
    showSystemUi = true,
    widthDp = 432,
    heightDp = 840,
)
@Composable
private fun LegalDocumentLoadErrorPreview() {
    ChalkakTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChalkakTheme.colors.background),
        ) {
            LegalDocumentPreviewBackground()
            LegalDocumentPreviewFrame {
                LegalDocumentLoadError(
                    message = "문서를 불러오지 못했어요",
                    retryText = "다시 시도",
                    onRetry = {},
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentPreviewFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val availableHeight = maxHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = availableHeight * 0.05f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(availableHeight * 0.95f),
                shape = ChalkakTheme.shapes.sheet,
                color = ChalkakTheme.colors.surfaceElevated,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "닫기",
                                tint = ChalkakTheme.colors.textSecondary,
                            )
                        }
                    }

                    HorizontalDivider(color = ChalkakTheme.colors.border)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        content()
                    }
                }
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
