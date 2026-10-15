package com.stonefive.chalkak.feature.web

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.web.SinglePageWebView

@Composable
fun WebPageRoute(
    title: String,
    url: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    WebPageScreen(
        title = title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SinglePageWebView(url = url, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun WebPageScreen(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT)) {
            Box(
                modifier = Modifier
                    .width(BACK_ACTION_WIDTH)
                    .fillMaxHeight()
                    .semantics { contentDescription = "뒤로 가기" }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onBackClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = ChalkakTheme.colors.iconPrimary,
                )
            }
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun WebPageScreenPreview() {
    ChalkakTheme {
        WebPageScreen(
            title = "ISSUE",
            onBackClick = {},
            content = {},
        )
    }
}

private val TOP_BAR_HEIGHT = 72.dp
private val BACK_ACTION_WIDTH = 56.dp
