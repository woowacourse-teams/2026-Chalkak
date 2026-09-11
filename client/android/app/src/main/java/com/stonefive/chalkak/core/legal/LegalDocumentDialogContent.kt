package com.stonefive.chalkak.core.legal

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

enum class LegalDocumentLoadState {
    LOADING,
    LOADED,
    ERROR,
}

@Composable
fun LegalDocumentDialogContent(
    loadState: LegalDocumentLoadState,
    closeContentDescription: String,
    loadFailedText: String,
    retryText: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LegalDocumentCard(
            loadState = loadState,
            closeContentDescription = closeContentDescription,
            loadFailedText = loadFailedText,
            retryText = retryText,
            onDismiss = onDismiss,
            onRetry = onRetry,
            modifier = Modifier
                .padding(top = maxHeight * CARD_TOP_MARGIN_RATIO)
                .fillMaxWidth(CARD_WIDTH_RATIO)
                .fillMaxHeight(),
            content = content,
        )
    }
}

private const val CARD_TOP_MARGIN_RATIO = 0.05f
private const val CARD_WIDTH_RATIO = 0.94f
