package com.stonefive.chalkak.core.designsystem.component.empty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

const val EMPTY_PHOTO_TITLE = "아직 올라온 사진이 없어요"
const val EMPTY_PHOTO_DESCRIPTION = "첫 번째 사진을 올려보세요"

@Composable
fun ChalkakEmptyPostContent(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    title: String = EMPTY_PHOTO_TITLE,
    description: String? = EMPTY_PHOTO_DESCRIPTION,
) {
    val contentModifier = testTag?.let(modifier::testTag) ?: modifier
    Column(
        modifier = contentModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
            Text(
                text = description,
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.subheadline,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun ChalkakEmptyPostContentPreview() {
    ChalkakTheme {
        ChalkakEmptyPostContent(modifier = Modifier.fillMaxSize())
    }
}
