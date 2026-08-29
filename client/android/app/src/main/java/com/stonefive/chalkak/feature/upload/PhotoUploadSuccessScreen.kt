package com.stonefive.chalkak.feature.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.upload.component.PhotoUploadSuccessCard

@Composable
fun PhotoUploadSuccessScreen(
    imageModel: Any?,
    caption: String,
    content: PhotoUploadSuccessContent,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakButton(
                text = "확인했어요.",
                onClick = onConfirmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChalkakTheme.spacing.xl)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChalkakBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            PhotoUploadSuccessCard(
                imageModel = imageModel,
                contentDescription = "전시한 사진",
                dateLabel = content.dateLabel,
                title = caption.takeIf(String::isNotBlank) ?: content.topic,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
            )

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "‘${content.topic}’를 기록했어요.",
                modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                style = ChalkakTheme.typography.title1,
                color = ChalkakTheme.colors.textPrimary,
            )
            Text(
                text = content.moderationStatus.toSuccessMessage(),
                modifier = Modifier.padding(
                    start = ChalkakTheme.spacing.screenHorizontal,
                    top = 10.dp,
                    end = ChalkakTheme.spacing.screenHorizontal,
                    bottom = 40.dp,
                ),
                style = ChalkakTheme.typography.footnote,
                color = ChalkakTheme.colors.textSecondary,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun PhotoUploadSuccessScreenPreview() {
    ChalkakTheme {
        PhotoUploadSuccessScreen(
            modifier = Modifier,
            imageModel = R.drawable.preview_photo,
            caption = "한낮의 다리",
            content = PhotoUploadSuccessContent(
                dateLabel = "2026. 08. 29",
                topic = "다리",
                moderationStatus = "VALIDATING",
            ),
            onConfirmClick = {},
        )
    }
}

private fun String.toSuccessMessage(): String = when (this) {
    "PENDING" -> "검수를 기다리고 있어요. 피드 표시까지 시간이 조금 걸릴 수도 있어요!"
    else -> "사진을 확인하고 있어요. 피드 표시까지 시간이 조금 걸릴 수도 있어요!"
}
