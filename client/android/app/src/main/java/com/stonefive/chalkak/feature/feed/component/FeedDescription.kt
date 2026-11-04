package com.stonefive.chalkak.feature.feed.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakPhotoActionText
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FeedDescription(
    description: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = description,
        modifier = modifier,
        color = ChalkakPhotoActionText,
        style = ChalkakTheme.typography.subheadline,
    )
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedDescriptionPreview() {
    ChalkakTheme {
        FeedDescription(description = "안녕하세요 감사합니다.")
    }
}
