package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        fallback = fallback,
        alignment = alignment,
        contentScale = contentScale,
    )
}

@Preview(showBackground = true)
@Composable
private fun ChalkakImagePreview() {
    ChalkakTheme {
        ChalkakImage(
            model = R.drawable.preview_photo,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
    }
}
