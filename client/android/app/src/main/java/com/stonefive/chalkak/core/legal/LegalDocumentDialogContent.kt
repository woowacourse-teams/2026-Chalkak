package com.stonefive.chalkak.core.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

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
                        content()

                        when (loadState) {
                            LegalDocumentLoadState.LOADING -> LegalDocumentLoading()

                            LegalDocumentLoadState.LOADED -> Unit

                            LegalDocumentLoadState.ERROR -> LegalDocumentLoadError(
                                message = loadFailedText,
                                retryText = retryText,
                                onRetry = onRetry,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LegalDocumentLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
    }
}

@Composable
private fun BoxScope.LegalDocumentLoadError(
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
