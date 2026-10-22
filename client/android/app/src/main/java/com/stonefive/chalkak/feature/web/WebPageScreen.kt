package com.stonefive.chalkak.feature.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    BackHandler(onBack = onBackClick)
    WebPageScreen(
        title = title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SinglePageWebView(
            url = url,
            modifier = Modifier.fillMaxSize(),
            onExternalLinkClick = { context.openIssueLinkInBrowser(it) },
        )
    }
}

private fun Context.openIssueLinkInBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        // Resolve a browser, rather than an app associated with this particular URL.
        selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "링크를 열 수 있는 브라우저가 없어요", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "브라우저에서 링크를 열지 못했어요", Toast.LENGTH_SHORT).show()
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
