package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SelectedPhotoDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun RecordSelectedPhoto(
    post: PostCalendarItem?,
    modifier: Modifier = Modifier,
) {
    if (post == null) return

    Box(modifier = modifier) {
        ChalkakImage(
            model = post.thumbnailImageUrl,
            contentDescription = "${post.topicDate.format(SelectedPhotoDateFormatter)} 기록 사진",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = post.topicDate.format(SelectedPhotoDateFormatter),
            color = ChalkakTheme.colors.textOnImage,
            style = ChalkakTheme.typography.body,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 15.dp,
                    top = 15.dp,
                ),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordSelectedPhotoPreview() {
    ChalkakTheme {
        RecordSelectedPhoto(
            post = PostCalendarItem(
                postId = "preview-post",
                topicDate = LocalDate.of(2026, 8, 2),
                thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                status = PostStatus.APPROVED,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
