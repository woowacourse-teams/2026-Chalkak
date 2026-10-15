package com.stonefive.chalkak.core.web

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SinglePageWebView(
    url: String,
    modifier: Modifier = Modifier,
) {
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
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = request.isForMainFrame
                    }
                    loadUrl(url)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
        )
    }
}
