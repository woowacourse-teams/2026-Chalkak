package com.stonefive.chalkak.feature.upload.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun PhotoUploadSuccessCard(
    imageModel: Any?,
    contentDescription: String?,
    dateLabel: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ChalkakTheme.shapes.small,
        color = ChalkakTheme.colors.surfaceElevated,
        shadowElevation = 12.dp,
    ) {
        Column {
            ChalkakImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = ChalkakTheme.spacing.lg,
                        top = ChalkakTheme.spacing.lg,
                        end = ChalkakTheme.spacing.lg,
                    ),
                contentScale = ContentScale.FillWidth,
            )

            Column(
                modifier = Modifier.padding(
                    start = ChalkakTheme.spacing.lg,
                    top = 27.dp,
                    end = ChalkakTheme.spacing.lg,
                    bottom = 36.dp,
                ),
            ) {
                Text(
                    text = dateLabel,
                    style = ChalkakTheme.typography.caption,
                    color = ChalkakTheme.colors.textMuted,
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = ChalkakTheme.spacing.sm),
                    style = ChalkakTheme.typography.photoCardTitle,
                    color = ChalkakTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun PhotoUploadSuccessCardPreview() {
    ChalkakTheme {
        PhotoUploadSuccessCard(
            imageModel = R.drawable.preview_photo,
            contentDescription = "바다 위 다리",
            dateLabel = "2025. 07. 18",
            title = "다리 - 한낮의 다리",
            modifier = Modifier.padding(ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
