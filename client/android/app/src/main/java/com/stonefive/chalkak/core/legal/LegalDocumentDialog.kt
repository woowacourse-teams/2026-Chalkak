package com.stonefive.chalkak.core.legal

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun LegalDocumentDialog(
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
