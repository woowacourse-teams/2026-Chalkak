package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakSignedImage(
    imageModel: Any?,
    signatureModel: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    signatureModifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier,
        propagateMinConstraints = true,
    ) {
        ChalkakImage(
            model = imageModel,
            contentDescription = contentDescription,
            contentScale = contentScale,
        )

        if (signatureModel != null) {
            ChalkakImage(
                model = signatureModel,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .then(signatureModifier),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakSignedImagePreview() {
    ChalkakTheme {
        ChalkakSignedImage(
            imageModel = R.drawable.preview_photo,
            signatureModel = R.drawable.preview_signature,
            contentDescription = null,
            modifier = Modifier.size(
                width = 270.dp,
                height = 360.dp,
            ),
            signatureModifier = Modifier.size(
                width = 56.dp,
                height = 42.dp,
            ),
        )
    }
}
