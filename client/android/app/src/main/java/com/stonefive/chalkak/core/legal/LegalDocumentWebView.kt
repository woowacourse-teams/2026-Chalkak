package com.stonefive.chalkak.core.legal

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LegalDocumentWebView(
    document: LegalDocument,
    onWebViewChanged: (WebView?) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onMainFrameError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnWebViewChanged by rememberUpdatedState(onWebViewChanged)
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnMainFrameError by rememberUpdatedState(onMainFrameError)

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                currentOnWebViewChanged(this)
                configureSettings()
                webViewClient = LegalDocumentWebViewClient(
                    context = context,
                    onPageStarted = { currentOnPageStarted() },
                    onPageFinished = { currentOnPageFinished() },
                    onMainFrameError = { currentOnMainFrameError() },
                )
                loadUrl(document.url)
            }
        },
        update = { webView ->
            if (webView.url != document.url) {
                webView.loadUrl(document.url)
            }
        },
        onRelease = { webView ->
            currentOnWebViewChanged(null)
            webView.stopLoading()
            webView.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSettings() {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
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
}

private class LegalDocumentWebViewClient(
    private val context: Context,
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onMainFrameError: () -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (request.url.isAllowedLegalHost()) {
            return false
        }

        context.openExternalLink(request.url)
        return true
    }

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        onPageStarted()
    }

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        onPageFinished()
        view.post {
            view.evaluateJavascript(NOTION_WEB_VIEW_LAYOUT_FIX_SCRIPT, null)
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onMainFrameError()
        }
    }
}

private fun Uri.isAllowedLegalHost(): Boolean {
    if (!scheme.equals("https", ignoreCase = true)) return false

    val host = host ?: return false
    return NOTION_ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
}

private fun Context.openExternalLink(uri: Uri) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (exception: ActivityNotFoundException) {
        Log.w(LOG_TAG, "외부 링크를 처리할 앱을 찾지 못했습니다.", exception)
    } catch (exception: SecurityException) {
        Log.w(LOG_TAG, "외부 링크를 열 권한이 없습니다.", exception)
    }
}

private val NOTION_ALLOWED_HOSTS = listOf("notion.com", "notion.so", "notion.site")

private const val LOG_TAG = "LegalDocumentWebView"

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
