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
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.RecordPhoto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SelectedPhotoDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun RecordSelectedPhoto(
    photo: RecordPhoto?,
    modifier: Modifier = Modifier,
) {
    if (photo == null) return

    Box(modifier = modifier) {
        ChalkakSignedImage(
            imageModel = photo.imageUrl,
            signatureModel = photo.signatureUrl,
            contentDescription = photo.contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = buildString {
                append(photo.date.format(SelectedPhotoDateFormatter))
                photo.title?.takeIf(String::isNotBlank)?.let {
                    append(" · ")
                    append(it)
                }
            },
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
            photo = RecordPhoto(
                date = LocalDate.of(2026, 8, 2),
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "노을과 전신주",
                title = "물결",
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
