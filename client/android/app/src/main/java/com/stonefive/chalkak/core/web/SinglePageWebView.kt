package com.stonefive.chalkak.core.web

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SinglePageWebView(
    url: String,
    onExternalLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnExternalLinkClick by rememberUpdatedState(onExternalLinkClick)
    key(url) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = when (
                            issueLinkNavigation(
                                currentUrl = view.url,
                                targetUrl = request.url.toString(),
                                isMainFrame = request.isForMainFrame,
                                hasGesture = request.hasGesture(),
                            )
                        ) {
                            IssueLinkNavigation.Allow -> false

                            IssueLinkNavigation.Block -> true

                            IssueLinkNavigation.OpenExternally -> {
                                currentOnExternalLinkClick(request.url.toString())
                                true
                            }
                        }
                    }
                    webChromeClient = IssueLinkWindowClient { currentOnExternalLinkClick(it) }
                    loadUrl(url)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                (webView.webChromeClient as? IssueLinkWindowClient)?.close()
                webView.destroy()
            },
        )
    }
}

private class IssueLinkWindowClient(private val onExternalLinkClick: (String) -> Unit) : WebChromeClient() {
    private val pendingWindows = mutableSetOf<WebView>()

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (!isUserGesture) return false
        // A temporary WebView receives target="_blank" without replacing the issue page.
        val opener = view
        val popup = WebView(view.context)
        pendingWindows += popup
        popup.settings.allowFileAccess = false
        popup.settings.allowContentAccess = false
        popup.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return true
                if (pendingWindows.remove(popup)) {
                    if (
                        issueLinkNavigation(null, request.url.toString(), true, true) ==
                        IssueLinkNavigation.OpenExternally
                    ) {
                        onExternalLinkClick(request.url.toString())
                    }
                    opener.post { popup.destroy() }
                }
                return true
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        opener.postDelayed({
            if (pendingWindows.remove(popup)) popup.destroy()
        }, POPUP_NAVIGATION_TIMEOUT_MS)
        return true
    }

    fun close() {
        pendingWindows.forEach { it.destroy() }
        pendingWindows.clear()
    }
}

private const val POPUP_NAVIGATION_TIMEOUT_MS = 10_000L
